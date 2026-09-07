package io.nekohasekai.sfa.utils

import android.content.Context
import android.os.Build
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

private val hwidMemoryCache = ConcurrentHashMap<String, String>()

class HTTPClient : Closeable {

    private val client = Libbox.newHTTPClient()

    init {
        client.modernTLS()
    }

    fun getString(url: String): String {
        val request = client.newRequest()
        request.setURL(url)

        val hwid = getOrCreateHwid(url)

        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val rawModel = Build.MODEL
        val model = if (rawModel.startsWith(manufacturer, ignoreCase = true)) rawModel else "$manufacturer $rawModel"
        val androidVer = Build.VERSION.RELEASE
        val buildId = Build.ID.ifEmpty { "UKQ1.231003.002" }

        val userAgentStr = "sing-box/1.14.0 SFAxtnd/0.0.8 (Linux; Android $androidVer; $model Build/$buildId) HWID/$hwid"

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

        return processSubscriptionContent(rawContent)
    }

    private fun getOrCreateHwid(url: String): String {
        val normalizedUrl = url.trim()
        val urlKey = try {
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
        } catch (e: Exception) {
            try {
                val activityThreadClass = Class.forName("android.app.ActivityThread")
                val currentAppMethod = activityThreadClass.getMethod("currentApplication")
                currentAppMethod.invoke(null) as? Context
            } catch (e2: Exception) {
                null
            }
        }
    }

    private fun processSubscriptionContent(raw: String): String {
        val trimmed = raw.trim()

        if (trimmed.startsWith("{") && (trimmed.contains("\"outbounds\"") || trimmed.contains("\"route\""))) {
            return sanitizeAndMigrateConfig(trimmed)
        }

        val contentToParse = tryDecodeBase64(trimmed)

        if (contentToParse.startsWith("{") && (contentToParse.contains("\"outbounds\"") || contentToParse.contains("\"route\""))) {
            return sanitizeAndMigrateConfig(contentToParse)
        }

        if (contentToParse.startsWith("[")) {
            try {
                val jsonArray = JSONArray(contentToParse)
                val nodes = mutableListOf<JSONObject>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.optJSONObject(i) ?: continue
                    nodes.add(obj)
                }
                if (nodes.isNotEmpty()) {
                    return buildSingBoxConfig(nodes)
                }
            } catch (e: Exception) {
            }
        }

        val nodes = parseUriLines(contentToParse)
        if (nodes.isNotEmpty()) {
            return buildSingBoxConfig(nodes)
        }

