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
            rawModel.startsWith(manufacturer, ignoreCase = true)
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

        val context =
            getApplicationContext()

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

                currentAppMethod.invoke(
                    null
                ) as? Context

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

            } catch (e: Exception) {
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

            val root =
                JSONObject(fixedRaw)

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
                dns.remove(
                    "address_strategy"
                )

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

        val cleanedServers =
            JSONArray()

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
                server.has(
                    "address_resolver"
                )
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
                        cleanAddress
                            .substringBefore("/")

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
                                cleanAddress
                                    .substringAfter("/")
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

        } catch (e: Exception) {

            server.put(
                "type",
                "udp"
            )

            server.put(
                "server",
                address
                    .substringAfter(
                        "://"
                    )
                    .substringBefore("/")
            )
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

            val addresses =
                JSONArray()

            if (
                inbound.has("inet4_address")
            ) {

                val value =
                    inbound.remove(
                        "inet4_address"
                    )

                if (value is JSONArray) {

                    for (
                        j in 0 until value.length()
                    ) {

                        addresses.put(
                            value.get(j)
                        )
                    }

                } else {

                    addresses.put(value)
                }
            }

            if (
                inbound.has("inet6_address")
            ) {

                val value =
                    inbound.remove(
                        "inet6_address"
                    )

                if (value is JSONArray) {

                    for (
                        j in 0 until value.length()
                    ) {

                        addresses.put(
                            value.get(j)
                        )
                    }

                } else {

                    addresses.put(value)
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
            inbound.remove("sniff_override_destination")
            inbound.remove("sniff_timeout")
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
            i in 0 until outbounds.length()
        ) {

            val outbound =
                outbounds.optJSONObject(i)
                    ?: continue

            if (
                outbound.optString("type") !=
                "dns"
            ) {

                cleanedOutbounds.put(
                    outbound
                )
            }
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

        val newRules =
            JSONArray()

        var hasSniff = false
        var hasHijackDns = false

        for (
            i in 0 until oldRules.length()
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

            val protocol =
                rule.optString("protocol")

            val outbound =
                rule.optString("outbound")

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

            if (
                rule.optString("action") ==
                "hijack-dns"
            ) {

                hasHijackDns = true
            }

            newRules.put(rule)
        }

        if (!hasSniff) {

            newRules.put(
                0,
                JSONObject().apply {

                    put(
                        "action",
                        "sniff"
                    )
                }
            )
        }

        if (!hasHijackDns) {

            val hijackRule =
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

            val insertIndex =
                if (hasSniff) {
                    1
                } else {
                    1
                }

            newRules.put(
                insertIndex,
                hijackRule
            )
        }

        route.put(
            "rules",
            newRules
        )
    }

    private fun tryDecodeBase64(
        text: String
    ): String {

        val trimmed =
            text.trim()

        if (
            trimmed.contains("://") ||
            trimmed.startsWith("{") ||
            trimmed.startsWith("[")
        ) {
            return trimmed
        }

        val nonCommentLines =
            text.lines()
                .filter {

                    val current =
                        it.trim()

                    current.isNotEmpty() &&
                        !current.startsWith("#") &&
                        !current.startsWith("//")
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

                val decodedString =
                    String(
                        decoded,
                        StandardCharsets.UTF_8
                    ).trim()

                if (
                    decodedString.contains(
                        "://"
                    ) ||
                    decodedString.startsWith("{") ||
                    decodedString.startsWith("[")
                ) {

                    return decodedString
                }

            } catch (e: Exception) {
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

        if (!trimmed.contains("%")) {
            return trimmed
        }

        return try {

            URLDecoder.decode(
                trimmed,
                StandardCharsets.UTF_8.name()
            ).trim()

        } catch (e: Exception) {
            trimmed
        }
    }

    private fun isWhitelistBypassServer(
        tag: String
    ): Boolean {

        val normalized =
            tag.lowercase(Locale.ROOT)

        return normalized.contains(
            "обход"
        ) ||
            normalized.contains(
                "white list"
            ) ||
            normalized.contains(
                "whitelist"
            ) ||
            normalized.contains(
                "white-list"
            )
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
                        "vless://"
                    ) -> {

                        parseVless(line)
                            ?.let {
                                outbounds.add(it)
                            }
                    }

                    line.startsWith(
                        "vmess://"
                    ) -> {

                        parseVmess(line)
                            ?.let {
                                outbounds.add(it)
                            }
                    }

                    line.startsWith(
                        "trojan://"
                    ) -> {

                        parseTrojan(line)
                            ?.let {
                                outbounds.add(it)
                            }
                    }

                    line.startsWith(
                        "ss://"
                    ) -> {

                        parseShadowsocks(line)
                            ?.let {
                                outbounds.add(it)
                            }
                    }
                }

            } catch (e: Exception) {
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

        val queryString =
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
                hostPort.substringBefore(
                    "]:"
                ) + "]"

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
            parseQueryParams(
                queryString
            )

        val outbound =
            JSONObject()

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
            params["packetEncoding"] ?: "xudp"
        )

        val security =
            params["security"] ?: "none"

        if (
            security == "tls" ||
            security == "reality"
        ) {

            val tls =
                JSONObject()

            tls.put(
                "enabled",
                true
            )

            params["sni"]?.let {

                tls.put(
                    "server_name",
                    URLDecoder.decode(
                        it,
                        StandardCharsets.UTF_8.name()
                    )
                )
            }

            val utls =
                JSONObject()

            utls.put(
                "enabled",
                true
            )

            utls.put(
                "fingerprint",
                params["fp"] ?: "chrome"
            )

            tls.put(
                "utls",
                utls
            )

            if (
                security == "reality"
            ) {

                val reality =
                    JSONObject()

                reality.put(
                    "enabled",
                    true
                )

                params["pbk"]?.let {

                    reality.put(
                        "public_key",
                        it
                    )
                }

                params["sid"]?.let {

                    reality.put(
                        "short_id",
                        it
                    )
                }

                tls.put(
                    "reality",
                    reality
                )
            }

            outbound.put(
                "tls",
                tls
            )
        }

        val transportType =
            params["type"] ?: "tcp"

        if (
            transportType == "ws" ||
            transportType == "grpc" ||
            transportType == "http"
        ) {

            val transport =
                JSONObject()

            transport.put(
                "type",
                transportType
            )

            when (transportType) {

                "ws" -> {

                    params["path"]?.let {

                        transport.put(
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

                        transport.put(
                            "headers",
                            headers
                        )
                    }
                }

                "grpc" -> {

                    params["serviceName"]?.let {

                        transport.put(
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
                transport
            )
        }

        return outbound
    }

    private fun parseVmess(
        line: String
    ): JSONObject? {

        val base64 =
            line.removePrefix(
                "vmess://"
            ).trim()

        val jsonString =
            try {

                String(
                    Base64.decode(
                        base64,
                        Base64.DEFAULT
                    ),
                    StandardCharsets.UTF_8
                )

            } catch (e1: Exception) {

                String(
                    Base64.decode(
                        base64,
                        Base64.URL_SAFE
                    ),
                    StandardCharsets.UTF_8
                )
            }

        val vmess =
            JSONObject(jsonString)

        val server =
            vmess.optString("add")

        val port =
            vmess.optInt(
                "port",
                443
            )

        val uuid =
            vmess.optString("id")

        val tag =
            cleanNodeName(
                vmess.optString("ps")
            )

        if (
            server.isEmpty() ||
            uuid.isEmpty()
        ) {
            return null
        }

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
            vmess.optInt(
                "aid",
                0
            )
        )

        outbound.put(
            "security",
            vmess.optString(
                "scy",
                "auto"
            )
        )

        if (
            vmess
                .optString("tls")
                .equals(
                    "tls",
                    ignoreCase = true
                )
        ) {

            val tls =
                JSONObject()

            tls.put(
                "enabled",
                true
            )

            val sni =
                vmess
                    .optString("sni")
                    .ifEmpty {
                        vmess.optString("host")
                    }

            if (sni.isNotEmpty()) {

                tls.put(
                    "server_name",
                    sni
                )
            }

            outbound.put(
                "tls",
                tls
            )
        }

        val network =
            vmess.optString(
                "net",
                "tcp"
            )

        if (
            network == "ws" ||
            network == "grpc"
        ) {

            val transport =
                JSONObject()

            transport.put(
                "type",
                network
            )

            val path =
                vmess.optString("path")

            if (network == "ws") {

                if (path.isNotEmpty()) {

                    transport.put(
                        "path",
                        path
                    )
                }

                val host =
                    vmess.optString("host")

                if (host.isNotEmpty()) {

                    val headers =
                        JSONObject()

                    headers.put(
                        "Host",
                        host
                    )

                    transport.put(
                        "headers",
                        headers
                    )
                }

            } else if (
                network == "grpc" &&
                path.isNotEmpty()
            ) {

                transport.put(
                    "service_name",
                    path
                )
            }

            outbound.put(
                "transport",
                transport
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
                hostPort.substringBefore(
                    "]:"
                ) + "]"

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
            parseQueryParams(
                queryString
            )

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

        val tls =
            JSONObject()

        tls.put(
            "enabled",
            true
        )

        tls.put(
            "server_name",
            params["sni"]
                ?: params["peer"]
                ?: server
        )

        outbound.put(
            "tls",
            tls
        )

        val transportType =
            params["type"] ?: "tcp"

        if (
            transportType == "ws" ||
            transportType == "grpc"
        ) {

            val transport =
                JSONObject()

            transport.put(
                "type",
                transportType
            )

            if (
                transportType == "ws"
            ) {

                params["path"]?.let {

                    transport.put(
                        "path",
                        URLDecoder.decode(
                            it,
                            StandardCharsets.UTF_8.name()
                        )
                    )
                }

            } else {

                params["serviceName"]?.let {

                    transport.put(
                        "service_name",
                        URLDecoder.decode(
                            it,
                            StandardCharsets.UTF_8.name()
                        )
                    )
                }
            }

            outbound.put(
                "transport",
                transport
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
            line.substringBefore("#")
                .removePrefix("ss://")
                .trim()

        var method = ""
        var password = ""
        var server = ""
        var port = 8388

        if (
            withoutTag.contains("@")
        ) {

            val userPart =
                withoutTag.substringBefore("@")

            val hostPort =
                withoutTag.substringAfter("@")

            val decodedUserInfo =
                try {

                    String(
                        Base64.decode(
                            userPart,
                            Base64.DEFAULT
                        ),
                        StandardCharsets.UTF_8
                    )

                } catch (e: Exception) {
                    userPart
                }

            val credentials =
                decodedUserInfo.split(
                    ":",
                    limit = 2
                )

            if (
                credentials.size == 2
            ) {

                method =
                    credentials[0]

                password =
                    credentials[1]
            }

            if (
                hostPort.startsWith("[") &&
                hostPort.contains("]:")
            ) {

                server =
                    hostPort.substringBefore(
                        "]:"
                    ) + "]"

                port =
                    hostPort
                        .substringAfter("]:")
                        .toIntOrNull()
                        ?: 8388

            } else if (
                hostPort.contains(":")
            ) {

                server =
                    hostPort.substringBeforeLast(
                        ":"
                    )

                port =
                    hostPort
                        .substringAfterLast(
                            ":"
                        )
                        .toIntOrNull()
                        ?: 8388

            } else {

                server = hostPort
            }

        } else {

            val decoded =
                try {

                    String(
                        Base64.decode(
                            withoutTag,
                            Base64.DEFAULT
                        ),
                        StandardCharsets.UTF_8
                    )

                } catch (e: Exception) {

                    try {

                        String(
                            Base64.decode(
                                withoutTag,
                                Base64.URL_SAFE
                            ),
                            StandardCharsets.UTF_8
                        )

                    } catch (e2: Exception) {
                        ""
                    }
                }

            val split =
                decoded.split(
                    "@",
                    limit = 2
                )

            if (
                split.size == 2
            ) {

                val credentials =
                    split[0].split(
                        ":",
                        limit = 2
                    )

                method =
                    credentials[0]

                password =
                    credentials.getOrElse(1) {
                        ""
                    }

                val hostPort =
                    split[1]

                if (
                    hostPort.startsWith("[") &&
                    hostPort.contains("]:")
                ) {

                    server =
                        hostPort.substringBefore(
                            "]:"
                        ) + "]"

                    port =
                        hostPort
                            .substringAfter("]:")
                            .toIntOrNull()
                            ?: 8388

                } else if (
                    hostPort.contains(":")
                ) {

                    server =
                        hostPort.substringBeforeLast(
                            ":"
                        )

                    port =
                        hostPort
                            .substringAfterLast(
                                ":"
                            )
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

            if (index > 0) {

                result[
                    pair.substring(
                        0,
                        index
                    )
                ] =
                    pair.substring(
                        index + 1
                    )
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

        val bypassTags =
            mutableSetOf<String>()

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

                if (
                    isWhitelistBypassServer(
                        cleanTag
                    )
                ) {

                    bypassTags.add(
                        uniqueTag
                    )
                }
            }
        }

        if (proxyTags.isEmpty()) {

            for (node in validNodes) {

                proxyTags.add(
                    node.getString("tag")
                )
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

        root.put(
            "dns",
            buildDnsConfig()
        )

        root.put(
            "inbounds",
            buildInbounds()
        )

        root.put(
            "outbounds",
            buildOutbounds(
                validNodes,
                proxyTags
            )
        )

        root.put(
            "route",
            buildRouteConfig(
                bypassTags
            )
        )

        return root.toString(2)
    }

    private fun buildDnsConfig(): JSONObject {

        val dns =
            JSONObject()

        val servers =
            JSONArray()

        servers.put(
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

        servers.put(
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

        dns.put(
            "servers",
            servers
        )

        dns.put(
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

                                put("ru")
                                put("su")
                                put("xn--p1ai")
                                put("by")
                                put("kz")
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

        dns.put(
            "final",
            "dns-remote"
        )

        dns.put(
            "strategy",
            "ipv4_only"
        )

        return dns
    }

    private fun buildInbounds(): JSONArray {

        return JSONArray().apply {

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
    }

    private fun buildOutbounds(
        nodes: List<JSONObject>,
        proxyTags: List<String>
    ): JSONArray {

        val outbounds =
            JSONArray()

        val selectorExists =
            nodes.any {
                it.optString("tag") ==
                    "Выбор сервера"
            }

        if (!selectorExists) {

            val selector =
                JSONObject()

            selector.put(
                "type",
                "selector"
            )

            selector.put(
                "tag",
                "Выбор сервера"
            )

            val selectorOutbounds =
                JSONArray()

            for (tag in proxyTags) {

                selectorOutbounds.put(
                    tag
                )
            }

            selectorOutbounds.put(
                "direct"
            )

            selector.put(
                "outbounds",
                selectorOutbounds
            )

            if (
                proxyTags.isNotEmpty()
            ) {

                selector.put(
                    "default",
                    proxyTags.first()
                )
            }

            outbounds.put(
                selector
            )
        }

        for (node in nodes) {

            outbounds.put(node)
        }

        if (
            nodes.none {
                it.optString("tag") ==
                    "direct"
            }
        ) {

            outbounds.put(
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
            nodes.none {
                it.optString("tag") ==
                    "block"
            }
        ) {

            outbounds.put(
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

        return outbounds
    }

    private fun buildRouteConfig(
        bypassTags: Set<String>
    ): JSONObject {

        val route =
            JSONObject()

        route.put(
            "default_domain_resolver",
            "dns-direct"
        )

        route.put(
            "rule_set",
            buildRuleSets()
        )

        val rules =
            JSONArray()

        rules.put(
            JSONObject().apply {

                put(
                    "action",
                    "sniff"
                )
            }
        )

        rules.put(
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

        rules.put(
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

        rules.put(
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

        rules.put(
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

        rules.put(
            JSONObject().apply {

                put(
                    "domain_suffix",
                    JSONArray().apply {

                        put("ru")
                        put("su")
                        put("xn--p1ai")
                        put("by")
                        put("kz")
                    }
                )

                put(
                    "outbound",
                    "direct"
                )
            }
        )

        route.put(
            "rules",
            rules
        )

        /*
         * Обычные серверы:
         * российское и доступное напрямую
         * обходят прокси по правилам выше.
         *
         * Всё остальное:
         * идёт через выбранный сервер.
         *
         * Серверы с названием "обход":
         * автоматически попадают в тот же
         * селектор, но распознаются отдельно.
         * Это позволяет в дальнейшем расширить
         * индивидуальную маршрутизацию
         * без изменения парсера подписки.
         */
        route.put(
            "final",
            "Выбор сервера"
        )

        route.put(
            "auto_detect_interface",
            true
        )

        return route
    }
    
    private fun buildRuleSets(): JSONArray {

        return JSONArray().apply {

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
    }

    override fun close() {
        client.close()
    }

    companion object {

        const val userAgent =
            "SFAxtnd"
    }
}
