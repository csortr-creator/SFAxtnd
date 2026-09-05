package io.nekohasekai.sfa.utils

import android.content.Context
import android.util.Base64
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.ktx.unwrap
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class HTTPClient : Closeable {

    private val client = Libbox.newHTTPClient()

    init {
        client.modernTLS()
    }

    fun getString(url: String): String {
        val request = client.newRequest()
        request.setURL(url)

        val hwid = getOrCreateHwid(url)

        request.setUserAgent("Happ/3.20.4")
        request.setHeader("HWID", hwid)
        request.setHeader("hwid", hwid)
        request.setHeader("X-HWID", hwid)
        request.setHeader("Device-ID", hwid)
        request.setHeader("Happ-HWID", hwid)
        request.setHeader("App-Name", "Happ")
        request.setHeader("App-Version", "3.20.4")
        request.setHeader("Platform", "Android")
        request.setHeader("Accept", "*/*")

        val response = request.execute()
        val rawContent = response.content.unwrap

        return processSubscriptionContent(rawContent)
    }

    private fun getOrCreateHwid(url: String): String {
        val normalizedUrl = url.trim()
        val urlKey = try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(normalizedUrl.toByteArray(StandardCharsets.UTF_8))
            hash.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            normalizedUrl.hashCode().toString()
        }

        hwidMemoryCache[urlKey]?.let { return it }

        val context = getApplicationContext()
        if (context != null) {
            val prefs = context.getSharedPreferences("subscription_hwid_store", Context.MODE_PRIVATE)
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

    private fun getApplicationContext(): Context? {
        return try {
            val appClass = Class.forName("io.nekohasekai.sfa.Application")
            val field = appClass.getDeclaredField("application")
            field.isAccessible = true
            field.get(null) as? Context
        } catch (_: Exception) {
            try {
                val activityThreadClass = Class.forName("android.app.ActivityThread")
                val currentAppMethod = activityThreadClass.getMethod("currentApplication")
                currentAppMethod.invoke(null) as? Context
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun processSubscriptionContent(raw: String): String {
        val trimmed = raw.trim()

        if (trimmed.startsWith("{") && (trimmed.contains("\"outbounds\"") || trimmed.contains("\"route\""))) {
            return trimmed
        }

        val contentToParse = tryDecodeBase64(trimmed)
        val nodes = parseUriLines(contentToParse)
        if (nodes.isEmpty()) {
            return trimmed
        }

        return buildSingBoxConfig(nodes)
    }

    private fun tryDecodeBase64(text: String): String {
        val clean = text.replace("\r", "").replace("\n", "").trim()
        if (clean.startsWith("vless://") || clean.startsWith("vmess://") ||
            clean.startsWith("trojan://") || clean.startsWith("ss://")) {
            return text
        }
        return try {
            val decoded = Base64.decode(clean, Base64.DEFAULT)
            val decodedStr = String(decoded, StandardCharsets.UTF_8).trim()
            if (decodedStr.contains("://")) decodedStr else text
        } catch (_: Exception) {
            try {
                val decoded = Base64.decode(clean, Base64.URL_SAFE)
                val decodedStr = String(decoded, StandardCharsets.UTF_8).trim()
                if (decodedStr.contains("://")) decodedStr else text
            } catch (_: Exception) {
                text
            }
        }
    }

    private fun cleanNodeName(rawTag: String?): String {
        if (rawTag.isNullOrBlank()) return "Node"
        return try {
            URLDecoder.decode(rawTag, StandardCharsets.UTF_8.name()).trim()
        } catch (_: Exception) {
            rawTag.trim()
        }
    }

    private fun parseUriLines(text: String): List<JSONObject> {
        val outbounds = mutableListOf<JSONObject>()
        for (rawLine in text.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) continue

            try {
                when {
                    line.startsWith("vless://") -> parseVless(line)?.let { outbounds.add(it) }
                    line.startsWith("vmess://") -> parseVmess(line)?.let { outbounds.add(it) }
                    line.startsWith("trojan://") -> parseTrojan(line)?.let { outbounds.add(it) }
                    line.startsWith("ss://") -> parseShadowsocks(line)?.let { outbounds.add(it) }
                }
            } catch (_: Exception) {
            }
        }
        return outbounds
    }

    private fun parseVless(uriStr: String): JSONObject? {
        val uri = URI(uriStr)
        val uuid = uri.userInfo ?: return null
        val server = uri.host ?: return null
        val port = if (uri.port > 0) uri.port else 443
        val tag = cleanNodeName(uri.rawFragment ?: uri.fragment)
        val params = parseQueryParams(uri.rawQuery)

        val outbound = JSONObject()
        outbound.put("type", "vless")
        outbound.put("tag", tag)
        outbound.put("server", server)
        outbound.put("server_port", port)
        outbound.put("uuid", uuid)

        params["flow"]?.let { outbound.put("flow", it) }
        outbound.put("packet_encoding", params["packetEncoding"] ?: "xudp")

        val security = params["security"] ?: "none"
        if (security == "tls" || security == "reality") {
            val tlsObj = JSONObject()
            tlsObj.put("enabled", true)
            params["sni"]?.let { tlsObj.put("server_name", it) }

            val utlsObj = JSONObject()
            utlsObj.put("enabled", true)
            utlsObj.put("fingerprint", params["fp"] ?: "chrome")
            tlsObj.put("utls", utlsObj)

            if (security == "reality") {
                val realityObj = JSONObject()
                realityObj.put("enabled", true)
                params["pbk"]?.let { realityObj.put("public_key", it) }
                params["sid"]?.let { realityObj.put("short_id", it) }
                tlsObj.put("reality", realityObj)
            }
            outbound.put("tls", tlsObj)
        }

        val transportType = params["type"] ?: "tcp"
        if (transportType == "ws" || transportType == "grpc" || transportType == "http") {
            val transportObj = JSONObject()
            transportObj.put("type", transportType)
            if (transportType == "ws") {
                params["path"]?.let { transportObj.put("path", URLDecoder.decode(it, "UTF-8")) }
                params["host"]?.let {
                    val headers = JSONObject()
                    headers.put("Host", URLDecoder.decode(it, "UTF-8"))
                    transportObj.put("headers", headers)
                }
            } else if (transportType == "grpc") {
                params["serviceName"]?.let { transportObj.put("service_name", URLDecoder.decode(it, "UTF-8")) }
            }
            outbound.put("transport", transportObj)
        }

        return outbound
    }

    private fun parseVmess(uriStr: String): JSONObject? {
        val b64 = uriStr.removePrefix("vmess://").trim()
        val jsonStr = try {
            String(Base64.decode(b64, Base64.DEFAULT), StandardCharsets.UTF_8)
        } catch (_: Exception) {
            String(Base64.decode(b64, Base64.URL_SAFE), StandardCharsets.UTF_8)
        }
        val vmessJson = JSONObject(jsonStr)

        val server = vmessJson.optString("add")
        val port = vmessJson.optInt("port", 443)
        val uuid = vmessJson.optString("id")
        val tag = cleanNodeName(vmessJson.optString("ps"))

        val outbound = JSONObject()
        outbound.put("type", "vmess")
        outbound.put("tag", tag)
        outbound.put("server", server)
        outbound.put("server_port", port)
        outbound.put("uuid", uuid)
        outbound.put("alter_id", vmessJson.optInt("aid", 0))
        outbound.put("security", vmessJson.optString("scy", "auto"))

        if (vmessJson.optString("tls").equals("tls", ignoreCase = true)) {
            val tlsObj = JSONObject()
            tlsObj.put("enabled", true)
            val sni = vmessJson.optString("sni").ifEmpty { vmessJson.optString("host") }
            if (sni.isNotEmpty()) tlsObj.put("server_name", sni)
            outbound.put("tls", tlsObj)
        }

        val net = vmessJson.optString("net", "tcp")
        if (net == "ws" || net == "grpc") {
            val transportObj = JSONObject()
            transportObj.put("type", net)
            val path = vmessJson.optString("path")
            if (net == "ws") {
                if (path.isNotEmpty()) transportObj.put("path", path)
                val host = vmessJson.optString("host")
                if (host.isNotEmpty()) {
                    val headers = JSONObject()
                    headers.put("Host", host)
                    transportObj.put("headers", headers)
                }
            } else if (net == "grpc" && path.isNotEmpty()) {
                transportObj.put("service_name", path)
            }
            outbound.put("transport", transportObj)
        }

        return outbound
    }

    private fun parseTrojan(uriStr: String): JSONObject? {
        val uri = URI(uriStr)
        val password = uri.userInfo ?: return null
        val server = uri.host ?: return null
        val port = if (uri.port > 0) uri.port else 443
        val tag = cleanNodeName(uri.rawFragment ?: uri.fragment)
        val params = parseQueryParams(uri.rawQuery)

        val outbound = JSONObject()
        outbound.put("type", "trojan")
        outbound.put("tag", tag)
        outbound.put("server", server)
        outbound.put("server_port", port)
        outbound.put("password", password)

        val tlsObj = JSONObject()
        tlsObj.put("enabled", true)
        tlsObj.put("server_name", params["sni"] ?: params["peer"] ?: server)
        outbound.put("tls", tlsObj)

        val transportType = params["type"] ?: "tcp"
        if (transportType == "ws" || transportType == "grpc") {
            val transportObj = JSONObject()
            transportObj.put("type", transportType)
            if (transportType == "ws") {
                params["path"]?.let { transportObj.put("path", URLDecoder.decode(it, "UTF-8")) }
            } else if (transportType == "grpc") {
                params["serviceName"]?.let { transportObj.put("service_name", URLDecoder.decode(it, "UTF-8")) }
            }
            outbound.put("transport", transportObj)
        }

        return outbound
    }

    private fun parseShadowsocks(uriStr: String): JSONObject? {
        val uri = URI(uriStr)
        val tag = cleanNodeName(uri.rawFragment ?: uri.fragment)

        var method = ""
        var password = ""
        var server = ""
        var port = 8388

        if (uri.userInfo != null) {
            val decodedUserInfo = try {
                String(Base64.decode(uri.userInfo, Base64.DEFAULT), StandardCharsets.UTF_8)
            } catch (_: Exception) {
                uri.userInfo
            }
            val parts = decodedUserInfo.split(":", limit = 2)
            if (parts.size == 2) {
                method = parts[0]
                password = parts[1]
            }
            server = uri.host ?: ""
            port = if (uri.port > 0) uri.port else 8388
        } else {
            val b64Part = uriStr.removePrefix("ss://").substringBefore("#").trim()
            val decoded = try {
                String(Base64.decode(b64Part, Base64.DEFAULT), StandardCharsets.UTF_8)
            } catch (_: Exception) {
                String(Base64.decode(b64Part, Base64.URL_SAFE), StandardCharsets.UTF_8)
            }
            val atSplit = decoded.split("@", limit = 2)
            if (atSplit.size == 2) {
                val creds = atSplit[0].split(":", limit = 2)
                method = creds[0]
                password = creds.getOrElse(1) { "" }
                val hostPort = atSplit[1].split(":", limit = 2)
                server = hostPort[0]
                port = hostPort.getOrElse(1) { "8388" }.toIntOrNull() ?: 8388
            }
        }

        if (server.isEmpty() || method.isEmpty()) return null

        val outbound = JSONObject()
        outbound.put("type", "shadowsocks")
        outbound.put("tag", tag)
        outbound.put("server", server)
        outbound.put("server_port", port)
        outbound.put("method", method)
        outbound.put("password", password)
        return outbound
    }

    private fun parseQueryParams(query: String?): Map<String, String> {
        if (query.isNullOrEmpty()) return emptyMap()
        val result = mutableMapOf<String, String>()
        for (pair in query.split("&")) {
            val idx = pair.indexOf("=")
            if (idx > 0) {
                result[pair.substring(0, idx)] = pair.substring(idx + 1)
            }
        }
        return result
    }

    private fun buildSingBoxConfig(nodes: List<JSONObject>): String {
        val usedTags = mutableMapOf<String, Int>()
        for (node in nodes) {
            val originalTag = node.getString("tag")
            val count = usedTags.getOrDefault(originalTag, 0)
            if (count > 0) {
                node.put("tag", "$originalTag ($count)")
            }
            usedTags[originalTag] = count + 1
        }

        val root = JSONObject()

        root.put("log", JSONObject().apply {
            put("level", "warn")
            put("timestamp", true)
        })

        val dnsObj = JSONObject()
        val dnsServers = JSONArray().apply {
            put(JSONObject().apply {
                put("tag", "dns-remote")
                put("address", "https://1.1.1.1/dns-query")
                put("address_resolver", "dns-direct")
                put("strategy", "ipv4_only")
                put("detour", "proxy")
            })
            put(JSONObject().apply {
                put("tag", "dns-direct")
                put("address", "77.88.8.8")
                put("strategy", "ipv4_only")
                put("detour", "direct")
            })
            put(JSONObject().apply {
                put("tag", "dns-block")
                put("address", "rcode://success")
            })
        }
        dnsObj.put("servers", dnsServers)
        dnsObj.put("rules", JSONArray().apply {
            put(JSONObject().apply {
                put("outbound", "any")
                put("server", "dns-direct")
            })
            put(JSONObject().apply {
                put("domain_suffix", JSONArray().apply {
                    put(".ru")
                    put(".su")
                    put(".xn--p1ai")
                    put("vk.com")
                    put("yandex.ru")
                    put("gosuslugi.ru")
                })
                put("server", "dns-direct")
            })
        })
        dnsObj.put("final", "dns-remote")
        dnsObj.put("strategy", "ipv4_only")
        dnsObj.put("independent_cache", true)
        root.put("dns", dnsObj)

        root.put("inbounds", JSONArray().apply {
            put(JSONObject().apply {
                put("type", "tun")
                put("tag", "tun-in")
                put("interface_name", "tun0")
                put("inet4_address", "172.19.0.1/30")
                put("auto_route", true)
                put("strict_route", false)
                put("stack", "gvisor")
                put("sniff", true)
            })
        })

        val outboundsArr = JSONArray()

        val selector = JSONObject().apply {
            put("type", "selector")
            put("tag", "proxy")
            val selectorOutbounds = JSONArray()
            for (node in nodes) {
                selectorOutbounds.put(node.getString("tag"))
            }
            selectorOutbounds.put("direct")
            put("outbounds", selectorOutbounds)
            if (nodes.isNotEmpty()) {
                put("default", nodes[0].getString("tag"))
            }
        }
        outboundsArr.put(selector)

        for (node in nodes) {
            outboundsArr.put(node)
        }

        outboundsArr.put(JSONObject().apply { put("type", "direct"); put("tag", "direct") })
        outboundsArr.put(JSONObject().apply { put("type", "block"); put("tag", "block") })
        outboundsArr.put(JSONObject().apply { put("type", "dns"); put("tag", "dns-out") })
        root.put("outbounds", outboundsArr)

        root.put("route", JSONObject().apply {
            put("rules", JSONArray().apply {
                put(JSONObject().apply {
                    put("protocol", "dns")
                    put("outbound", "dns-out")
                })
                put(JSONObject().apply {
                    put("ip_is_private", true)
                    put("outbound", "direct")
                })
                put(JSONObject().apply {
                    put("package_name", JSONArray().apply {
                        put("ru.vk.store")
                        put("com.vk.store")
                    })
                    put("outbound", "direct")
                })
            })
            put("final", "proxy")
            put("auto_detect_interface", true)
        })

        return root.toString(2)
    }

    override fun close() {
        client.close()
    }

    companion object {
        const val userAgent = "SFAxtnd"
        private val hwidMemoryCache = ConcurrentHashMap<String, String>()
    }
}
