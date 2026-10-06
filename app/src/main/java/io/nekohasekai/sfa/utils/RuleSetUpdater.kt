package io.nekohasekai.sfa.utils

import android.content.Context
import android.util.Log
import io.nekohasekai.libbox.Libbox
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object RuleSetUpdater {
    private const val TAG = "RuleSetUpdater"
    private const val RULE_SET_DIR = "rule_sets"

    suspend fun ensureRuleSets(
        context: Context,
        configJson: String,
        updateIntervalMs: Long,
    ): String {
        return withContext(Dispatchers.IO) {
            try {
                val root = JSONObject(configJson)
                val route = root.optJSONObject("route") ?: return@withContext configJson
                val ruleSets = route.optJSONArray("rule_set") ?: return@withContext configJson

                val dir = File(context.filesDir, RULE_SET_DIR)
                if (!dir.exists()) {
                    dir.mkdirs()
                }

                for (i in 0 until ruleSets.length()) {
                    val ruleSet = ruleSets.optJSONObject(i) ?: continue
                    if (ruleSet.optString("type") == "remote") {
                        val urlString = ruleSet.optString("url")
                        if (urlString.isNotEmpty()) {
                            val tag = ruleSet.optString("tag")
                            val format = ruleSet.optString("format", "binary")
                            val safeTag = tag.replace(Regex("[^A-Za-z0-9._-]"), "_")
                            val hash =
                                java.security.MessageDigest.getInstance("SHA-256")
                                    .digest(urlString.toByteArray())
                                    .take(8)
                                    .joinToString("") { "%02x".format(it) }
                            val fileName =
                                "$safeTag-$hash.${if (format == "source") "json" else "srs"}"
                            val file = File(dir, fileName)
                            // Whitelist must be usable on a fresh install even when GitHub
                            // is unreachable on the current restricted mobile network.
                            if (!file.exists() && tag in RoutingPresets.remoteRuleSets) {
                                context.assets.open("rule_sets/$tag.srs").use { input ->
                                    file.outputStream().use { input.copyTo(it) }
                                }
                            }

                            var shouldDownload = true
                            if (file.exists()) {
                                if (updateIntervalMs <= 0) { // -1 means never update if exists
                                    shouldDownload = false
                                } else if (updateIntervalMs > 0) {
                                    val lastModified = file.lastModified()
                                    val now = System.currentTimeMillis()
                                    if (now - lastModified < updateIntervalMs) {
                                        shouldDownload = false
                                    }
                                }
                            }

                            if (shouldDownload) {
                                downloadFile(urlString, file, format)
                            }

                            RuleSetPolicy.configure(
                                ruleSet,
                                file.takeIf { it.exists() }?.absolutePath,
                                updateIntervalMs,
                            )
                        }
                    }
                }
                root.toString()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process rule sets", e)
                throw IllegalArgumentException(
                    "Не удалось подготовить наборы правил: ${e.message}",
                    e,
                )
            }
        }
    }

    private fun downloadFile(urlString: String, destFile: File, format: String) {
        Log.i(TAG, "Downloading rule-set from $urlString")
        var connection: HttpURLConnection? = null
        try {
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val tempFile = File(destFile.parentFile, destFile.name + ".tmp")
                connection.inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(8192)
                        var total = 0
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= 16 * 1024 * 1024) { "Rule-set exceeds 16 MiB" }
                            output.write(buffer, 0, count)
                        }
                    }
                }
                Libbox.checkConfig(
                    JSONObject()
                        .put(
                            "route",
                            JSONObject()
                                .put(
                                    "rule_set",
                                    JSONArray()
                                        .put(
                                            JSONObject()
                                                .put("type", "local")
                                                .put("tag", "validate-update")
                                                .put("format", format)
                                                .put("path", tempFile.absolutePath)
                                        ),
                                ),
                        )
                        .toString()
                )
                if (tempFile.renameTo(destFile)) {
                    Log.i(TAG, "Successfully downloaded to ${destFile.absolutePath}")
                } else {
                    Log.e(TAG, "Failed to rename temp file for ${destFile.name}")
                }
            } else {
                Log.e(TAG, "HTTP error $responseCode downloading $urlString")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading $urlString", e)
        } finally {
            connection?.disconnect()
        }
    }
}
