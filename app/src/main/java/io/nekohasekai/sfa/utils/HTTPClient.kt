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
        val mode = SubscriptionRouting.detectMode(trimmed)

        if (trimmed.startsWith("{") && (trimmed.contains("\"outbounds\"") || trimmed.contains("\"route\""))) {
            return sanitizeAndMigrateConfig(trimmed, mode)
        }

        val contentToParse = tryDecodeBase64(trimmed)

        if (contentToParse.startsWith("{") && (contentToParse.contains("\"outbounds\"") || contentToParse.contains("\"route\""))) {
            return sanitizeAndMigrateConfig(contentToParse, mode)
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
                    return buildSingBoxConfig(nodes, mode)
                }
            } catch (e: Exception) {
            }
        }

        val nodes = parseUriLines(contentToParse)
        if (nodes.isNotEmpty()) {
            return buildSingBoxConfig(nodes, mode)
        }

        return sanitizeAndMigrateConfig(trimmed, mode)
    }

    private fun sanitizeAndMigrateConfig(jsonStr: String, mode: SubscriptionRouting.Mode): String {
        return try {
            val fixedRaw = jsonStr
                .replace("https://raw.githubusercontent.com/SagerNet/sing-geosite/rule-set/geosite-ru.srs", "https://raw.githubusercontent.com/SagerNet/sing-geosite/rule-set/geosite-category-ru.srs")
                .replace("\"geosite-ru\"", "\"geosite-category-ru\"")

            val root = JSONObject(fixedRaw)
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

            SubscriptionRouting.apply(root, mode)

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
            URLDecoder.decode(trimmed, "UTF-8")
        } catch (e: Exception) {
            trimmed
        }
    }

    private fun parseUriLines(text: String): List<JSONObject> {
        val nodes = mutableListOf<JSONObject>()
        for (line in text.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//")) continue
            val node = when {
                trimmed.startsWith("vless://") -> parseVless(trimmed)
                trimmed.startsWith("vmess://") -> parseVmess(trimmed)
                trimmed.startsWith("trojan://") -> parseTrojan(trimmed)
                trimmed.startsWith("ss://") -> parseShadowsocks(trimmed)
                else -> null
            }
            if (node != null) nodes.add(node)
        }
        return nodes
    }

    private fun parseVless(line: String): JSONObject? {
        val withoutScheme = line.removePrefix("vless://")
        val hashIdx = withoutScheme.lastIndexOf("#")
        val rawTag = if (hashIdx >= 0) withoutScheme.substring(hashIdx + 1) else null
        val main = if (hashIdx >= 0) withoutScheme.substring(0, hashIdx) else withoutScheme
        val tag = cleanNodeName(rawTag)

        val atIdx = main.indexOf("@")
        if (atIdx < 0) return null
        val uuid = main.substring(0, atIdx)
        val rest = main.substring(atIdx + 1)
        val qIdx = rest.indexOf("?")
        val hostPort = if (qIdx >= 0) rest.substring(0, qIdx) else rest
        val query = if (qIdx >= 0) rest.substring(qIdx + 1) else null
        val params = parseQueryParams(query)

        val host = hostPort.substringBefore(":")
        val port = hostPort.substringAfter(":", "443").toIntOrNull() ?: 443
        if (host.isEmpty() || uuid.isEmpty()) return null

        val outbound = JSONObject()
        outbound.put("type", "vless")
        outbound.put("tag", tag)
        outbound.put("server", host)
        outbound.put("server_port", port)
        outbound.put("uuid", uuid)
        val flow = params["flow"]
        if (!flow.isNullOrBlank()) outbound.put("flow", flow)

        val network = params["type"] ?: "tcp"
        val security = params["security"] ?: "none"
        val sni = params["sni"] ?: params["host"] ?: host
        val fp = params["fp"] ?: "chrome"
        val alpn = params["alpn"]
        val path = params["path"] ?: "/"
        val hostHeader = params["host"] ?: sni

        if (security == "reality") {
            val tls = JSONObject()
            tls.put("enabled", true)
            tls.put("server_name", sni)
            tls.put("utls", JSONObject().apply {
                put("enabled", true)
                put("fingerprint", fp)
            })
            val pbk = params["pbk"]
            val sid = params["sid"]
            if (!pbk.isNullOrBlank()) {
                tls.put("reality", JSONObject().apply {
                    put("enabled", true)
                    put("public_key", pbk)
                    if (!sid.isNullOrBlank()) put("short_id", sid)
                })
            }
            outbound.put("tls", tls)
        } else if (security == "tls") {
            val tls = JSONObject()
            tls.put("enabled", true)
            tls.put("server_name", sni)
            if (!alpn.isNullOrBlank()) {
                tls.put("alpn", JSONArray(alpn.split(",")))
            }
            outbound.put("tls", tls)
        }

        if (network == "ws") {
            outbound.put("transport", JSONObject().apply {
                put("type", "ws")
                put("path", path)
                put("headers", JSONObject().apply { put("Host", hostHeader) })
            })
        } else if (network == "grpc") {
            outbound.put("transport", JSONObject().apply {
                put("type", "grpc")
                put("service_name", params["serviceName"] ?: params["service_name"] ?: "")
            })
        }

        return outbound
    }

    private fun parseVmess(line: String): JSONObject? {
        val b64 = line.removePrefix("vmess://").trim()
        val decoded = try {
            val raw = Base64.decode(b64, Base64.DEFAULT)
            String(raw, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            try {
                val raw = Base64.decode(b64, Base64.URL_SAFE)
                String(raw, StandardCharsets.UTF_8)
            } catch (e2: Exception) {
                return null
            }
        }
        val vmessJson = try {
            JSONObject(decoded)
        } catch (e: Exception) {
            return null
        }

        val tag = cleanNodeName(vmessJson.optString("ps"))
        val host = vmessJson.optString("add")
        val port = vmessJson.optInt("port", 443)
        val uuid = vmessJson.optString("id")
        if (host.isEmpty() || uuid.isEmpty()) return null

        val outbound = JSONObject()
        outbound.put("type", "vmess")
        outbound.put("tag", tag)
        outbound.put("server", host)
        outbound.put("server_port", port)
        outbound.put("uuid", uuid)
        outbound.put("security", vmessJson.optString("scy", "auto"))
        val aid = vmessJson.optInt("aid", 0)
        if (aid > 0) outbound.put("alter_id", aid)

        val network = vmessJson.optString("net", "tcp")
        val tlsFlag = vmessJson.optString("tls")
        val sni = vmessJson.optString("sni").ifEmpty { vmessJson.optString("host").ifEmpty { host } }

        if (tlsFlag.equals("tls", true)) {
            outbound.put("tls", JSONObject().apply {
                put("enabled", true)
                put("server_name", sni)
            })
        }

        if (network == "ws") {
            outbound.put("transport", JSONObject().apply {
                put("type", "ws")
                put("path", vmessJson.optString("path", "/"))
                put("headers", JSONObject().apply {
                    put("Host", vmessJson.optString("host").ifEmpty { sni })
                })
            })
        } else if (network == "grpc") {
            outbound.put("transport", JSONObject().apply {
                put("type", "grpc")
                put("service_name", vmessJson.optString("path", ""))
            })
        }

        return outbound
    }

    private fun parseTrojan(line: String): JSONObject? {
        val withoutScheme = line.removePrefix("trojan://")
        val hashIdx = withoutScheme.lastIndexOf("#")
        val rawTag = if (hashIdx >= 0) withoutScheme.substring(hashIdx + 1) else null
        val main = if (hashIdx >= 0) withoutScheme.substring(0, hashIdx) else withoutScheme
        val tag = cleanNodeName(rawTag)

        val atIdx = main.indexOf("@")
        if (atIdx < 0) return null
        val password = main.substring(0, atIdx)
        val rest = main.substring(atIdx + 1)
        val qIdx = rest.indexOf("?")
        val hostPort = if (qIdx >= 0) rest.substring(0, qIdx) else rest
        val query = if (qIdx >= 0) rest.substring(qIdx + 1) else null
        val params = parseQueryParams(query)

        val host = hostPort.substringBefore(":")
        val port = hostPort.substringAfter(":", "443").toIntOrNull() ?: 443
        if (host.isEmpty() || password.isEmpty()) return null

        val outbound = JSONObject()
        outbound.put("type", "trojan")
        outbound.put("tag", tag)
        outbound.put("server", host)
        outbound.put("server_port", port)
        outbound.put("password", password)

        val sni = params["sni"] ?: params["host"] ?: host
        outbound.put("tls", JSONObject().apply {
            put("enabled", true)
            put("server_name", sni)
        })

        val network = params["type"] ?: "tcp"
        if (network == "ws") {
            outbound.put("transport", JSONObject().apply {
                put("type", "ws")
                put("path", params["path"] ?: "/")
                put("headers", JSONObject().apply {
                    put("Host", params["host"] ?: sni)
                })
            })
        }

        return outbound
    }

    private fun parseShadowsocks(line: String): JSONObject? {
        val withoutScheme = line.removePrefix("ss://")
        val hashIdx = withoutScheme.lastIndexOf("#")
        val rawTag = if (hashIdx >= 0) withoutScheme.substring(hashIdx + 1) else null
        val main = if (hashIdx >= 0) withoutScheme.substring(0, hashIdx) else withoutScheme
        val tag = cleanNodeName(rawTag)

        var method = ""
        var password = ""
        var server = ""
        var port = 8388

        val atIdx = main.indexOf("@")
        if (atIdx >= 0) {
            val userInfo = main.substring(0, atIdx)
            val hostPort = main.substring(atIdx + 1).substringBefore("?")
            try {
                val decoded = String(Base64.decode(userInfo, Base64.DEFAULT), StandardCharsets.UTF_8)
                method = decoded.substringBefore(":")
                password = decoded.substringAfter(":")
            } catch (e: Exception) {
                method = userInfo.substringBefore(":")
                password = userInfo.substringAfter(":")
            }
            if (hostPort.contains(":") && !hostPort.startsWith("[")) {
                server = hostPort.substringBeforeLast(":")
                port = hostPort.substringAfterLast(":").toIntOrNull() ?: 8388
            } else {
                server = hostPort
            }
        } else {
            try {
                val decoded = String(Base64.decode(main.substringBefore("?"), Base64.DEFAULT), StandardCharsets.UTF_8)
                val at2 = decoded.indexOf("@")
                if (at2 >= 0) {
                    val userInfo = decoded.substring(0, at2)
                    val hostPort = decoded.substring(at2 + 1)
                    method = userInfo.substringBefore(":")
                    password = userInfo.substringAfter(":")
                    if (hostPort.contains(":")) {
                        server = hostPort.substringBeforeLast(":")
                        port = hostPort.substringAfterLast(":").toIntOrNull() ?: 8388
                    } else {
                        server = hostPort
                    }
                }
            } catch (e: Exception) {
                return null
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
    private fun buildSingBoxConfig(nodes: List<JSONObject>, mode: SubscriptionRouting.Mode): String {
        val validNodes = nodes.filter { it.optString("type") != "dns" }

        val usedTags = mutableMapOf<String, Int>()
        usedTags[SubscriptionRouting.NORMAL_SELECTOR_TAG] = 1
        usedTags[SubscriptionRouting.WHITELIST_SELECTOR_TAG] = 1
        usedTags["direct"] = 1
        usedTags["block"] = 1

        val normalProxyTags = mutableListOf<String>()
        val whitelistProxyTags = mutableListOf<String>()

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
                if (SubscriptionRouting.isWhitelistBypassTag(uniqueTag)) {
                    whitelistProxyTags.add(uniqueTag)
                } else {
                    normalProxyTags.add(uniqueTag)
                }
            }
        }

        if (normalProxyTags.isEmpty() && whitelistProxyTags.isEmpty()) {
            for (node in validNodes) {
                normalProxyTags.add(node.getString("tag"))
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
                put("detour", SubscriptionRouting.NORMAL_SELECTOR_TAG)
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
                put("rule_set", JSONArray().apply { put("geosite-category-ru") })
                put("server", "dns-direct")
            })
            put(JSONObject().apply {
                put("domain_suffix", JSONArray().apply {
                    put(".ru"); put(".su"); put(".xn--p1ai"); put(".by"); put(".kz")
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
                put("address", JSONArray().apply { put("172.19.0.1/30") })
                put("auto_route", true)
                put("strict_route", false)
                put("stack", "gvisor")
            })
        })

        val outboundsArr = JSONArray()

        val hasNormalSelector = validNodes.any {
            it.optString("tag") == SubscriptionRouting.NORMAL_SELECTOR_TAG
        }
        if (!hasNormalSelector) {
            val tagsForNormal = normalProxyTags.ifEmpty { whitelistProxyTags }
            outboundsArr.put(JSONObject().apply {
                put("type", "selector")
                put("tag", SubscriptionRouting.NORMAL_SELECTOR_TAG)
                val outs = JSONArray()
                tagsForNormal.forEach { outs.put(it) }
                outs.put("direct")
                put("outbounds", outs)
                if (tagsForNormal.isNotEmpty()) put("default", tagsForNormal[0])
            })
        }

        val needWhitelistSelector =
            mode == SubscriptionRouting.Mode.WHITELIST_BYPASS || whitelistProxyTags.isNotEmpty()

        val hasWhitelistSelector = validNodes.any {
            it.optString("tag") == SubscriptionRouting.WHITELIST_SELECTOR_TAG
        }
        if (needWhitelistSelector && !hasWhitelistSelector) {
            val tagsForWl = whitelistProxyTags.ifEmpty { normalProxyTags }
            outboundsArr.put(JSONObject().apply {
                put("type", "selector")
                put("tag", SubscriptionRouting.WHITELIST_SELECTOR_TAG)
                val outs = JSONArray()
                tagsForWl.forEach { outs.put(it) }
                outs.put("direct")
                put("outbounds", outs)
                if (tagsForWl.isNotEmpty()) put("default", tagsForWl[0])
            })
        }

        for (node in validNodes) {
            outboundsArr.put(node)
        }

        if (validNodes.none { it.optString("tag") == "direct" }) {
            outboundsArr.put(JSONObject().apply {
                put("type", "direct")
                put("tag", "direct")
            })
        }
        if (validNodes.none { it.optString("tag") == "block" }) {
            outboundsArr.put(JSONObject().apply {
                put("type", "block")
                put("tag", "block")
            })
        }

        root.put("outbounds", outboundsArr)

        SubscriptionRouting.apply(root, mode)

        return root.toString(2)
    }

    override fun close() {
        client.close()
    }

    companion object {
        const val userAgent = "SFAxtnd"
    }
}
