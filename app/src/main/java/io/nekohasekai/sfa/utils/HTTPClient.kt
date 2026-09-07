package io.nekohasekai.sfa.utils

import android.content.Context
import android.os.Build
import android.util.Base64
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.ktx.unwrap
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
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

                        if (server.optString("detour") == "direct") {
                            server.remove("detour")
                        }

                        if (server.has("address") && !server.has("type")) {
                            val addr = server.remove("address").toString().trim()
                            try {
                                when {
                                    addr == "local" || addr.startsWith("rcode://") -> server.put("type", "local")
                                    addr.startsWith("https://") -> {
                                        server.put("type", "https")
                                        val cleanAddr = addr.removePrefix("https://")
                                        val host = cleanAddr.substringBefore("/").substringBefore(":")
                                        val path = if (cleanAddr.contains("/")) "/" + cleanAddr.substringAfter("/") else "/dns-query"
                                        server.put("server", host)
                                        server.put("path", path)
                                    }
                                    addr.startsWith("tls://") -> {
                                        server.put("type", "tls")
                                        val cleanAddr = addr.removePrefix("tls://")
                                        server.put("server", cleanAddr.substringBefore(":"))
                                    }
                                    addr.startsWith("tcp://") -> {
                                        server.put("type", "tcp")
                                        val cleanAddr = addr.removePrefix("tcp://")
                                        server.put("server", cleanAddr.substringBefore(":"))
                                    }
                                    addr.startsWith("udp://") -> {
                                        server.put("type", "udp")
                                        val cleanAddr = addr.removePrefix("udp://")
                                        server.put("server", cleanAddr.substringBefore(":"))
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
                if (!route.has("default_domain_resolver")) {
                    route.put("default_domain_resolver", "dns-direct")
                }

                val rules = route.optJSONArray("rules")
                if (rules != null) {
                    var hasSniff = false
                    for (i in 0 until rules.length()) {
                        val rule = rules.optJSONObject(i) ?: continue
                        if (rule.optString("action") == "sniff") {
                            hasSniff = true
                        }
                        if (rule.optString("protocol") == "dns" || rule.optString("outbound") == "dns-out") {
                            rule.remove("outbound")
                            rule.put("action", "hijack-dns")
                        }
                    }
                    if (!hasSniff) {
                        val newRules = JSONArray()
                        newRules.put(JSONObject().apply { put("action", "sniff") })
                        for (i in 0 until rules.length()) {
                            newRules.put(rules.get(i))
                        }
                        route.put("rules", newRules)
                    }
                }
            }

            root.toString(2)
        } catch (e: Exception) {
            jsonStr
        }
    }

    private fun tryDecodeBase64(text: String): String {
        val trimmed = text.trim()
        if (trimmed.contains("://") || trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return trimmed
        }
        val nonCommentLines = text.lines().filter {
            val t = it.trim()
            t.isNotEmpty() && !t.startsWith("//") && !t.startsWith("#")
        }.joinToString("")

        for (flags in intArrayOf(Base64.DEFAULT, Base64.URL_SAFE)) {
            try {
                val decoded = Base64.decode(nonCommentLines, flags)
                val decodedStr = String(decoded, StandardCharsets.UTF_8).trim()
                if (decodedStr.contains("://") || decodedStr.startsWith("{") || decodedStr.startsWith("[")) {
                    return decodedStr
                }
            } catch (e: Exception) {
            }
        }
        return trimmed
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

    private fun parseVless(line: String): JSONObject? {
        val rawTag = if (line.contains("#")) line.substringAfter("#") else ""
        val tag = cleanNodeName(rawTag)

        val withoutTag = line.substringBefore("#")
        val queryStr = if (withoutTag.contains("?")) withoutTag.substringAfter("?") else ""
        val mainPart = withoutTag.substringBefore("?").removePrefix("vless://")

        if (!mainPart.contains("@")) return null

        val uuid = mainPart.substringBefore("@").trim()
        val hostPort = mainPart.substringAfter("@").trim()
        if (uuid.isEmpty() || hostPort.isEmpty()) return null

        val server: String
        val port: Int

        if (hostPort.startsWith("[") && hostPort.contains("]:")) {
            server = hostPort.substringBefore("]:") + "]"
            port = hostPort.substringAfter("]:").toIntOrNull() ?: 443
        } else if (hostPort.contains(":") && !hostPort.startsWith("[")) {
            server = hostPort.substringBeforeLast(":")
            port = hostPort.substringAfterLast(":").toIntOrNull() ?: 443
        } else {
            server = hostPort
            port = 443
        }

        if (server.isEmpty()) return null

        val params = parseQueryParams(queryStr)
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

    private fun parseVmess(line: String): JSONObject? {
        val b64 = line.removePrefix("vmess://").trim()
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

    private fun parseTrojan(line: String): JSONObject? {
        val rawTag = if (line.contains("#")) line.substringAfter("#") else ""
        val tag = cleanNodeName(rawTag)

        val withoutTag = line.substringBefore("#")
        val queryStr = if (withoutTag.contains("?")) withoutTag.substringAfter("?") else ""
        val mainPart = withoutTag.substringBefore("?").removePrefix("trojan://")

        if (!mainPart.contains("@")) return null

        val password = mainPart.substringBefore("@").trim()
        val hostPort = mainPart.substringAfter("@").trim()
        if (password.isEmpty() || hostPort.isEmpty()) return null

        val server: String
        val port: Int

        if (hostPort.startsWith("[") && hostPort.contains("]:")) {
            server = hostPort.substringBefore("]:") + "]"
            port = hostPort.substringAfter("]:").toIntOrNull() ?: 443
        } else if (hostPort.contains(":") && !hostPort.startsWith("[")) {
            server = hostPort.substringBeforeLast(":")
            port = hostPort.substringAfterLast(":").toIntOrNull() ?: 443
        } else {
            server = hostPort
            port = 443
        }

        if (server.isEmpty()) return null

        val params = parseQueryParams(queryStr)
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

    private fun parseShadowsocks(line: String): JSONObject? {
        val rawTag = if (line.contains("#")) line.substringAfter("#") else ""
        val tag = cleanNodeName(rawTag)

        val withoutTag = line.substringBefore("#").removePrefix("ss://").trim()
        var method = ""
        var password = ""
        var server = ""
        var port = 8388

        if (withoutTag.contains("@")) {
            val userPart = withoutTag.substringBefore("@")
            val hostPort = withoutTag.substringAfter("@")

            val decodedUserInfo = try {
                String(Base64.decode(userPart, Base64.DEFAULT), StandardCharsets.UTF_8)
            } catch (e: Exception) {
                userPart
            }

            val parts = decodedUserInfo.split(":", limit = 2)
            if (parts.size == 2) {
                method = parts[0]
                password = parts[1]
            }

            if (hostPort.startsWith("[") && hostPort.contains("]:")) {
                server = hostPort.substringBefore("]:") + "]"
                port = hostPort.substringAfter("]:").toIntOrNull() ?: 8388
            } else if (hostPort.contains(":")) {
                server = hostPort.substringBeforeLast(":")
                port = hostPort.substringAfterLast(":").toIntOrNull() ?: 8388
            } else {
                server = hostPort
            }
        } else {
            val decoded = try {
                String(Base64.decode(withoutTag, Base64.DEFAULT), StandardCharsets.UTF_8)
            } catch (e: Exception) {
                try {
                    String(Base64.decode(withoutTag, Base64.URL_SAFE), StandardCharsets.UTF_8)
                } catch (e2: Exception) {
                    ""
                }
            }
            val atSplit = decoded.split("@", limit = 2)
            if (atSplit.size == 2) {
                val creds = atSplit[0].split(":", limit = 2)
                method = creds[0]
                password = creds.getOrElse(1) { "" }
                val hostPort = atSplit[1]
                if (hostPort.startsWith("[") && hostPort.contains("]:")) {
                    server = hostPort.substringBefore("]:") + "]"
                    port = hostPort.substringAfter("]:").toIntOrNull() ?: 8388
                } else if (hostPort.contains(":")) {
                    server = hostPort.substringBeforeLast(":")
                    port = hostPort.substringAfterLast(":").toIntOrNull() ?: 8388
                } else {
                    server = hostPort
                }
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
        usedTags["Выбор сервера"] = 1
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
                put("detour", "Выбор сервера")
            })
            put(JSONObject().apply {
                put("tag", "dns-direct")
                put("type", "udp")
                put("server", "77.88.8.8")
                put("server_port", 53)
            })
        }
        dnsObj.put("servers", dnsServers)
        dnsObj.put("rules", JSONArray().apply {
            put(JSONObject().apply {
                put("rule_set", JSONArray().apply {
                    put("geosite-ru")
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

        val hasProxySelector = validNodes.any { it.optString("tag") == "Выбор сервера" }
        if (!hasProxySelector) {
            val selector = JSONObject().apply {
                put("type", "selector")
                put("tag", "Выбор сервера")
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
            put("default_domain_resolver", "dns-direct")

            put("rule_set", JSONArray().apply {
                put(JSONObject().apply {
                    put("tag", "geosite-ru")
                    put("type", "remote")
                    put("format", "binary")
                    put("url", "https://raw.githubusercontent.com/SagerNet/sing-geosite/rule-set/geosite-ru.srs")
                    put("download_detour", "Выбор сервера")
                })
                put(JSONObject().apply {
                    put("tag", "geoip-ru")
                    put("type", "remote")
                    put("format", "binary")
                    put("url", "https://raw.githubusercontent.com/SagerNet/sing-geoip/rule-set/geoip-ru.srs")
                    put("download_detour", "Выбор сервера")
                })
            })

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
                    put("rule_set", JSONArray().apply {
                        put("geosite-ru")
                        put("geoip-ru")
                    })
                    put("outbound", "direct")
                })
            })
            put("final", "Выбор сервера")
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
