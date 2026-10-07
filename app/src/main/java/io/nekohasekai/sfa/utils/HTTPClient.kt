package io.nekohasekai.sfa.utils

import android.content.Context
import android.os.Build
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.ktx.unwrap
import java.io.Closeable
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONArray
import org.json.JSONObject

private val hwidMemoryCache = ConcurrentHashMap<String, String>()

class HTTPClient : Closeable {

    private val clientDelegate = lazy { Libbox.newHTTPClient().apply { modernTLS() } }
    private val client by clientDelegate

    fun getString(url: String): String {
        val result = getSubscription(url)
        require(result.report.issues.isEmpty()) { result.report.displayText() }
        return result.config
    }

    internal fun getSubscription(url: String): SubscriptionImportResult {
        val request = client.newRequest()
        request.setURL(url)

        val hwid = getOrCreateHwid(url)

        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val rawModel = Build.MODEL
        val model =
            if (rawModel.startsWith(manufacturer, ignoreCase = true)) rawModel
            else "$manufacturer $rawModel"
        val androidVer = Build.VERSION.RELEASE
        val buildId = Build.ID.ifEmpty { "UKQ1.231003.002" }

        val userAgentStr =
            "sing-box/1.14.2 SFAxtnd/${io.nekohasekai.sfa.BuildConfig.VERSION_NAME} (Linux; Android $androidVer; $model Build/$buildId) HWID/$hwid"

        request.setUserAgent(userAgentStr)
        request.setHeader("HWID", hwid)
        request.setHeader("hwid", hwid)
        request.setHeader("X-HWID", hwid)
        request.setHeader("Device-ID", hwid)
        request.setHeader("Happ-HWID", hwid)

        val fullDeviceTitle = "$model (Android $androidVer)"
        request.setHeader("Device-Name", fullDeviceTitle)
        request.setHeader("X-Device-Name", fullDeviceTitle)
        request.setHeader("Happ-Device-Name", fullDeviceTitle)
        request.setHeader("Device-Model", model)
        request.setHeader("X-Device-Model", model)
        request.setHeader("Device-OS", "Android $androidVer")
        request.setHeader("X-Device-OS", "Android $androidVer")

        request.setHeader("App-Name", "SFAxtnd")
        request.setHeader("Platform", "Android")
        request.setHeader("Accept", "*/*")

        val response = request.execute()
        val rawContent = response.content.unwrap

        return parseSubscription(rawContent).copy(
            profileName = SubscriptionMetadata.title(response.getHeader("profile-title"), rawContent),
            updateIntervalMinutes = SubscriptionMetadata.intervalMinutes(response.getHeader("profile-update-interval")),
        )
    }

    private fun getOrCreateHwid(url: String): String {
        val normalizedUrl = url.trim()
        val urlKey =
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                val hash = digest.digest(normalizedUrl.toByteArray(StandardCharsets.UTF_8))
                hash.joinToString("") { "%02x".format(it) }
            } catch (e: Exception) {
                normalizedUrl.hashCode().toString()
            }

        val memoryHwid = hwidMemoryCache[urlKey]
        if (!memoryHwid.isNullOrBlank()) {
            return memoryHwid
        }

        val context = getApplicationContext()
        if (context != null) {
            val prefs =
                context.getSharedPreferences("subscription_hwid_store", Context.MODE_PRIVATE)
            val savedHwid = prefs.getString(urlKey, null)
            if (!savedHwid.isNullOrBlank()) {
                hwidMemoryCache[urlKey] = savedHwid
                return savedHwid
            }

            val newHwid = UUID.randomUUID().toString().replace("-", "").take(16)
            prefs.edit().putString(urlKey, newHwid).apply()
            hwidMemoryCache[urlKey] = newHwid
            return newHwid
        }

        val fallbackHwid = urlKey.take(16)
        hwidMemoryCache[urlKey] = fallbackHwid
        return fallbackHwid
    }

    private fun getApplicationContext(): Context? =
        try {
            val appClass = Class.forName("io.nekohasekai.sfa.Application")
            val field = appClass.getDeclaredField("application")
            field.isAccessible = true
            field.get(null) as? Context
        } catch (e: Exception) {
            try {
                val activityThreadClass = Class.forName("android.app.ActivityThread")
                val currentAppMethod = activityThreadClass.getMethod("currentApplication")
                currentAppMethod.invoke(null) as? Context
            } catch (e2: Exception) {
                null
            }
        }

    internal fun processSubscriptionContent(raw: String): String {
        val result = parseSubscription(raw)
        require(result.report.issues.isEmpty()) { result.report.displayText() }
        return result.config
    }

    internal fun parseSubscription(raw: String): SubscriptionImportResult =
        SubscriptionContentParser(
                io.nekohasekai.sfa.database.Settings.tunStack,
                io.nekohasekai.sfa.database.Settings.routingBlockIpv6,
            ) { node ->
                val minimal = JSONObject().put("outbounds", JSONArray().put(node)).put("dns", JSONObject().put("servers", JSONArray().put(JSONObject().put("type", "local").put("tag", "dns-direct"))))
                Libbox.checkConfig(minimal.toString())
            }
            .parse(raw)

    override fun close() {
        if (clientDelegate.isInitialized()) client.close()
    }

    companion object {
        const val userAgent = "SFAxtnd"
    }
}
