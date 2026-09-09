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

        val manufacturer = Build.MANUFACTURER.replaceFirstChar {
            it.uppercase()
        }

        val rawModel = Build.MODEL

        val model = if (
            rawModel.startsWith(
                manufacturer,
                ignoreCase = true
            )
        ) {
            rawModel
        } else {
            "$manufacturer $rawModel"
        }

        val androidVer = Build.VERSION.RELEASE
        val buildId = Build.ID.ifEmpty {
            "UKQ1.231003.002"
        }

        val userAgentStr =
            "sing-box/1.14.0 SFAxtnd/0.0.8 " +
                "(Linux; Android $androidVer; " +
                "$model Build/$buildId) HWID/$hwid"

        request.setUserAgent(userAgentStr)

        request.setHeader("HWID", hwid)
        request.setHeader("hwid", hwid)
        request.setHeader("X-HWID", hwid)
        request.setHeader("Device-ID", hwid)
        request.setHeader("Happ-HWID", hwid)

        val fullDeviceTitle =
            "$model (Android $androidVer)"

        request.setHeader(
            "Device-Name",
            fullDeviceTitle
        )

        request.setHeader(
            "X-Device-Name",
            fullDeviceTitle
        )

        request.setHeader(
            "Happ-Device-Name",
            fullDeviceTitle
        )

        request.setHeader(
            "Device-Model",
            model
        )

        request.setHeader(
            "X-Device-Model",
            model
        )

        request.setHeader(
            "Device-OS",
            "Android $androidVer"
        )

        request.setHeader(
            "X-Device-OS",
            "Android $androidVer"
        )

        request.setHeader(
            "App-Name",
            "SFAxtnd"
        )

        request.setHeader(
            "Platform",
            "Android"
        )

        request.setHeader(
            "Accept",
            "*/*"
        )

        val response = request.execute()
        val rawContent = response.content.unwrap

        return processSubscriptionContent(rawContent)
    }

    private fun getOrCreateHwid(
        url: String
    ): String {
        val normalizedUrl = url.trim()

        val urlKey = try {
            val digest =
                MessageDigest.getInstance("SHA-256")

            val hash = digest.digest(
                normalizedUrl.toByteArray(
                    StandardCharsets.UTF_8
                )
            )

            hash.joinToString("") {
                "%02x".format(it)
            }
        } catch (e: Exception) {
            normalizedUrl.hashCode().toString()
        }

        val memoryHwid =
            hwidMemoryCache[urlKey]

        if (!memoryHwid.isNullOrBlank()) {
            return memoryHwid
        }

        val context = getApplicationContext()

        if (context != null) {
            val prefs =
                context.getSharedPreferences(
                    "subscription_hwid_store",
                    Context.MODE_PRIVATE
                )

            val savedHwid =
                prefs.getString(
                    urlKey,
                    null
                )

            if (!savedHwid.isNullOrBlank()) {
                hwidMemoryCache[urlKey] =
                    savedHwid

                return savedHwid
            }

            val newHwid =
                UUID.randomUUID()
                    .toString()
                    .replace("-", "")
                    .take(16)

            prefs.edit()
                .putString(
                    urlKey,
                    newHwid
                )
                .apply()

            hwidMemoryCache[urlKey] =
                newHwid

            return newHwid
        }

        val fallbackHwid =
            urlKey.take(16)

        hwidMemoryCache[urlKey] =
            fallbackHwid

        return fallbackHwid
    }

    private fun getApplicationContext(): Context? {
        return try {
            val appClass =
                Class.forName(
                    "io.nekohasekai.sfa.Application"
                )

            val field =
                appClass.getDeclaredField(
                    "application"
                )

            field.isAccessible = true

            field.get(null) as? Context
        } catch (e: Exception) {
            try {
                val activityThreadClass =
                    Class.forName(
                        "android.app.ActivityThread"
                    )

                val currentAppMethod =
                    activityThreadClass.getMethod(
                        "currentApplication"
                    )

                currentAppMethod.invoke(null)
                    as? Context
            } catch (e2: Exception) {
                null
            }
        }
    }

    private fun processSubscriptionContent(
        raw: String
    ): String {
        val trimmed = raw.trim()

        if (
            trimmed.startsWith("{") &&
            (
                trimmed.contains("\"outbounds\"") ||
                    trimmed.contains("\"route\"")
                )
        ) {
            return sanitizeAndMigrateConfig(trimmed)
        }

        val contentToParse =
            tryDecodeBase64(trimmed)

        if (
            contentToParse.startsWith("{") &&
            (
                contentToParse.contains(
                    "\"outbounds\""
                ) ||
                    contentToParse.contains(
                        "\"route\""
                    )
                )
        ) {
            return sanitizeAndMigrateConfig(
                contentToParse
            )
        }

        if (contentToParse.startsWith("[")) {
            try {
                val jsonArray =
                    JSONArray(contentToParse)

                val nodes =
                    mutableListOf<JSONObject>()

                for (
                    i in 0 until jsonArray.length()
                ) {
                    val obj =
                        jsonArray.optJSONObject(i)
                            ?: continue

                    nodes.add(obj)
                }

                if (nodes.isNotEmpty()) {
                    return buildSingBoxConfig(
                        nodes
                    )
                }
            } catch (e: Exception) {
            }
        }

        val nodes =
            parseUriLines(contentToParse)

        if (nodes.isNotEmpty()) {
            return buildSingBoxConfig(nodes)
        }

        return sanitizeAndMigrateConfig(trimmed)
    }

    private fun sanitizeAndMigrateConfig(
        jsonStr: String
    ): String {
        return try {
            val fixedRaw =
                jsonStr
                    .replace(
                        "https://raw.githubusercontent.com/" +
                            "SagerNet/sing-geosite/rule-set/" +
                            "geosite-ru.srs",
                        "https://raw.githubusercontent.com/" +
                            "SagerNet/sing-geosite/rule-set/" +
                            "geosite-category-ru.srs"
                    )
                    .replace(
                        "\"geosite-ru\"",
                        "\"geosite-category-ru\""
                    )

            val root = JSONObject(fixedRaw)

            migrateDns(root)
            migrateInbounds(root)
            migrateOutbounds(root)
            migrateRoute(root)

            root.toString(2)
        } catch (e: Exception) {
            jsonStr
        }
    }

    private fun migrateDns(
        root: JSONObject
    ) {
        val dns =
            root.optJSONObject("dns")
                ?: return

        dns.remove("independent_cache")

        if (dns.has("address_strategy")) {
            val strategy =
                dns.remove("address_strategy")

            if (!dns.has("strategy")) {
                dns.put(
                    "strategy",
                    strategy
                )
            }
        }

        val servers =
            dns.optJSONArray("servers")
                ?: return

        val cleanedServers = JSONArray()

        for (
            i in 0 until servers.length()
        ) {
            val server =
                servers.optJSONObject(i)
                    ?: continue

            server.remove("strategy")
            server.remove("address_strategy")

            if (
                server.optString("type") ==
                    "rcode"
            ) {
                continue
            }

            if (
                server.optString("detour") ==
                    "direct"
            ) {
                server.remove("detour")
            }

            migrateDnsServer(server)

            if (
                server.has("address_resolver")
            ) {
                val resolver =
                    server.remove(
                        "address_resolver"
                    )

                server.put(
                    "domain_resolver",
                    resolver
                )
            }

            cleanedServers.put(server)
        }

        dns.put(
            "servers",
            cleanedServers
        )
    }

    private fun migrateDnsServer(
        server: JSONObject
    ) {
        if (
            !server.has("address") ||
            server.has("type")
        ) {
            return
        }

        val address =
            server.remove("address")
                .toString()
                .trim()

        try {
            when {
                address == "local" ||
                    address.startsWith(
                        "rcode://"
                    ) -> {
                    server.put(
                        "type",
                        "local"
                    )
                }

                address.startsWith(
                    "https://"
                ) -> {
                    val cleanAddress =
                        address.removePrefix(
                            "https://"
                        )

                    val hostPort =
                        cleanAddress.substringBefore(
                            "/"
                        )

                    val host =
                        hostPort.substringBefore(
                            ":"
                        )

                    val portString =
                        hostPort.substringAfter(
                            ":",
                            ""
                        )

                    server.put(
                        "type",
                        "https"
                    )

                    server.put(
                        "server",
                        host
                    )

                    if (
                        portString.isNotEmpty()
                    ) {
                        portString
                            .toIntOrNull()
                            ?.let {
                                server.put(
                                    "server_port",
                                    it
                                )
                            }
                    }

                    val path =
                        if (
                            cleanAddress.contains("/")
                        ) {
                            "/" +
                                cleanAddress.substringAfter(
                                    "/"
                                )
                        } else {
                            "/dns-query"
                        }

                    server.put(
                        "path",
                        path
                    )
                }

                address.startsWith(
                    "tls://"
                ) -> {
                    val cleanAddress =
                        address.removePrefix(
                            "tls://"
                        )

                    server.put(
                        "type",
                        "tls"
                    )

                    server.put(
                        "server",
                        cleanAddress.substringBefore(
                            ":"
                        )
                    )
                }

                address.startsWith(
                    "tcp://"
                ) -> {
                    val cleanAddress =
                        address.removePrefix(
                            "tcp://"
                        )

                    server.put(
                        "type",
                        "tcp"
                    )

                    server.put(
                        "server",
                        cleanAddress.substringBefore(
                            ":"
                        )
                    )
                }

                address.startsWith(
                    "udp://"
                ) -> {
                    val cleanAddress =
                        address.removePrefix(
                            "udp://"
                        )

                    server.put(
                        "type",
                        "udp"
                    )

                    server.put(
                        "server",
                        cleanAddress.substringBefore(
                            ":"
                        )
                    )
                }

                else -> {
                    server.put(
                        "type",
                        "udp"
                    )

                    server.put(
                        "server",
                        address
                    )
                }
            }
        } catch (e: Exception) {
            server.put(
                "type",
                "udp"
            )

            server.put(
                "server",
                address
                    .substringAfter("://")
                    .substringBefore("/")
            )
        }
    }
                  if (server.has("address_resolver")) {
                    val resolver = server.remove("address_resolver")
                    if (!server.has("domain_resolver")) {
                        server.put("domain_resolver", resolver)
                    }
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
                    val value = inbound.remove("inet4_address")

                    when (value) {
                        is JSONArray -> {
                            for (j in 0 until value.length()) {
                                addresses.put(value.get(j))
                            }
                        }

                        else -> addresses.put(value)
                    }
                }

                if (inbound.has("inet6_address")) {
                    val value = inbound.remove("inet6_address")

                    when (value) {
                        is JSONArray -> {
                            for (j in 0 until value.length()) {
                                addresses.put(value.get(j))
                            }
                        }

                        else -> addresses.put(value)
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
            val outbound = outbounds.optJSONObject(i) ?: continue

            if (outbound.optString("type") != "dns") {
                cleanedOutbounds.put(outbound)
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
            val newRules = JSONArray()

            var hasSniff = false
            var hasHijackDns = false

            for (i in 0 until rules.length()) {
                val rule = rules.optJSONObject(i) ?: continue

                when {
                    rule.optString("action") == "sniff" -> {
                        hasSniff = true
                    }

                    rule.optString("action") == "hijack-dns" -> {
                        hasHijackDns = true
                    }

                    rule.optString("protocol") == "dns" ||
                        rule.optString("outbound") == "dns-out" -> {

                        rule.remove("outbound")
                        rule.remove("protocol")
                        rule.put("action", "hijack-dns")

                        hasHijackDns = true
                    }
                }

                newRules.put(rule)
            }

            if (!hasSniff) {
                newRules.put(
                    0,
                    JSONObject().apply {
                        put("action", "sniff")
                    }
                )
            }

            if (!hasHijackDns) {
                var insertIndex = 0

                for (i in 0 until newRules.length()) {
                    val rule = newRules.optJSONObject(i)

                    if (rule?.optString("action") == "sniff") {
                        insertIndex = i + 1
                        break
                    }
                }

                newRules.put(
                    insertIndex,
                    JSONObject().apply {
                        put("protocol", "dns")
                        put("action", "hijack-dns")
                    }
                )
            }

            route.put("rules", newRules)
        }
    }

    SubscriptionRouting.apply(root)

    return root.toString(2)
} catch (e: Exception) {
    jsonStr
}
}