        return sanitizeAndMigrateConfig(trimmed)
    }

    private fun sanitizeAndMigrateConfig(jsonStr: String): String {
        return try {
            val root = JSONObject(jsonStr)
            val dns = root.optJSONObject("dns")
            if (dns != null) {
                dns.remove("independent_cache")

                if (dns.has("address_strategy")) {
                    val strat = dns.remove("address_strategy")
                    if (!dns.has("strategy")) {
                        dns.put("strategy", strat)
                    }
                }

                val servers = dns.optJSONArray("servers")
                if (servers != null) {
                    val cleanedServers = JSONArray()
                    for (i in 0 until servers.length()) {
                        val server = servers.optJSONObject(i) ?: continue

                        server.remove("strategy")
                        server.remove("address_strategy")

                        if (server.optString("type") == "rcode") {
                            continue
                        }

                        if (server.has("address") && !server.has("type")) {
                            val addr = server.remove("address").toString().trim()
                            try {
                                when {
                                    addr == "local" || addr.startsWith("rcode://") -> server.put("type", "local")
                                    addr.startsWith("https://") -> {
                                        server.put("type", "https")
                                        val uri = URI(addr)
                                        server.put("server", uri.host ?: addr.removePrefix("https://").substringBefore("/"))
                                        val path = uri.rawPath
                                        server.put("path", if (!path.isNullOrEmpty()) path else "/dns-query")
                                    }
                                    addr.startsWith("tls://") -> {
                                        server.put("type", "tls")
                                        val uri = URI(addr)
                                        server.put("server", uri.host ?: addr.removePrefix("tls://").substringBefore(":"))
                                    }
                                    addr.startsWith("tcp://") -> {
                                        server.put("type", "tcp")
                                        val uri = URI(addr)
                                        server.put("server", uri.host ?: addr.removePrefix("tcp://").substringBefore(":"))
                                    }
                                    addr.startsWith("udp://") -> {
                                        server.put("type", "udp")
                                        val uri = URI(addr)
                                        server.put("server", uri.host ?: addr.removePrefix("udp://").substringBefore(":"))
                                    }
                                    else -> {
                                        server.put("type", "udp")
                                        server.put("server", addr)
                                    }
                                }
                            } catch (e: Exception) {
                                server.put("type", "udp")
                                server.put("server", addr.substringAfter("://").substringBefore("/"))
                            }
                        }
                        if (server.has("address_resolver")) {
                            val res = server.remove("address_resolver")
                            server.put("domain_resolver", res)
                        }
                        cleanedServers.put(server)
                    }
                    dns.put("servers", cleanedServers)
                }
            }

            val inbounds = root.optJSONArray("inbounds")
            if (inbounds != null) {
                for (i in 0 until inbounds.length()) {
                    val inbound = inbounds.optJSONObject(i) ?: continue
                    if (inbound.optString("type") == "tun") {
                        val addresses = JSONArray()
                        if (inbound.has("inet4_address")) {
                            val v = inbound.remove("inet4_address")
                            if (v is JSONArray) {
                                for (j in 0 until v.length()) addresses.put(v.get(j))
                            } else {
                                addresses.put(v)
                            }
                        }
                        if (inbound.has("inet6_address")) {
                            val v = inbound.remove("inet6_address")
                            if (v is JSONArray) {
                                for (j in 0 until v.length()) addresses.put(v.get(j))
                            } else {
                                addresses.put(v)
                            }
                        }
                        if (addresses.length() > 0 && !inbound.has("address")) {
                            inbound.put("address", addresses)
                        }
                        inbound.remove("sniff")
                    }
                }
            }

            val outbounds = root.optJSONArray("outbounds")
            if (outbounds != null) {
                val cleanedOutbounds = JSONArray()
                for (i in 0 until outbounds.length()) {
                    val ob = outbounds.optJSONObject(i) ?: continue
                    if (ob.optString("type") != "dns") {
                        cleanedOutbounds.put(ob)
                    }
                }
                root.put("outbounds", cleanedOutbounds)
            }

            val route = root.optJSONObject("route")
            if (route != null) {
                val rules = route.optJSONArray("rules")
                if (rules != null) {
                    for (i in 0 until rules.length()) {
                        val rule = rules.optJSONObject(i) ?: continue
                        if (rule.optString("protocol") == "dns" || rule.optString("outbound") == "dns-out") {
                            rule.remove("outbound")
                            rule.put("action", "hijack-dns")
                        }
                    }
                }
            }

            root.toString(2)
        } catch (e: Exception) {
            jsonStr
        }
    }

    private fun tryDecodeBase64(text: String): String {
        val clean = text.replace("\r", "").replace("\n", "").trim()
        if (clean.startsWith("vless://") || clean.startsWith("vmess://") ||
            clean.startsWith("trojan://") || clean.startsWith("ss://") ||
            clean.startsWith("{") || clean.startsWith("[")) {
            return text
        }
        for (flags in intArrayOf(Base64.DEFAULT, Base64.URL_SAFE)) {
            try {
                val decoded = Base64.decode(clean, flags)
                val decodedStr = String(decoded, StandardCharsets.UTF_8).trim()
                if (decodedStr.contains("://") || decodedStr.startsWith("{") || decodedStr.startsWith("[")) {
                    return decodedStr
                }
            } catch (e: Exception) {
            }
        }
        return text
    }

    private fun cleanNodeName(rawTag: String?): String {
        if (rawTag.isNullOrBlank()) return "Proxy"
        val trimmed = rawTag.trim()
        if (!trimmed.contains("%")) return trimmed
        return try {
            URLDecoder.decode(trimmed, StandardCharsets.UTF_8.name()).trim()
        } catch (e: Exception) {
            trimmed
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
            } catch (e: Exception) {
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
        } catch (e1: Exception) {
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
            } catch (e: Exception) {
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
            } catch (e: Exception) {
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
        val validNodes = nodes.filter { it.optString("type") != "dns" }

        val usedTags = mutableMapOf<String, Int>()
        usedTags["proxy"] = 1
        usedTags["direct"] = 1
        usedTags["block"] = 1

        val proxyTags = mutableListOf<String>()

        for (node in validNodes) {
            val rawTag = node.optString("tag").ifEmpty {
                node.optString("server").ifEmpty { "Proxy" }
            }
            val cleanTag = cleanNodeName(rawTag)
            val count = usedTags.getOrDefault(cleanTag, 0)
            val uniqueTag = if (count > 0) "$cleanTag ($count)" else cleanTag
            usedTags[cleanTag] = count + 1
            node.put("tag", uniqueTag)

            val type = node.optString("type")
            if (type !in listOf("selector", "urltest", "direct", "block", "dns")) {
                proxyTags.add(uniqueTag)
            }
        }

        if (proxyTags.isEmpty()) {
            for (node in validNodes) {
                proxyTags.add(node.getString("tag"))
            }
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
                put("type", "https")
                put("server", "1.1.1.1")
                put("path", "/dns-query")
                put("domain_resolver", "dns-direct")
                put("detour", "proxy")
            })
            put(JSONObject().apply {
                put("tag", "dns-direct")
                put("type", "udp")
                put("server", "77.88.8.8")
                put("server_port", 53)
                put("detour", "direct")
            })
        }
        dnsObj.put("servers", dnsServers)
        dnsObj.put("rules", JSONArray().apply {
            put(JSONObject().apply {
                put("domain_suffix", JSONArray().apply {
                    put(".ru")
                    put(".su")
                    put(".xn--p1ai")
                    put(".by")
                    put(".kz")
                    put("vk.com")
                    put("vk.ru")
                    put("yandex.ru")
                    put("ya.ru")
                    put("gosuslugi.ru")
                    put("tinkoff.ru")
                    put("tbank.ru")
                    put("sberbank.ru")
                    put("sber.ru")
                    put("alfabank.ru")
                    put("vtb.ru")
                    put("ozon.ru")
                    put("wildberries.ru")
                    put("avito.ru")
                    put("kinopoisk.ru")
                })
                put("server", "dns-direct")
            })
        })
        dnsObj.put("final", "dns-remote")
        dnsObj.put("strategy", "ipv4_only")
        root.put("dns", dnsObj)

        root.put("inbounds", JSONArray().apply {
            put(JSONObject().apply {
                put("type", "tun")
                put("tag", "tun-in")
                put("interface_name", "tun0")
                put("address", JSONArray().apply {
                    put("172.19.0.1/30")
                })
                put("auto_route", true)
                put("strict_route", false)
                put("stack", "gvisor")
            })
        })

        val outboundsArr = JSONArray()

        val hasProxySelector = validNodes.any { it.optString("tag") == "proxy" }
        if (!hasProxySelector) {
            val selector = JSONObject().apply {
                put("type", "selector")
                put("tag", "proxy")
                val selectorOutbounds = JSONArray()
                for (t in proxyTags) {
                    selectorOutbounds.put(t)
                }
                selectorOutbounds.put("direct")
                put("outbounds", selectorOutbounds)
                if (proxyTags.isNotEmpty()) {
                    put("default", proxyTags[0])
                }
            }
            outboundsArr.put(selector)
        }

        for (node in validNodes) {
            outboundsArr.put(node)
        }

        if (validNodes.none { it.optString("tag") == "direct" }) {
            outboundsArr.put(JSONObject().apply { put("type", "direct"); put("tag", "direct") })
        }
        if (validNodes.none { it.optString("tag") == "block" }) {
            outboundsArr.put(JSONObject().apply { put("type", "block"); put("tag", "block") })
        }
        root.put("outbounds", outboundsArr)

        root.put("route", JSONObject().apply {
            put("rules", JSONArray().apply {
                put(JSONObject().apply {
                    put("action", "sniff")
                })
                put(JSONObject().apply {
                    put("protocol", "dns")
                    put("action", "hijack-dns")
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
                put(JSONObject().apply {
                    put("domain_suffix", JSONArray().apply {
                        put(".ru")
                        put(".su")
                        put(".xn--p1ai")
                        put(".by")
                        put(".kz")
                        put("vk.com")
                        put("vk.ru")
                        put("yandex.ru")
                        put("ya.ru")
                        put("gosuslugi.ru")
                        put("tinkoff.ru")
                        put("tbank.ru")
                        put("sberbank.ru")
                        put("sber.ru")
                        put("alfabank.ru")
                        put("vtb.ru")
                        put("ozon.ru")
                        put("wildberries.ru")
                        put("avito.ru")
                        put("kinopoisk.ru")
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
    }
}
