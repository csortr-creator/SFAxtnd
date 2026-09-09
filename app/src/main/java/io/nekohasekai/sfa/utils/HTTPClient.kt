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
import java.util.Locale
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
        } catch (_: Exception) {
            normalizedUrl.hashCode().toString()
        }

        val memoryHwid = hwidMemoryCache[urlKey]

        if (!memoryHwid.isNullOrBlank()) {
            return memoryHwid
        }

        val context = getApplicationContext()

        if (context != null) {
            val prefs = context.getSharedPreferences(
                "subscription_hwid_store",
                Context.MODE_PRIVATE
            )

            val savedHwid = prefs.getString(
                urlKey,
                null
            )

            if (!savedHwid.isNullOrBlank()) {
                hwidMemoryCache[urlKey] = savedHwid
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

            hwidMemoryCache[urlKey] = newHwid

            return newHwid
        }

        val fallbackHwid = urlKey.take(16)

        hwidMemoryCache[urlKey] = fallbackHwid

        return fallbackHwid
    }

    private fun getApplicationContext(): Context? {
        return try {
            val appClass = Class.forName(
                "io.nekohasekai.sfa.Application"
            )

            val field = appClass.getDeclaredField(
                "application"
            )

            field.isAccessible = true

            field.get(null) as? Context
        } catch (_: Exception) {
            try {
                val activityThreadClass = Class.forName(
                    "android.app.ActivityThread"
                )

                val currentAppMethod =
                    activityThreadClass.getMethod(
                        "currentApplication"
                    )

                currentAppMethod.invoke(
                    null
                ) as? Context
            } catch (_: Exception) {
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
            return sanitizeAndMigrateConfig(
                trimmed
            )
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
            } catch (_: Exception) {
            }
        }

        val nodes =
            parseUriLines(contentToParse)

        if (nodes.isNotEmpty()) {
            return buildSingBoxConfig(
                nodes
            )
        }

        return sanitizeAndMigrateConfig(
            trimmed
        )
    }

    private fun sanitizeAndMigrateConfig(
        jsonStr: String
    ): String {
        return try {
            val fixedRaw =
                jsonStr
                    .replace(
                        "https://raw.githubusercontent.com/SagerNet/sing-geosite/rule-set/geosite-ru.srs",
                        "https://raw.githubusercontent.com/SagerNet/sing-geosite/rule-set/geosite-category-ru.srs"
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
        } catch (_: Exception) {
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

            if (
                server.optString("type") ==
                "rcode"
            ) {
                continue
            }

            migrateDnsServer(server)

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
        server.remove("strategy")
        server.remove("address_strategy")

        if (
            server.optString("detour") ==
            "direct"
        ) {
            server.remove("detour")
        }

        if (
            !server.has("address") ||
            server.has("type")
        ) {
            if (server.has("address_resolver")) {
                val resolver =
                    server.remove(
                        "address_resolver"
                    )

                if (
                    !server.has(
                        "domain_resolver"
                    )
                ) {
                    server.put(
                        "domain_resolver",
                        resolver
                    )
                }
            }

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
                        cleanAddress.substringBefore("/")

                    val host =
                        hostPort.substringBefore(":")

                    val port =
                        hostPort
                            .substringAfter(
                                ":",
                                ""
                            )
                            .toIntOrNull()

                    val path =
                        if (
                            cleanAddress.contains("/")
                        ) {
                            "/" +
                                cleanAddress.substringAfter("/")
                        } else {
                            "/dns-query"
                        }

                    server.put(
                        "type",
                        "https"
                    )

                    server.put(
                        "server",
                        host
                    )

                    if (port != null) {
                        server.put(
                            "server_port",
                            port
                        )
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
                        cleanAddress
                            .substringBefore(":")
                    )

                    cleanAddress
                        .substringAfter(
                            ":",
                            ""
                        )
                        .toIntOrNull()
                        ?.let {
                            server.put(
                                "server_port",
                                it
                            )
                        }
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
                        cleanAddress
                            .substringBefore(":")
                    )

                    cleanAddress
                        .substringAfter(
                            ":",
                            ""
                        )
                        .toIntOrNull()
                        ?.let {
                            server.put(
                                "server_port",
                                it
                            )
                        }
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
                        cleanAddress
                            .substringBefore(":")
                    )

                    cleanAddress
                        .substringAfter(
                            ":",
                            ""
                        )
                        .toIntOrNull()
                        ?.let {
                            server.put(
                                "server_port",
                                it
                            )
                        }
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
        } catch (_: Exception) {
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

        if (server.has("address_resolver")) {
            val resolver =
                server.remove(
                    "address_resolver"
                )

            if (
                !server.has(
                    "domain_resolver"
                )
            ) {
                server.put(
                    "domain_resolver",
                    resolver
                )
            }
        }
    }
    private fun migrateInbounds(
        root: JSONObject
    ) {
        val inbounds =
            root.optJSONArray("inbounds")
                ?: return

        for (
            i in 0 until inbounds.length()
        ) {
            val inbound =
                inbounds.optJSONObject(i)
                    ?: continue

            if (
                inbound.optString("type") !=
                "tun"
            ) {
                continue
            }

            val addresses = JSONArray()

            if (
                inbound.has("inet4_address")
            ) {
                val value =
                    inbound.remove(
                        "inet4_address"
                    )

                when (value) {
                    is JSONArray -> {
                        for (
                            j in 0 until
                                value.length()
                        ) {
                            addresses.put(
                                value.get(j)
                            )
                        }
                    }

                    null -> Unit

                    else -> {
                        addresses.put(value)
                    }
                }
            }

            if (
                inbound.has("inet6_address")
            ) {
                val value =
                    inbound.remove(
                        "inet6_address"
                    )

                when (value) {
                    is JSONArray -> {
                        for (
                            j in 0 until
                                value.length()
                        ) {
                            addresses.put(
                                value.get(j)
                            )
                        }
                    }

                    null -> Unit

                    else -> {
                        addresses.put(value)
                    }
                }
            }

            if (
                addresses.length() > 0 &&
                !inbound.has("address")
            ) {
                inbound.put(
                    "address",
                    addresses
                )
            }

            inbound.remove("sniff")
        }
    }

    private fun migrateOutbounds(
        root: JSONObject
    ) {
        val outbounds =
            root.optJSONArray("outbounds")
                ?: return

        val cleanedOutbounds =
            JSONArray()

        for (
            i in 0 until
                outbounds.length()
        ) {
            val outbound =
                outbounds.optJSONObject(i)
                    ?: continue

            if (
                outbound.optString("type") ==
                "dns"
            ) {
                continue
            }

            cleanedOutbounds.put(
                outbound
            )
        }

        root.put(
            "outbounds",
            cleanedOutbounds
        )
    }

    private fun migrateRoute(
        root: JSONObject
    ) {
        val route =
            root.optJSONObject("route")
                ?: return

        if (
            !route.has(
                "default_domain_resolver"
            )
        ) {
            route.put(
                "default_domain_resolver",
                "dns-direct"
            )
        }

        val oldRules =
            route.optJSONArray("rules")
                ?: JSONArray()

        val migratedRules =
            JSONArray()

        var hasSniff = false
        var hasHijackDns = false

        for (
            i in 0 until
                oldRules.length()
        ) {
            val rule =
                oldRules.optJSONObject(i)
                    ?: continue

            if (
                rule.optString("action") ==
                "sniff"
            ) {
                hasSniff = true
            }

            if (
                rule.optString("action") ==
                "hijack-dns"
            ) {
                hasHijackDns = true
            }

            val protocol =
                rule.optString("protocol")

            val outbound =
                rule.optString("outbound")

            if (
                protocol == "dns" ||
                outbound == "dns-out"
            ) {
                rule.remove(
                    "outbound"
                )

                rule.put(
                    "action",
                    "hijack-dns"
                )

                hasHijackDns = true
            }

            migratedRules.put(rule)
        }

        val finalRules = JSONArray()

        if (!hasSniff) {
            finalRules.put(
                JSONObject().apply {
                    put(
                        "action",
                        "sniff"
                    )
                }
            )
        }

        if (!hasHijackDns) {
            finalRules.put(
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

        for (
            i in 0 until
                migratedRules.length()
        ) {
            finalRules.put(
                migratedRules.get(i)
            )
        }

        route.put(
            "rules",
            finalRules
        )
    }

    private fun tryDecodeBase64(
        text: String
    ): String {
        val trimmed = text.trim()

        if (
            trimmed.contains("://") ||
            trimmed.startsWith("{") ||
            trimmed.startsWith("[")
        ) {
            return trimmed
        }

        val nonCommentLines =
            text
                .lines()
                .filter {
                    val line =
                        it.trim()

                    line.isNotEmpty() &&
                        !line.startsWith("#") &&
                        !line.startsWith("//")
                }
                .joinToString("")

        for (
            flags in intArrayOf(
                Base64.DEFAULT,
                Base64.URL_SAFE
            )
        ) {
            try {
                val decoded =
                    Base64.decode(
                        nonCommentLines,
                        flags
                    )

                val decodedStr =
                    String(
                        decoded,
                        StandardCharsets.UTF_8
                    ).trim()

                if (
                    decodedStr.contains("://") ||
                    decodedStr.startsWith("{") ||
                    decodedStr.startsWith("[")
                ) {
                    return decodedStr
                }
            } catch (_: Exception) {
            }
        }

        return trimmed
    }

    private fun cleanNodeName(
        rawTag: String?
    ): String {
        if (
            rawTag.isNullOrBlank()
        ) {
            return "Proxy"
        }

        val trimmed =
            rawTag.trim()

        if (
            !trimmed.contains("%")
        ) {
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

        for (
            rawLine in text.lines()
        ) {
            val line =
                rawLine.trim()

            if (
                line.isEmpty() ||
                line.startsWith("#") ||
                line.startsWith("//")
            ) {
                continue
            }

            try {
                when {
                    line.startsWith(
                        "vless://",
                        ignoreCase = true
                    ) -> {
                        parseVless(line)
                            ?.let {
                                outbounds.add(it)
                            }
                    }

                    line.startsWith(
                        "vmess://",
                        ignoreCase = true
                    ) -> {
                        parseVmess(line)
                            ?.let {
                                outbounds.add(it)
                            }
                    }

                    line.startsWith(
                        "trojan://",
                        ignoreCase = true
                    ) -> {
                        parseTrojan(line)
                            ?.let {
                                outbounds.add(it)
                            }
                    }

                    line.startsWith(
                        "ss://",
                        ignoreCase = true
                    ) -> {
                        parseShadowsocks(line)
                            ?.let {
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

        val tag =
            cleanNodeName(rawTag)

        val withoutTag =
            line.substringBefore("#")

        val queryStr =
            if (
                withoutTag.contains("?")
            ) {
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
                hostPort.substringBefore("]:") +
                    "]"

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
            parseQueryParams(queryStr)

        val outbound = JSONObject()

        outbound.put(
            "type",
            "vless"
        )

        outbound.put(
            "tag",
            tag
        )

        outbound.put(
            "server",
            server
        )

        outbound.put(
            "server_port",
            port
        )

        outbound.put(
            "uuid",
            uuid
        )

        params["flow"]?.let {
            outbound.put(
                "flow",
                it
            )
        }

        outbound.put(
            "packet_encoding",
            params["packetEncoding"]
                ?: "xudp"
        )

        val security =
            params["security"]
                ?: "none"

        if (
            security == "tls" ||
            security == "reality"
        ) {
            val tlsObj = JSONObject()

            tlsObj.put(
                "enabled",
                true
            )

            params["sni"]?.let {
                tlsObj.put(
                    "server_name",
                    it
                )
            }

            val fingerprint =
                params["fp"]
                    ?: "chrome"

            val utlsObj =
                JSONObject().apply {
                    put(
                        "enabled",
                        true
                    )

                    put(
                        "fingerprint",
                        fingerprint
                    )
                }

            tlsObj.put(
                "utls",
                utlsObj
            )

            if (
                security == "reality"
            ) {
                val realityObj =
                    JSONObject()

                realityObj.put(
                    "enabled",
                    true
                )

                params["pbk"]?.let {
                    realityObj.put(
                        "public_key",
                        it
                    )
                }

                params["sid"]?.let {
                    realityObj.put(
                        "short_id",
                        it
                    )
                }

                tlsObj.put(
                    "reality",
                    realityObj
                )
            }

            outbound.put(
                "tls",
                tlsObj
            )
        }

        val transportType =
            params["type"]
                ?: "tcp"

        if (
            transportType == "ws" ||
            transportType == "grpc" ||
            transportType == "http"
        ) {
            val transportObj =
                JSONObject()

            transportObj.put(
                "type",
                transportType
            )

            when (transportType) {
                "ws" -> {
                    params["path"]?.let {
                        transportObj.put(
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

                        transportObj.put(
                            "headers",
                            headers
                        )
                    }
                }

                "grpc" -> {
                    params["serviceName"]?.let {
                        transportObj.put(
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
                transportObj
            )
        }

        return outbound
    }
        private fun parseVmess(
        line: String
    ): JSONObject? {
        val b64 =
            line
                .removePrefix("vmess://")
                .trim()

        val jsonStr = try {
            String(
                Base64.decode(
                    b64,
                    Base64.DEFAULT
                ),
                StandardCharsets.UTF_8
            )
        } catch (_: Exception) {
            try {
                String(
                    Base64.decode(
                        b64,
                        Base64.URL_SAFE
                    ),
                    StandardCharsets.UTF_8
                )
            } catch (_: Exception) {
                return null
            }
        }

        val vmessJson =
            JSONObject(jsonStr)

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

        if (
            server.isBlank() ||
            uuid.isBlank()
        ) {
            return null
        }

        val tag =
            cleanNodeName(
                vmessJson.optString("ps")
            )

        val outbound =
            JSONObject()

        outbound.put(
            "type",
            "vmess"
        )

        outbound.put(
            "tag",
            tag
        )

        outbound.put(
            "server",
            server
        )

        outbound.put(
            "server_port",
            port
        )

        outbound.put(
            "uuid",
            uuid
        )

        outbound.put(
            "alter_id",
            vmessJson.optInt(
                "aid",
                0
            )
        )

        outbound.put(
            "security",
            vmessJson.optString(
                "scy",
                "auto"
            )
        )

        val tlsEnabled =
            vmessJson
                .optString("tls")
                .equals(
                    "tls",
                    ignoreCase = true
                )

        if (tlsEnabled) {
            val tlsObj =
                JSONObject()

            tlsObj.put(
                "enabled",
                true
            )

            val sni =
                vmessJson
                    .optString("sni")
                    .ifEmpty {
                        vmessJson.optString(
                            "host"
                        )
                    }

            if (sni.isNotBlank()) {
                tlsObj.put(
                    "server_name",
                    sni
                )
            }

            outbound.put(
                "tls",
                tlsObj
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
            val transportObj =
                JSONObject()

            transportObj.put(
                "type",
                network
            )

            val path =
                vmessJson.optString("path")

            when (network) {
                "ws" -> {
                    if (path.isNotBlank()) {
                        transportObj.put(
                            "path",
                            path
                        )
                    }

                    val host =
                        vmessJson.optString(
                            "host"
                        )

                    if (host.isNotBlank()) {
                        val headers =
                            JSONObject()

                        headers.put(
                            "Host",
                            host
                        )

                        transportObj.put(
                            "headers",
                            headers
                        )
                    }
                }

                "grpc" -> {
                    val serviceName =
                        vmessJson
                            .optString(
                                "serviceName"
                            )
                            .ifEmpty {
                                path
                            }

                    if (
                        serviceName.isNotBlank()
                    ) {
                        transportObj.put(
                            "service_name",
                            serviceName
                        )
                    }
                }
            }

            outbound.put(
                "transport",
                transportObj
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

        val queryStr =
            if (
                withoutTag.contains("?")
            ) {
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
                hostPort.substringBefore("]:") +
                    "]"

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
            parseQueryParams(queryStr)

        val outbound =
            JSONObject()

        outbound.put(
            "type",
            "trojan"
        )

        outbound.put(
            "tag",
            tag
        )

        outbound.put(
            "server",
            server
        )

        outbound.put(
            "server_port",
            port
        )

        outbound.put(
            "password",
            password
        )

        val tlsObj =
            JSONObject()

        tlsObj.put(
            "enabled",
            true
        )

        tlsObj.put(
            "server_name",
            params["sni"]
                ?: params["peer"]
                ?: server
        )

        val fingerprint =
            params["fp"]

        if (!fingerprint.isNullOrBlank()) {
            tlsObj.put(
                "utls",
                JSONObject().apply {
                    put(
                        "enabled",
                        true
                    )

                    put(
                        "fingerprint",
                        fingerprint
                    )
                }
            )
        }

        outbound.put(
            "tls",
            tlsObj
        )

        val transportType =
            params["type"]
                ?: "tcp"

        if (
            transportType == "ws" ||
            transportType == "grpc"
        ) {
            val transportObj =
                JSONObject()

            transportObj.put(
                "type",
                transportType
            )

            when (transportType) {
                "ws" -> {
                    params["path"]?.let {
                        transportObj.put(
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

                        transportObj.put(
                            "headers",
                            headers
                        )
                    }
                }

                "grpc" -> {
                    params["serviceName"]?.let {
                        transportObj.put(
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
                transportObj
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

            val parts =
                decodedUserInfo.split(
                    ":",
                    limit = 2
                )

            if (parts.size == 2) {
                method = parts[0]
                password = parts[1]
            }

            if (
                hostPort.startsWith("[") &&
                hostPort.contains("]:")
            ) {
                server =
                    hostPort.substringBefore("]:") +
                        "]"

                port =
                    hostPort
                        .substringAfter("]:")
                        .substringBefore("?")
                        .toIntOrNull()
                        ?: 8388
            } else if (
                hostPort.contains(":")
            ) {
                server =
                    hostPort
                        .substringBeforeLast(":")
                        .trim()

                port =
                    hostPort
                        .substringAfterLast(":")
                        .substringBefore("?")
                        .toIntOrNull()
                        ?: 8388
            } else {
                server =
                    hostPort
                        .substringBefore("?")
                        .trim()
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
                        hostPort
                            .substringBefore("]:") +
                            "]"

                    port =
                        hostPort
                            .substringAfter("]:")
                            .toIntOrNull()
                            ?: 8388
                } else if (
                    hostPort.contains(":")
                ) {
                    server =
                        hostPort
                            .substringBeforeLast(":")
                            .trim()

                    port =
                        hostPort
                            .substringAfterLast(":")
                            .toIntOrNull()
                            ?: 8388
                } else {
                    server =
                        hostPort.trim()
                }
            }
        }

        if (
            server.isEmpty() ||
            method.isEmpty()
        ) {
            return null
        }

        return JSONObject().apply {
            put(
                "type",
                "shadowsocks"
            )

            put(
                "tag",
                tag
            )

            put(
                "server",
                server
            )

            put(
                "server_port",
                port
            )

            put(
                "method",
                method
            )

            put(
                "password",
                password
            )
        }
    }

    private fun parseQueryParams(
        query: String?
    ): Map<String, String> {
        if (query.isNullOrEmpty()) {
            return emptyMap()
        }

        val result =
            mutableMapOf<String, String>()

        for (
            pair in query.split("&")
        ) {
            val index =
                pair.indexOf("=")

            if (index <= 0) {
                continue
            }

            val key =
                pair.substring(
                    0,
                    index
                )

            val value =
                pair.substring(
                    index + 1
                )

            result[key] = try {
                URLDecoder.decode(
                    value,
                    StandardCharsets.UTF_8.name()
                )
            } catch (_: Exception) {
                value
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

        val usedTags =
            mutableMapOf<String, Int>()

        usedTags["Выбор сервера"] = 1
        usedTags["direct"] = 1
        usedTags["block"] = 1

        val proxyTags =
            mutableListOf<String>()

        for (node in validNodes) {
            val rawTag =
                node.optString("tag")
                    .ifEmpty {
                        node.optString("server")
                            .ifEmpty {
                                "Proxy"
                            }
                    }

            val cleanTag =
                cleanNodeName(rawTag)

            val count =
                usedTags.getOrDefault(
                    cleanTag,
                    0
                )

            val uniqueTag =
                if (count > 0) {
                    "$cleanTag ($count)"
                } else {
                    cleanTag
                }

            usedTags[cleanTag] =
                count + 1

            node.put(
                "tag",
                uniqueTag
            )

            val type =
                node.optString("type")

            if (
                type !in listOf(
                    "selector",
                    "urltest",
                    "direct",
                    "block",
                    "dns"
                )
            ) {
                proxyTags.add(
                    uniqueTag
                )
            }
        }

        if (proxyTags.isEmpty()) {
            for (node in validNodes) {
                val tag =
                    node.optString("tag")

                if (tag.isNotBlank()) {
                    proxyTags.add(tag)
                }
            }
        }

        val root =
            JSONObject()

        root.put(
            "log",
            JSONObject().apply {
                put(
                    "level",
                    "warn"
                )

                put(
                    "timestamp",
                    true
                )
            }
        )

        val dnsServers =
            JSONArray()

        dnsServers.put(
            JSONObject().apply {
                put(
                    "tag",
                    "dns-remote"
                )

                put(
                    "type",
                    "https"
                )

                put(
                    "server",
                    "1.1.1.1"
                )

                put(
                    "path",
                    "/dns-query"
                )

                put(
                    "domain_resolver",
                    "dns-direct"
                )

                put(
                    "detour",
                    "Выбор сервера"
                )
            }
        )

        dnsServers.put(
            JSONObject().apply {
                put(
                    "tag",
                    "dns-direct"
                )

                put(
                    "type",
                    "udp"
                )

                put(
                    "server",
                    "77.88.8.8"
                )

                put(
                    "server_port",
                    53
                )
            }
        )

        val dnsObj =
            JSONObject()

        dnsObj.put(
            "servers",
            dnsServers
        )

        dnsObj.put(
            "rules",
            JSONArray().apply {
                put(
                    JSONObject().apply {
                        put(
                            "rule_set",
                            JSONArray().apply {
                                put(
                                    "geosite-category-ru"
                                )
                            }
                        )

                        put(
                            "server",
                            "dns-direct"
                        )
                    }
                )

                put(
                    JSONObject().apply {
                        put(
                            "domain_suffix",
                            JSONArray().apply {
                                put(".ru")
                                put(".su")
                                put(".xn--p1ai")
                                put(".by")
                                put(".kz")
                            }
                        )

                        put(
                            "server",
                            "dns-direct"
                        )
                    }
                )
            }
        )

        dnsObj.put(
            "final",
            "dns-remote"
        )

        dnsObj.put(
            "strategy",
            "ipv4_only"
        )

        root.put(
            "dns",
            dnsObj
        )

        root.put(
            "inbounds",
            JSONArray().apply {
                put(
                    JSONObject().apply {
                        put(
                            "type",
                            "tun"
                        )

                        put(
                            "tag",
                            "tun-in"
                        )

                        put(
                            "interface_name",
                            "tun0"
                        )

                        put(
                            "address",
                            JSONArray().apply {
                                put(
                                    "172.19.0.1/30"
                                )
                            }
                        )

                        put(
                            "auto_route",
                            true
                        )

                        put(
                            "strict_route",
                            false
                        )

                        put(
                            "stack",
                            "gvisor"
                        )
                    }
                )
            }
        )

        val outboundsArr =
            JSONArray()

        val hasProxySelector =
            validNodes.any {
                it.optString("tag") ==
                    "Выбор сервера"
            }

        if (!hasProxySelector) {
            val selector =
                JSONObject().apply {
                    put(
                        "type",
                        "selector"
                    )

                    put(
                        "tag",
                        "Выбор сервера"
                    )

                    val selectorOutbounds =
                        JSONArray()

                    for (proxyTag in proxyTags) {
                        selectorOutbounds.put(
                            proxyTag
                        )
                    }

                    selectorOutbounds.put(
                        "direct"
                    )

                    put(
                        "outbounds",
                        selectorOutbounds
                    )

                    if (
                        proxyTags.isNotEmpty()
                    ) {
                        put(
                            "default",
                            proxyTags.first()
                        )
                    }
                }

            outboundsArr.put(selector)
        }

        for (node in validNodes) {
            outboundsArr.put(node)
        }

        if (
            validNodes.none {
                it.optString("tag") ==
                    "direct"
            }
        ) {
            outboundsArr.put(
                JSONObject().apply {
                    put(
                        "type",
                        "direct"
                    )

                    put(
                        "tag",
                        "direct"
                    )
                }
            )
        }

        if (
            validNodes.none {
                it.optString("tag") ==
                    "block"
            }
        ) {
            outboundsArr.put(
                JSONObject().apply {
                    put(
                        "type",
                        "block"
                    )

                    put(
                        "tag",
                        "block"
                    )
                }
            )
        }

        root.put(
            "outbounds",
            outboundsArr
        )

        val route =
            JSONObject()

        route.put(
            "default_domain_resolver",
            "dns-direct"
        )

        route.put(
            "rule_set",
            JSONArray().apply {
                put(
                    JSONObject().apply {
                        put(
                            "tag",
                            "geosite-category-ru"
                        )

                        put(
                            "type",
                            "remote"
                        )

                        put(
                            "format",
                            "binary"
                        )

                        put(
                            "url",
                            "https://raw.githubusercontent.com/" +
                                "SagerNet/sing-geosite/" +
                                "rule-set/" +
                                "geosite-category-ru.srs"
                        )

                        put(
                            "download_detour",
                            "Выбор сервера"
                        )
                    }
                )

                put(
                    JSONObject().apply {
                        put(
                            "tag",
                            "geoip-ru"
                        )

                        put(
                            "type",
                            "remote"
                        )

                        put(
                            "format",
                            "binary"
                        )

                        put(
                            "url",
                            "https://raw.githubusercontent.com/" +
                                "SagerNet/sing-geoip/" +
                                "rule-set/" +
                                "geoip-ru.srs"
                        )

                        put(
                            "download_detour",
                            "Выбор сервера"
                        )
                    }
                )
            }
        )

        route.put(
            "rules",
            JSONArray().apply {
                put(
                    JSONObject().apply {
                        put(
                            "action",
                            "sniff"
                        )
                    }
                )

                put(
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

                put(
                    JSONObject().apply {
                        put(
                            "ip_is_private",
                            true
                        )

                        put(
                            "outbound",
                            "direct"
                        )
                    }
                )

                put(
                    JSONObject().apply {
                        put(
                            "package_name",
                            JSONArray().apply {
                                put(
                                    "ru.vk.store"
                                )

                                put(
                                    "com.vk.store"
                                )
                            }
                        )

                        put(
                            "outbound",
                            "direct"
                        )
                    }
                )

                put(
                    JSONObject().apply {
                        put(
                            "rule_set",
                            JSONArray().apply {
                                put(
                                    "geosite-category-ru"
                                )

                                put(
                                    "geoip-ru"
                                )
                            }
                        )

                        put(
                            "outbound",
                            "direct"
                        )
                    }
                )

                put(
                    JSONObject().apply {
                        put(
                            "domain_suffix",
                            JSONArray().apply {
                                put(".ru")
                                put(".su")
                                put(".xn--p1ai")
                                put(".by")
                                put(".kz")
                            }
                        )

                        put(
                            "outbound",
                            "direct"
                        )
                    }
                )
            }
        )

        route.put(
            "final",
            "Выбор сервера"
        )

        route.put(
            "auto_detect_interface",
            true
        )

        root.put(
            "route",
            route
        )

        return root.toString(2)
        }
            private fun applySubscriptionRouting(
        root: JSONObject,
        routingMode: SubscriptionRouting.Mode
    ) {
        val route =
            root.optJSONObject("route")
                ?: JSONObject().also {
                    root.put("route", it)
                }

        val outbounds =
            root.optJSONArray("outbounds")
                ?: return

        val availableTags =
            mutableSetOf<String>()

        for (
            i in 0 until
                outbounds.length()
        ) {
            val outbound =
                outbounds.optJSONObject(i)
                    ?: continue

            val tag =
                outbound.optString("tag")

            if (tag.isNotBlank()) {
                availableTags.add(tag)
            }
        }

        val normalSelector =
            SubscriptionRouting.NORMAL_SELECTOR_TAG

        val whitelistSelector =
            SubscriptionRouting.WHITELIST_SELECTOR_TAG

        if (
            normalSelector !in availableTags
        ) {
            return
        }

        val rules =
            route.optJSONArray("rules")
                ?: JSONArray()

        val newRules =
            JSONArray()

        for (
            i in 0 until
                rules.length()
        ) {
            val rule =
                rules.optJSONObject(i)
                    ?: continue

            newRules.put(rule)
        }

        when (routingMode) {
            SubscriptionRouting.Mode.NORMAL -> {
                route.put(
                    "final",
                    normalSelector
                )
            }

            SubscriptionRouting.Mode.WHITELIST_BYPASS -> {
                if (
                    whitelistSelector in
                    availableTags
                ) {
                    route.put(
                        "final",
                        whitelistSelector
                    )
                } else {
                    route.put(
                        "final",
                        normalSelector
                    )
                }
            }
        }

        route.put(
            "rules",
            newRules
        )

        root.put(
            "route",
            route
        )
    }

    private fun ensureRouteActions(
        root: JSONObject
    ) {
        val route =
            root.optJSONObject("route")
                ?: return

        val rules =
            route.optJSONArray("rules")
                ?: JSONArray()

        val newRules =
            JSONArray()

        var hasSniff = false
        var hasHijackDns = false

        for (
            i in 0 until
                rules.length()
        ) {
            val rule =
                rules.optJSONObject(i)
                    ?: continue

            val action =
                rule.optString("action")

            if (action == "sniff") {
                hasSniff = true
            }

            if (
                action == "hijack-dns"
            ) {
                hasHijackDns = true
            }

            if (
                rule.optString("protocol") ==
                "dns"
            ) {
                rule.remove(
                    "outbound"
                )

                rule.put(
                    "action",
                    "hijack-dns"
                )

                hasHijackDns = true
            }

            newRules.put(rule)
        }

        val finalRules =
            JSONArray()

        if (!hasSniff) {
            finalRules.put(
                JSONObject().apply {
                    put(
                        "action",
                        "sniff"
                    )
                }
            )
        }

        if (!hasHijackDns) {
            finalRules.put(
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

        for (
            i in 0 until
                newRules.length()
        ) {
            finalRules.put(
                newRules.get(i)
            )
        }

        route.put(
            "rules",
            finalRules
        )
    }

    private fun normalizeSelectorOutbounds(
        root: JSONObject
    ) {
        val outbounds =
            root.optJSONArray("outbounds")
                ?: return

        val existingTags =
            mutableSetOf<String>()

        for (
            i in 0 until
                outbounds.length()
        ) {
            val outbound =
                outbounds.optJSONObject(i)
                    ?: continue

            val tag =
                outbound.optString("tag")

            if (tag.isNotBlank()) {
                existingTags.add(tag)
            }
        }

        for (
            i in 0 until
                outbounds.length()
        ) {
            val outbound =
                outbounds.optJSONObject(i)
                    ?: continue

            if (
                outbound.optString("type") !=
                "selector"
            ) {
                continue
            }

            val selectorOutbounds =
                outbound.optJSONArray(
                    "outbounds"
                )
                    ?: continue

            val cleaned =
                JSONArray()

            val added =
                mutableSetOf<String>()

            for (
                j in 0 until
                    selectorOutbounds.length()
            ) {
                val tag =
                    selectorOutbounds.optString(j)

                if (
                    tag.isBlank() ||
                    tag !in existingTags ||
                    !added.add(tag)
                ) {
                    continue
                }

                cleaned.put(tag)
            }

            if (
                cleaned.length() > 0
            ) {
                outbound.put(
                    "outbounds",
                    cleaned
                )

                val defaultTag =
                    outbound.optString(
                        "default"
                    )

                if (
                    defaultTag.isBlank() ||
                    defaultTag !in added
                ) {
                    outbound.put(
                        "default",
                        cleaned.getString(0)
                    )
                }
            }
        }
    }

    private fun finalizeConfig(
        root: JSONObject
    ): String {
        migrateInbounds(root)
        migrateOutbounds(root)
        migrateRoute(root)
        ensureRouteActions(root)
        normalizeSelectorOutbounds(root)

        return root.toString(2)
    }
        private fun parseConfigText(
        text: String
    ): JSONObject? {
        val decoded =
            tryDecodeBase64(text)

        if (
            decoded.startsWith("{")
        ) {
            return try {
                JSONObject(decoded)
            } catch (_: Exception) {
                null
            }
        }

        if (
            decoded.startsWith("[")
        ) {
            return try {
                val array =
                    JSONArray(decoded)

                JSONObject().apply {
                    put(
                        "outbounds",
                        array
                    )
                }
            } catch (_: Exception) {
                null
            }
        }

        val nodes =
            parseUriLines(decoded)

        if (nodes.isEmpty()) {
            return null
        }

        return JSONObject().apply {
            put(
                "outbounds",
                JSONArray().apply {
                    for (node in nodes) {
                        put(node)
                    }
                }
            )
        }
    }

    private fun buildSubscriptionConfig(
        text: String,
        routingMode: SubscriptionRouting.Mode
    ): String? {
        val root =
            parseConfigText(text)
                ?: return null

        if (
            root.optJSONArray("inbounds") ==
            null
        ) {
            val nodes =
                mutableListOf<JSONObject>()

            val outbounds =
                root.optJSONArray("outbounds")
                    ?: JSONArray()

            for (
                i in 0 until
                    outbounds.length()
            ) {
                outbounds.optJSONObject(i)
                    ?.let {
                        nodes.add(it)
                    }
            }

            val generatedConfig =
                buildSingBoxConfig(nodes)

            return try {
                val generatedRoot =
                    JSONObject(generatedConfig)

                applySubscriptionRouting(
                    generatedRoot,
                    routingMode
                )

                finalizeConfig(generatedRoot)
            } catch (_: Exception) {
                null
            }
        }

        applySubscriptionRouting(
            root,
            routingMode
        )

        return finalizeConfig(root)
    }

    private fun getRoutingMode(
        isWhitelistBypass: Boolean
    ): SubscriptionRouting.Mode {
        return if (isWhitelistBypass) {
            SubscriptionRouting.Mode
                .WHITELIST_BYPASS
        } else {
            SubscriptionRouting.Mode.NORMAL
        }
    }

    private fun isSubscriptionContent(
        text: String
    ): Boolean {
        val decoded =
            tryDecodeBase64(text)

        if (
            decoded.contains("vless://") ||
            decoded.contains("vmess://") ||
            decoded.contains("trojan://") ||
            decoded.contains("ss://")
        ) {
            return true
        }

        if (
            decoded.startsWith("{") ||
            decoded.startsWith("[")
        ) {
            return true
        }

        return false
    }

    private fun mergeOutbounds(
        root: JSONObject,
        importedOutbounds: List<JSONObject>
    ) {
        if (
            importedOutbounds.isEmpty()
        ) {
            return
        }

        val outbounds =
            root.optJSONArray("outbounds")
                ?: JSONArray().also {
                    root.put(
                        "outbounds",
                        it
                    )
                }

        val existingTags =
            mutableSetOf<String>()

        for (
            i in 0 until
                outbounds.length()
        ) {
            val outbound =
                outbounds.optJSONObject(i)
                    ?: continue

            outbound.optString("tag")
                .takeIf {
                    it.isNotBlank()
                }
                ?.let {
                    existingTags.add(it)
                }
        }

        for (
            imported in importedOutbounds
        ) {
            val type =
                imported.optString("type")

            if (
                type == "dns" ||
                type == "direct" ||
                type == "block" ||
                type == "selector" ||
                type == "urltest"
            ) {
                continue
            }

            val originalTag =
                imported.optString("tag")
                    .ifBlank {
                        imported.optString("server")
                    }
                    .ifBlank {
                        "Proxy"
                    }

            var uniqueTag =
                cleanNodeName(originalTag)

            var index = 1

            while (
                uniqueTag in existingTags
            ) {
                uniqueTag =
                    "${cleanNodeName(originalTag)} ($index)"

                index++
            }

            imported.put(
                "tag",
                uniqueTag
            )

            existingTags.add(uniqueTag)

            outbounds.put(imported)
        }
    }

    private fun rebuildSelector(
        root: JSONObject,
        selectorTag: String,
        includedTags: List<String>
    ) {
        val outbounds =
            root.optJSONArray("outbounds")
                ?: return

        var selector: JSONObject? =
            null

        for (
            i in 0 until
                outbounds.length()
        ) {
            val outbound =
                outbounds.optJSONObject(i)
                    ?: continue

            if (
                outbound.optString("tag") ==
                selectorTag
            ) {
                selector = outbound
                break
            }
        }

        val selectorOutbounds =
            JSONArray()

        val uniqueTags =
            linkedSetOf<String>()

        for (tag in includedTags) {
            if (
                tag.isNotBlank() &&
                uniqueTags.add(tag)
            ) {
                selectorOutbounds.put(tag)
            }
        }

        if (
            selectorTag ==
            SubscriptionRouting.NORMAL_SELECTOR_TAG &&
            uniqueTags.add("direct")
        ) {
            selectorOutbounds.put("direct")
        }

        if (
            selector == null
        ) {
            selector =
                JSONObject().apply {
                    put(
                        "type",
                        "selector"
                    )

                    put(
                        "tag",
                        selectorTag
                    )
                }

            outbounds.put(selector)
        }

        selector.put(
            "outbounds",
            selectorOutbounds
        )

        if (
            selectorOutbounds.length() > 0
        ) {
            val currentDefault =
                selector.optString("default")

            var defaultExists = false

            for (
                i in 0 until
                    selectorOutbounds.length()
            ) {
                if (
                    selectorOutbounds.optString(i) ==
                    currentDefault
                ) {
                    defaultExists = true
                    break
                }
            }

            if (!defaultExists) {
                selector.put(
                    "default",
                    selectorOutbounds.getString(0)
                )
            }
        }
    }

    private fun prepareSubscriptionOutbounds(
        root: JSONObject
    ) {
        val outbounds =
            root.optJSONArray("outbounds")
                ?: return

        val normalTags =
            mutableListOf<String>()

        val whitelistTags =
            mutableListOf<String>()

        for (
            i in 0 until
                outbounds.length()
        ) {
            val outbound =
                outbounds.optJSONObject(i)
                    ?: continue

            val type =
                outbound.optString("type")

            if (
                type in listOf(
                    "selector",
                    "urltest",
                    "direct",
                    "block",
                    "dns"
                )
            ) {
                continue
            }

            val tag =
                outbound.optString("tag")

            if (tag.isBlank()) {
                continue
            }

            if (
                SubscriptionRouting
                    .isWhitelistBypassTag(tag)
            ) {
                whitelistTags.add(tag)
            } else {
                normalTags.add(tag)
            }
        }

        rebuildSelector(
            root,
            SubscriptionRouting.NORMAL_SELECTOR_TAG,
            normalTags
        )

        if (
            whitelistTags.isNotEmpty()
        ) {
            rebuildSelector(
                root,
                SubscriptionRouting
                    .WHITELIST_SELECTOR_TAG,
                whitelistTags
            )
        }
    }

    private fun completeSubscriptionConfig(
        root: JSONObject,
        routingMode: SubscriptionRouting.Mode
    ): String {
        prepareSubscriptionOutbounds(root)

        applySubscriptionRouting(
            root,
            routingMode
        )

        migrateInbounds(root)
        migrateOutbounds(root)
        migrateRoute(root)
        ensureRouteActions(root)
        normalizeSelectorOutbounds(root)

        return root.toString(2)
    }
}