private fun tryDecodeBase64(text: String): String {
    val trimmed = text.trim()

    if (
        trimmed.contains("://") ||
        trimmed.startsWith("{") ||
        trimmed.startsWith("[")
    ) {
        return trimmed
    }

    val nonCommentLines = text
        .lines()
        .filter {
            val line = it.trim()

            line.isNotEmpty() &&
                !line.startsWith("//") &&
                !line.startsWith("#")
        }
        .joinToString("")

    for (flags in intArrayOf(Base64.DEFAULT, Base64.URL_SAFE)) {
        try {
            val decoded = Base64.decode(
                nonCommentLines,
                flags
            )

            val decodedString = String(
                decoded,
                StandardCharsets.UTF_8
            ).trim()

            if (
                decodedString.contains("://") ||
                decodedString.startsWith("{") ||
                decodedString.startsWith("[")
            ) {
                return decodedString
            }
        } catch (_: Exception) {
        }
    }

    return trimmed
}

private fun cleanNodeName(rawTag: String?): String {
    if (rawTag.isNullOrBlank()) {
        return "Proxy"
    }

    val trimmed = rawTag.trim()

    if (!trimmed.contains("%")) {
        return trimmed
    }

    return try {
        URLDecoder.decode(
            trimmed,
            StandardCharsets.UTF_8.name()
        ).trim()
    } catch (_: Exception) {
        trimmed
    }
}

