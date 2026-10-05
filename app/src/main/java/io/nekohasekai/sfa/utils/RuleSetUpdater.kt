package io.nekohasekai.sfa.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object RuleSetUpdater {
    private const val TAG = "RuleSetUpdater"
    private const val RULE_SET_DIR = "rule_sets"

    suspend fun ensureRuleSets(context: Context, configJson: String, updateIntervalMs: Long): String {
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
                            val fileName = "$tag.srs"
                            val file = File(dir, fileName)

                            var shouldDownload = true
                            if (file.exists()) {
                                if (updateIntervalMs < 0) { // -1 means never update if exists
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
                                downloadFile(urlString, file)
                            }

                            if (file.exists()) {
                                ruleSet.put("type", "local")
                                ruleSet.remove("url")
                                ruleSet.remove("download_detour")
                                ruleSet.put("path", file.absolutePath)
                            } else {
                                Log.e(TAG, "Failed to ensure rule-set file for tag: $tag")
                            }
                        }
                    }
                }
                root.toString()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process rule sets", e)
                configJson
            }
        }
    }

    private fun downloadFile(urlString: String, destFile: File) {
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
                        input.copyTo(output)
                    }
                }
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