private fun parseUriLines(text: String): List<JSONObject> {
    val outbounds = mutableListOf<JSONObject>()

    for (rawLine in text.lines()) {
        val line = rawLine.trim()

        if (
            line.isEmpty() ||
            line.startsWith("#") ||
            line.startsWith("//")
        ) {
            continue
        }

        try {
            when {
                line.startsWith("vless://") -> {
                    parseVless(line)?.let {
                        outbounds.add(it)
                    }
                }

                line.startsWith("vmess://") -> {
                    parseVmess(line)?.let {
                        outbounds.add(it)
                    }
                }

                line.startsWith("trojan://") -> {
                    parseTrojan(line)?.let {
                        outbounds.add(it)
                    }
                }

                line.startsWith("ss://") -> {
                    parseShadowsocks(line)?.let {
                        outbounds.add(it)
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    return outbounds
}

private fun parseVless(line: String): JSONObject? {
    val rawTag = if (line.contains("#")) {
        line.substringAfter("#")
    } else {
        ""
    }

    val tag = cleanNodeName(rawTag)

    val withoutTag = line.substringBefore("#")

    val queryString = if (withoutTag.contains("?")) {
        withoutTag.substringAfter("?")
    } else {
        ""
    }

    val mainPart = withoutTag
        .substringBefore("?")
        .removePrefix("vless://")

    if (!mainPart.contains("@")) {
        return null
    }

    val uuid = mainPart
        .substringBefore("@")
        .trim()

    val hostPort = mainPart
        .substringAfter("@")
        .trim()

    if (uuid.isEmpty() || hostPort.isEmpty()) {
        return null
    }

    val server: String
    val port: Int

    if (
        hostPort.startsWith("[") &&
        hostPort.contains("]:")
    ) {
        server = hostPort.substringBefore("]:") + "]"
        port = hostPort
            .substringAfter("]:")
            .toIntOrNull()
            ?: 443
    } else if (
        hostPort.contains(":") &&
        !hostPort.startsWith("[")
    ) {
        server = hostPort.substringBeforeLast(":")
        port = hostPort
            .substringAfterLast(":")
            .toIntOrNull()
            ?: 443
    } else {
        server = hostPort
        port = 443
    }

    if (server.isEmpty()) {
        return null
    }

    val params = parseQueryParams(queryString)

    val outbound = JSONObject()

    outbound.put("type", "vless")
    outbound.put("tag", tag)
    outbound.put("server", server)
    outbound.put("server_port", port)
    outbound.put("uuid", uuid)

    params["flow"]?.let {
        outbound.put("flow", it)
    }

    outbound.put(
        "packet_encoding",
        params["packetEncoding"] ?: "xudp"
    )

    val security = params["security"] ?: "none"

    if (
        security == "tls" ||
        security == "reality"
    ) {
        val tlsObject = JSONObject()

        tlsObject.put("enabled", true)

        params["sni"]?.let {
            tlsObject.put("server_name", it)
        }

        val utlsObject = JSONObject()

        utlsObject.put("enabled", true)
        utlsObject.put(
            "fingerprint",
            params["fp"] ?: "chrome"
        )

        tlsObject.put("utls", utlsObject)

        if (security == "reality") {
            val realityObject = JSONObject()

            realityObject.put("enabled", true)

            params["pbk"]?.let {
                realityObject.put("public_key", it)
            }

            params["sid"]?.let {
                realityObject.put("short_id", it)
            }

            tlsObject.put(
                "reality",
                realityObject
            )
        }

        outbound.put("tls", tlsObject)
    }

    val transportType = params["type"] ?: "tcp"

    if (
        transportType == "ws" ||
        transportType == "grpc" ||
        transportType == "http"
    ) {
        val transportObject = JSONObject()

        transportObject.put(
            "type",
            transportType
        )

        when (transportType) {
            "ws" -> {
                params["path"]?.let {
                    transportObject.put(
                        "path",
                        URLDecoder.decode(
                            it,
                            StandardCharsets.UTF_8.name()
                        )
                    )
                }

                params["host"]?.let {
                    val headers = JSONObject()

                    headers.put(
                        "Host",
                        URLDecoder.decode(
                            it,
                            StandardCharsets.UTF_8.name()
                        )
                    )

                    transportObject.put(
                        "headers",
                        headers
                    )
                }
            }

            "grpc" -> {
                params["serviceName"]?.let {
                    transportObject.put(
                        "service_name",
                        URLDecoder.decode(
                            it,
                            StandardCharsets.UTF_8.name()
                        )
                    )
                }
            }
        }

        outbound.put(
            "transport",
            transportObject
        )
    }

    return outbound
}  
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
                        val value = inbound.remove("inet4_address")

                        if (value is JSONArray) {
                            for (j in 0 until value.length()) {
                                addresses.put(value.get(j))
                            }
                        } else {
                            addresses.put(value)
                        }
                    }

                    if (inbound.has("inet6_address")) {
                        val value = inbound.remove("inet6_address")

                        if (value is JSONArray) {
                            for (j in 0 until value.length()) {
                                addresses.put(value.get(j))
                            }
                        } else {
                            addresses.put(value)
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
                val outbound = outbounds.optJSONObject(i) ?: continue

                if (outbound.optString("type") != "dns") {
                    cleanedOutbounds.put(outbound)
                }
            }

            root.put("outbounds", cleanedOutbounds)
        }

        val route = root.optJSONObject("route")

        if (route != null) {
            if (!route.has("default_domain_resolver")) {
                route.put(
                    "default_domain_resolver",
                    "dns-direct"
                )
            }

            val rules = route.optJSONArray("rules")

            if (rules != null) {
                var hasSniff = false
                var hasHijackDns = false

                val newRules = JSONArray()

                for (i in 0 until rules.length()) {
                    val rule = rules.optJSONObject(i) ?: continue

                    if (rule.optString("action") == "sniff") {
                        hasSniff = true
                    }

                    val protocol = rule.optString("protocol")
                    val outbound = rule.optString("outbound")

                    if (
                        protocol == "dns" ||
                        outbound == "dns-out"
                    ) {
                        rule.remove("outbound")
                        rule.put(
                            "action",
                            "hijack-dns"
                        )

                        hasHijackDns = true
                    }

                    if (rule.optString("action") == "hijack-dns") {
                        hasHijackDns = true
                    }

                    newRules.put(rule)
                }

                if (!hasSniff) {
                    val rulesWithSniff = JSONArray()

                    rulesWithSniff.put(
                        JSONObject().apply {
                            put(
                                "action",
                                "sniff"
                            )
                        }
                    )

                    for (i in 0 until newRules.length()) {
                        rulesWithSniff.put(
                            newRules.get(i)
                        )
                    }

                    route.put(
                        "rules",
                        rulesWithSniff
                    )
                } else {
                    route.put(
                        "rules",
                        newRules
                    )
                }

                if (!hasHijackDns) {
                    val currentRules =
                        route.optJSONArray("rules") ?: JSONArray()

                    val rulesWithDns = JSONArray()

                    var inserted = false

                    for (i in 0 until currentRules.length()) {
                        val rule = currentRules.get(i)

                        rulesWithDns.put(rule)

                        if (!inserted) {
                            val ruleObj =
                                rule as? JSONObject

                            if (
                                ruleObj?.optString("action") ==
                                "sniff"
                            ) {
                                rulesWithDns.put(
                                    JSONObject().apply {
                                        put(
                                            "protocol",
                                            "dns"
                                        )
                                        put(
                                            "action",
                                            "hijack-dns"
                                        )
                                    }
                                )

                                inserted = true
                            }
                        }
                    }

                    if (!inserted) {
                        rulesWithDns.put(
                            0,
                            JSONObject().apply {
                                put(
                                    "protocol",
                                    "dns"
                                )
                                put(
                                    "action",
                                    "hijack-dns"
                                )
                            }
                        )
                    }

                    route.put(
                        "rules",
                        rulesWithDns
                    )
                }
            }
        }

        SubscriptionRouting.apply(root)

        root.toString(2)
    } catch (e: Exception) {
        jsonStr
    }
}

private fun tryDecodeBase64(text: String): String {
    val trimmed = text.trim()

    if (
        trimmed.contains("://") ||
        trimmed.startsWith("{") ||
        trimmed.startsWith("[")
    ) {
        return trimmed
    }

    val nonCommentLines = text
        .lines()
        .filter {
            val value = it.trim()

            value.isNotEmpty() &&
                !value.startsWith("//") &&
                !value.startsWith("#")
        }
        .joinToString("")

    for (
        flags in intArrayOf(
            Base64.DEFAULT,
            Base64.URL_SAFE
        )
    ) {
        try {
            val decoded = Base64.decode(
                nonCommentLines,
                flags
            )

            val decodedString =
                String(
                    decoded,
                    StandardCharsets.UTF_8
                ).trim()

            if (
                decodedString.contains("://") ||
                decodedString.startsWith("{") ||
                decodedString.startsWith("[")
            ) {
                return decodedString
            }
        } catch (_: Exception) {
        }
    }

    return trimmed
}

private fun cleanNodeName(
    rawTag: String?
): String {
    if (rawTag.isNullOrBlank()) {
        return "Proxy"
    }

    val trimmed = rawTag.trim()

    if (!trimmed.contains("%")) {
        return trimmed
    }

    return try {
        URLDecoder.decode(
            trimmed,
            StandardCharsets.UTF_8.name()
        ).trim()
    } catch (_: Exception) {
        trimmed
    }
}

private fun parseUriLines(
    text: String
): List<JSONObject> {
    val outbounds =
        mutableListOf<JSONObject>()

    for (rawLine in text.lines()) {
        val line = rawLine.trim()

        if (
            line.isEmpty() ||
            line.startsWith("#") ||
            line.startsWith("//")
        ) {
            continue
        }

        try {
            when {
                line.startsWith("vless://") -> {
                    parseVless(line)?.let {
                        outbounds.add(it)
                    }
                }

                line.startsWith("vmess://") -> {
                    parseVmess(line)?.let {
                        outbounds.add(it)
                    }
                }

                line.startsWith("trojan://") -> {
                    parseTrojan(line)?.let {
                        outbounds.add(it)
                    }
                }

                line.startsWith("ss://") -> {
                    parseShadowsocks(line)?.let {
                        outbounds.add(it)
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    return outbounds
}

private fun parseVless(
    line: String
): JSONObject? {
    val rawTag =
        if (line.contains("#")) {
            line.substringAfter("#")
        } else {
            ""
        }

    val tag = cleanNodeName(rawTag)

    val withoutTag = line.substringBefore("#")

    val queryString =
        if (withoutTag.contains("?")) {
            withoutTag.substringAfter("?")
        } else {
            ""
        }

    val mainPart =
        withoutTag
            .substringBefore("?")
            .removePrefix("vless://")

    if (!mainPart.contains("@")) {
        return null
    }

    val uuid =
        mainPart
            .substringBefore("@")
            .trim()

    val hostPort =
        mainPart
            .substringAfter("@")
            .trim()

    if (
        uuid.isEmpty() ||
        hostPort.isEmpty()
    ) {
        return null
    }

    val server: String
    val port: Int

    if (
        hostPort.startsWith("[") &&
        hostPort.contains("]:")
    ) {
        server =
            hostPort.substringBefore("]:") + "]"

        port =
            hostPort
                .substringAfter("]:")
                .toIntOrNull()
                ?: 443
    } else if (
        hostPort.contains(":") &&
        !hostPort.startsWith("[")
    ) {
        server =
            hostPort.substringBeforeLast(":")

        port =
            hostPort
                .substringAfterLast(":")
                .toIntOrNull()
                ?: 443
    } else {
        server = hostPort
        port = 443
    }

    if (server.isEmpty()) {
        return null
    }

    val params =
        parseQueryParams(queryString)

    val outbound = JSONObject()

    outbound.put("type", "vless")
    outbound.put("tag", tag)
    outbound.put("server", server)
    outbound.put("server_port", port)
    outbound.put("uuid", uuid)

    params["flow"]?.let {
        outbound.put(
            "flow",
            it
        )
    }

    outbound.put(
        "packet_encoding",
        params["packetEncoding"] ?: "xudp"
    )

    val security =
        params["security"] ?: "none"

    if (
        security == "tls" ||
        security == "reality"
    ) {
        val tlsObject = JSONObject()

        tlsObject.put(
            "enabled",
            true
        )

        params["sni"]?.let {
            tlsObject.put(
                "server_name",
                URLDecoder.decode(
                    it,
                    StandardCharsets.UTF_8.name()
                )
            )
        }

        val utlsObject = JSONObject()

        utlsObject.put(
            "enabled",
            true
        )

        utlsObject.put(
            "fingerprint",
            params["fp"] ?: "chrome"
        )

        tlsObject.put(
            "utls",
            utlsObject
        )

        if (security == "reality") {
            val realityObject =
                JSONObject()

            realityObject.put(
                "enabled",
                true
            )

            params["pbk"]?.let {
                realityObject.put(
                    "public_key",
                    it
                )
            }

            params["sid"]?.let {
                realityObject.put(
                    "short_id",
                    it
                )
            }

            tlsObject.put(
                "reality",
                realityObject
            )
        }

        outbound.put(
            "tls",
            tlsObject
        )
    }

    val transportType =
        params["type"] ?: "tcp"

    if (
        transportType == "ws" ||
        transportType == "grpc" ||
        transportType == "http"
    ) {
        val transportObject =
            JSONObject()

        transportObject.put(
            "type",
            transportType
        )

        when (transportType) {
            "ws" -> {
                params["path"]?.let {
                    transportObject.put(
                        "path",
                        URLDecoder.decode(
                            it,
                            StandardCharsets.UTF_8.name()
                        )
                    )
                }

                params["host"]?.let {
                    val headers =
                        JSONObject()

                    headers.put(
                        "Host",
                        URLDecoder.decode(
                            it,
                            StandardCharsets.UTF_8.name()
                        )
                    )

                    transportObject.put(
                        "headers",
                        headers
                    )
                }
            }

            "grpc" -> {
                params["serviceName"]?.let {
                    transportObject.put(
                        "service_name",
                        URLDecoder.decode(
                            it,
                            StandardCharsets.UTF_8.name()
                        )
                    )
                }
            }
        }

        outbound.put(
            "transport",
            transportObject
        )
    }

    return outbound
}
private fun parseVmess(
    line: String
): JSONObject? {
    val encoded =
        line
            .removePrefix("vmess://")
            .trim()

    val jsonString = try {
        String(
            Base64.decode(
                encoded,
                Base64.DEFAULT
            ),
            StandardCharsets.UTF_8
        )
    } catch (_: Exception) {
        String(
            Base64.decode(
                encoded,
                Base64.URL_SAFE
            ),
            StandardCharsets.UTF_8
        )
    }

    val vmessJson = JSONObject(jsonString)

    val server =
        vmessJson.optString("add")

    val port =
        vmessJson
            .optString("port")
            .toIntOrNull()
            ?: vmessJson.optInt(
                "port",
                443
            )

    val uuid =
        vmessJson.optString("id")

    val tag =
        cleanNodeName(
            vmessJson.optString("ps")
        )

    if (
        server.isEmpty() ||
        uuid.isEmpty()
    ) {
        return null
    }

    val outbound = JSONObject()

    outbound.put("type", "vmess")
    outbound.put("tag", tag)
    outbound.put("server", server)
    outbound.put("server_port", port)
    outbound.put("uuid", uuid)
    outbound.put(
        "alter_id",
        vmessJson.optInt("aid", 0)
    )
    outbound.put(
        "security",
        vmessJson.optString(
            "scy",
            "auto"
        )
    )

    if (
        vmessJson
            .optString("tls")
            .equals(
                "tls",
                ignoreCase = true
            )
    ) {
        val tlsObject = JSONObject()

        tlsObject.put(
            "enabled",
            true
        )

        val sni =
            vmessJson
                .optString("sni")
                .ifEmpty {
                    vmessJson.optString("host")
                }

        if (sni.isNotEmpty()) {
            tlsObject.put(
                "server_name",
                sni
            )
        }

        outbound.put(
            "tls",
            tlsObject
        )
    }

    val network =
        vmessJson.optString(
            "net",
            "tcp"
        )

    if (
        network == "ws" ||
        network == "grpc"
    ) {
        val transportObject =
            JSONObject()

        transportObject.put(
            "type",
            network
        )

        val path =
            vmessJson.optString("path")

        when (network) {
            "ws" -> {
                if (path.isNotEmpty()) {
                    transportObject.put(
                        "path",
                        path
                    )
                }

                val host =
                    vmessJson.optString("host")

                if (host.isNotEmpty()) {
                    val headers =
                        JSONObject()

                    headers.put(
                        "Host",
                        host
                    )

                    transportObject.put(
                        "headers",
                        headers
                    )
                }
            }

            "grpc" -> {
                if (path.isNotEmpty()) {
                    transportObject.put(
                        "service_name",
                        path
                    )
                }
            }
        }

        outbound.put(
            "transport",
            transportObject
        )
    }

    return outbound
}

private fun parseTrojan(
    line: String
): JSONObject? {
    val rawTag =
        if (line.contains("#")) {
            line.substringAfter("#")
        } else {
            ""
        }

    val tag =
        cleanNodeName(rawTag)

    val withoutTag =
        line.substringBefore("#")

    val queryString =
        if (withoutTag.contains("?")) {
            withoutTag.substringAfter("?")
        } else {
            ""
        }

    val mainPart =
        withoutTag
            .substringBefore("?")
            .removePrefix("trojan://")

    if (!mainPart.contains("@")) {
        return null
    }

    val password =
        mainPart
            .substringBefore("@")
            .trim()

    val hostPort =
        mainPart
            .substringAfter("@")
            .trim()

    if (
        password.isEmpty() ||
        hostPort.isEmpty()
    ) {
        return null
    }

    val server: String
    val port: Int

    if (
        hostPort.startsWith("[") &&
        hostPort.contains("]:")
    ) {
        server =
            hostPort.substringBefore("]:") + "]"

        port =
            hostPort
                .substringAfter("]:")
                .toIntOrNull()
                ?: 443
    } else if (
        hostPort.contains(":") &&
        !hostPort.startsWith("[")
    ) {
        server =
            hostPort.substringBeforeLast(":")

        port =
            hostPort
                .substringAfterLast(":")
                .toIntOrNull()
                ?: 443
    } else {
        server = hostPort
        port = 443
    }

    if (server.isEmpty()) {
        return null
    }

    val params =
        parseQueryParams(queryString)

    val outbound = JSONObject()

    outbound.put("type", "trojan")
    outbound.put("tag", tag)
    outbound.put("server", server)
    outbound.put("server_port", port)
    outbound.put("password", password)

    val tlsObject =
        JSONObject()

    tlsObject.put(
        "enabled",
        true
    )

    tlsObject.put(
        "server_name",
        params["sni"]
            ?: params["peer"]
            ?: server
    )

    params["fp"]?.let {
        val utlsObject =
            JSONObject()

        utlsObject.put(
            "enabled",
            true
        )

        utlsObject.put(
            "fingerprint",
            it
        )

        tlsObject.put(
            "utls",
            utlsObject
        )
    }

    outbound.put(
        "tls",
        tlsObject
    )

    val transportType =
        params["type"] ?: "tcp"

    if (
        transportType == "ws" ||
        transportType == "grpc"
    ) {
        val transportObject =
            JSONObject()

        transportObject.put(
            "type",
            transportType
        )

        when (transportType) {
            "ws" -> {
                params["path"]?.let {
                    transportObject.put(
                        "path",
                        URLDecoder.decode(
                            it,
                            StandardCharsets.UTF_8.name()
                        )
                    )
                }

                params["host"]?.let {
                    val headers =
                        JSONObject()

                    headers.put(
                        "Host",
                        URLDecoder.decode(
                            it,
                            StandardCharsets.UTF_8.name()
                        )
                    )

                    transportObject.put(
                        "headers",
                        headers
                    )
                }
            }

            "grpc" -> {
                params["serviceName"]?.let {
                    transportObject.put(
                        "service_name",
                        URLDecoder.decode(
                            it,
                            StandardCharsets.UTF_8.name()
                        )
                    )
                }
            }
        }

        outbound.put(
            "transport",
            transportObject
        )
    }

    return outbound
}

private fun parseShadowsocks(
    line: String
): JSONObject? {
    val rawTag =
        if (line.contains("#")) {
            line.substringAfter("#")
        } else {
            ""
        }

    val tag =
        cleanNodeName(rawTag)

    val withoutTag =
        line
            .substringBefore("#")
            .removePrefix("ss://")
            .trim()

    var method = ""
    var password = ""
    var server = ""
    var port = 8388

    if (withoutTag.contains("@")) {
        val userPart =
            withoutTag.substringBefore("@")

        val hostPort =
            withoutTag.substringAfter("@")

        val decodedUserInfo = try {
            String(
                Base64.decode(
                    userPart,
                    Base64.DEFAULT
                ),
                StandardCharsets.UTF_8
            )
        } catch (_: Exception) {
            try {
                String(
                    Base64.decode(
                        userPart,
                        Base64.URL_SAFE
                    ),
                    StandardCharsets.UTF_8
                )
            } catch (_: Exception) {
                userPart
            }
        }

        val credentials =
            decodedUserInfo.split(
                ":",
                limit = 2
            )

        if (credentials.size == 2) {
            method = credentials[0]
            password = credentials[1]
        }

        if (
            hostPort.startsWith("[") &&
            hostPort.contains("]:")
        ) {
            server =
                hostPort.substringBefore("]:") + "]"

            port =
                hostPort
                    .substringAfter("]:")
                    .substringBefore("?")
                    .toIntOrNull()
                    ?: 8388
        } else if (hostPort.contains(":")) {
            server =
                hostPort.substringBeforeLast(":")

            port =
                hostPort
                    .substringAfterLast(":")
                    .substringBefore("?")
                    .toIntOrNull()
                    ?: 8388
        } else {
            server =
                hostPort.substringBefore("?")
        }
    } else {
        val decoded = try {
            String(
                Base64.decode(
                    withoutTag,
                    Base64.DEFAULT
                ),
                StandardCharsets.UTF_8
            )
        } catch (_: Exception) {
            try {
                String(
                    Base64.decode(
                        withoutTag,
                        Base64.URL_SAFE
                    ),
                    StandardCharsets.UTF_8
                )
            } catch (_: Exception) {
                ""
            }
        }

        val atSplit =
            decoded.split(
                "@",
                limit = 2
            )

        if (atSplit.size == 2) {
            val credentials =
                atSplit[0].split(
                    ":",
                    limit = 2
                )

            method =
                credentials.getOrElse(0) {
                    ""
                }

            password =
                credentials.getOrElse(1) {
                    ""
                }

            val hostPort =
                atSplit[1]

            if (
                hostPort.startsWith("[") &&
                hostPort.contains("]:")
            ) {
                server =
                    hostPort.substringBefore("]:") + "]"

                port =
                    hostPort
                        .substringAfter("]:")
                        .toIntOrNull()
                        ?: 8388
            } else if (hostPort.contains(":")) {
                server =
                    hostPort.substringBeforeLast(":")

                port =
                    hostPort
                        .substringAfterLast(":")
                        .toIntOrNull()
                        ?: 8388
            } else {
                server = hostPort
            }
        }
    }

    if (
        server.isEmpty() ||
        method.isEmpty()
    ) {
        return null
    }

    val outbound = JSONObject()

    outbound.put("type", "shadowsocks")
    outbound.put("tag", tag)
    outbound.put("server", server)
    outbound.put("server_port", port)
    outbound.put("method", method)
    outbound.put("password", password)

    return outbound
}

private fun parseQueryParams(
    query: String?
): Map<String, String> {
    if (query.isNullOrEmpty()) {
        return emptyMap()
    }

    val result =
        mutableMapOf<String, String>()

    for (pair in query.split("&")) {
        val index =
            pair.indexOf("=")

        if (index > 0) {
            val key =
                pair.substring(
                    0,
                    index
                )

            val value =
                pair.substring(
                    index + 1
                )

            result[key] = value
        }
    }

    return result
}

private fun buildSingBoxConfig(
    nodes: List<JSONObject>
): String {
    val validNodes =
        nodes.filter {
            it.optString("type") != "dns"
        }

    val root = JSONObject()

    root.put(
        "log",
        JSONObject().apply {
            put("level", "warn")
            put("timestamp", true)
        }
    )

    root.put(
        "inbounds",
        JSONArray().apply {
            put(
                JSONObject().apply {
                    put("type", "tun")
                    put("tag", "tun-in")
                    put(
                        "address",
                        JSONArray().apply {
                            put("172.19.0.1/30")
                        }
                    )
                    put("auto_route", true)
                    put("strict_route", false)
                    put("stack", "gvisor")
                }
            )
        }
    )

    val outbounds =
        JSONArray()

    for (node in validNodes) {
        outbounds.put(node)
    }

    root.put(
        "outbounds",
        outbounds
    )

    root.put(
        "route",
        JSONObject().apply {
            put(
                "rules",
                JSONArray().apply {
                    put(
                        JSONObject().apply {
                            put("action", "sniff")
                        }
                    )

                    put(
                        JSONObject().apply {
                            put("protocol", "dns")
                            put(
                                "action",
                                "hijack-dns"
                            )
                        }
                    )
                }
            )

            put(
                "auto_detect_interface",
                true
            )
        }
    )

    return SubscriptionRouting
        .apply(root)
        .toString(2)
}

override fun close() {
    client.close()
}

companion object {
    const val userAgent = "SFAxtnd"
}
}
