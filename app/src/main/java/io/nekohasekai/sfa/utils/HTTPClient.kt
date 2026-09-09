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

private val hwidMemoryCache =
    ConcurrentHashMap<String, String>()

class HTTPClient : Closeable {

    private val client =
        Libbox.newHTTPClient()

    init {
        client.modernTLS()
    }

    fun getString(
        url: String
    ): String {
        val request =
            client.newRequest()

        request.setURL(url)

        val hwid =
            getOrCreateHwid(url)

        val manufacturer =
            Build.MANUFACTURER
                .replaceFirstChar {
                    it.uppercase()
                }

        val rawModel =
            Build.MODEL

        val model =
            if (
                rawModel.startsWith(
                    manufacturer,
                    ignoreCase = true
                )
            ) {
                rawModel
            } else {
                "$manufacturer $rawModel"
            }

        val androidVer =
            Build.VERSION.RELEASE

        val buildId =
            Build.ID.ifEmpty {
                "UKQ1.231003.002"
            }

        val userAgentStr =
            "sing-box/1.14.0 " +
                "SFAxtnd/0.0.8 " +
                "(Linux; Android $androidVer; " +
                "$model Build/$buildId) " +
                "HWID/$hwid"

        request.setUserAgent(
            userAgentStr
        )

        request.setHeader(
            "HWID",
            hwid
        )

        request.setHeader(
            "hwid",
            hwid
        )

        request.setHeader(
            "X-HWID",
            hwid
        )

        request.setHeader(
            "Device-ID",
            hwid
        )

        request.setHeader(
            "Happ-HWID",
            hwid
        )

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

        val response =
            request.execute()

        val rawContent =
            response.content.unwrap

        return processSubscriptionContent(
            rawContent
        )
    }

    private fun getOrCreateHwid(
        url: String
    ): String {
        val normalizedUrl =
            url.trim()

        val urlKey =
            try {
                val digest =
                    MessageDigest.getInstance(
                        "SHA-256"
                    )

                val hash =
                    digest.digest(
                        normalizedUrl.toByteArray(
                            StandardCharsets.UTF_8
                        )
                    )

                hash.joinToString("") {
                    "%02x".format(it)
                }
            } catch (_: Exception) {
                normalizedUrl
                    .hashCode()
                    .toString()
            }

        val memoryHwid =
            hwidMemoryCache[urlKey]

        if (
            !memoryHwid.isNullOrBlank()
        ) {
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

            if (
                !savedHwid.isNullOrBlank()
            ) {
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

            field.isAccessible =
                true

            field.get(null) as? Context
        } catch (_: Exception) {
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
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun processSubscriptionContent(
        raw: String
    ): String {
        val trimmed =
            raw.trim()

        val routingMode =
            SubscriptionRouting.detectMode(
                raw
            )

        if (
            trimmed.startsWith("{") &&
            (
                trimmed.contains(
                    "\"outbounds\""
                ) ||
                    trimmed.contains(
                        "\"route\""
                    )
                )
        ) {
            return buildSubscriptionConfig(
                trimmed,
                routingMode
            ) ?: trimmed
        }

        val contentToParse =
            tryDecodeBase64(
                trimmed
            )

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
            return buildSubscriptionConfig(
                contentToParse,
                routingMode
            ) ?: contentToParse
        }

        if (
            contentToParse.startsWith("[")
        ) {
            try {
                val jsonArray =
                    JSONArray(
                        contentToParse
                    )

                val nodes =
                    mutableListOf<JSONObject>()

                for (
                    i in 0 until
                        jsonArray.length()
                ) {
                    val obj =
                        jsonArray
                            .optJSONObject(i)
                            ?: continue

                    nodes.add(obj)
                }

                if (
                    nodes.isNotEmpty()
                ) {
                    val generatedConfig =
                        buildSingBoxConfig(
                            nodes
                        )

                    val root =
                        JSONObject(
                            generatedConfig
                        )

                    return completeSubscriptionConfig(
                        root,
                        routingMode
                    )
                }
            } catch (_: Exception) {
            }
        }

        val nodes =
            parseUriLines(
                contentToParse
            )

        if (
            nodes.isNotEmpty()
        ) {
            val generatedConfig =
                buildSingBoxConfig(
                    nodes
                )

            return try {
                val root =
                    JSONObject(
                        generatedConfig
                    )

                completeSubscriptionConfig(
                    root,
                    routingMode
                )
            } catch (_: Exception) {
                generatedConfig
            }
        }

        return sanitizeAndMigrateConfig(
            trimmed,
            routingMode
        )
    }

    private fun sanitizeAndMigrateConfig(
        jsonStr: String,
        routingMode: SubscriptionRouting.Mode
    ): String {
        return try {
            val fixedRaw =
                jsonStr
                    .replace(
                        "https://raw.githubusercontent.com/" +
                            "SagerNet/sing-geosite/" +
                            "rule-set/geosite-ru.srs",
                        "https://raw.githubusercontent.com/" +
                            "SagerNet/sing-geosite/" +
                            "rule-set/" +
                            "geosite-category-ru.srs"
                    )
                    .replace(
                        "\"geosite-ru\"",
                        "\"geosite-category-ru\""
                    )

            val root =
                JSONObject(
                    fixedRaw
                )

            migrateDns(root)

            completeSubscriptionConfig(
                root,
                routingMode
            )
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

        dns.remove(
            "independent_cache"
        )

        if (
            dns.has(
                "address_strategy"
            )
        ) {
            val strategy =
                dns.remove(
                    "address_strategy"
                )

            if (
                !dns.has("strategy")
            ) {
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
            i in 0 until
                servers.length()
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

            cleanedServers.put(
                server
            )
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
                        hostPort.substringBefore(
                            ":"
                        )

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
    }
        private fun migrateInbounds(
        root: JSONObject
    ) {
        val inbounds =
            root.optJSONArray("inbounds")
                ?: return

        val newInbounds =
            JSONArray()

        for (
            i in 0 until
                inbounds.length()
        ) {
            val inbound =
                inbounds.optJSONObject(i)
                    ?: continue

            val type =
                inbound.optString("type")

            when (type) {
                "mixed" -> {
                    migrateLegacyMixedInbound(
                        inbound
                    )
                }

                "socks" -> {
                    migrateLegacySocksInbound(
                        inbound
                    )
                }

                "http" -> {
                    migrateLegacyHttpInbound(
                        inbound
                    )
                }

                "tun" -> {
                    migrateLegacyTunInbound(
                        inbound
                    )
                }
            }

            newInbounds.put(
                inbound
            )
        }

        root.put(
            "inbounds",
            newInbounds
        )
    }

    private fun migrateLegacyMixedInbound(
        inbound: JSONObject
    ) {
        val listen =
            inbound.optString(
                "listen",
                "127.0.0.1"
            )

        val listenPort =
            inbound.optInt(
                "listen_port",
                0
            )

        if (
            !inbound.has("listen")
        ) {
            inbound.put(
                "listen",
                listen
            )
        }

        if (
            listenPort > 0
        ) {
            inbound.put(
                "listen_port",
                listenPort
            )
        }
    }

    private fun migrateLegacySocksInbound(
        inbound: JSONObject
    ) {
        val listen =
            inbound.optString(
                "listen",
                "127.0.0.1"
            )

        val listenPort =
            inbound.optInt(
                "listen_port",
                0
            )

        if (
            !inbound.has("listen")
        ) {
            inbound.put(
                "listen",
                listen
            )
        }

        if (
            listenPort > 0
        ) {
            inbound.put(
                "listen_port",
                listenPort
            )
        }
    }

    private fun migrateLegacyHttpInbound(
        inbound: JSONObject
    ) {
        val listen =
            inbound.optString(
                "listen",
                "127.0.0.1"
            )

        val listenPort =
            inbound.optInt(
                "listen_port",
                0
            )

        if (
            !inbound.has("listen")
        ) {
            inbound.put(
                "listen",
                listen
            )
        }

        if (
            listenPort > 0
        ) {
            inbound.put(
                "listen_port",
                listenPort
            )
        }
    }

    private fun migrateLegacyTunInbound(
        inbound: JSONObject
    ) {
        inbound.remove(
            "inet4_address"
        )

        inbound.remove(
            "inet6_address"
        )

        inbound.remove(
            "inet4_route_address"
        )

        inbound.remove(
            "inet6_route_address"
        )

        inbound.remove(
            "inet4_route_exclude_address"
        )

        inbound.remove(
            "inet6_route_exclude_address"
        )

        inbound.remove(
            "gso"
        )

        inbound.remove(
            "include_uid"
        )

        inbound.remove(
            "exclude_uid"
        )

        inbound.remove(
            "include_package"
        )

        inbound.remove(
            "exclude_package"
        )

        inbound.remove(
            "interface_name"
        )

        if (
            !inbound.has("address")
        ) {
            inbound.put(
                "address",
                JSONArray().apply {
                    put(
                        "172.19.0.1/30"
                    )
                }
            )
        }

        if (
            !inbound.has("auto_route")
        ) {
            inbound.put(
                "auto_route",
                true
            )
        }

        if (
            !inbound.has("strict_route")
        ) {
            inbound.put(
                "strict_route",
                false
            )
        }

        if (
            !inbound.has("stack")
        ) {
            inbound.put(
                "stack",
                "gvisor"
            )
        }
    }

    private fun migrateOutbounds(
        root: JSONObject
    ) {
        val outbounds =
            root.optJSONArray("outbounds")
                ?: return

        val newOutbounds =
            JSONArray()

        val usedTags =
            mutableSetOf<String>()

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
                type == "dns"
            ) {
                continue
            }

            migrateOutbound(
                outbound
            )

            var tag =
                outbound.optString("tag")

            if (
                tag.isBlank()
            ) {
                tag =
                    cleanNodeName(
                        outbound.optString(
                            "server",
                            "Proxy"
                        )
                    )
            }

            val originalTag =
                tag

            var index = 1

            while (
                tag in usedTags
            ) {
                tag =
                    "$originalTag ($index)"

                index++
            }

            outbound.put(
                "tag",
                tag
            )

            usedTags.add(tag)

            newOutbounds.put(
                outbound
            )
        }

        root.put(
            "outbounds",
            newOutbounds
        )
    }

    private fun migrateOutbound(
        outbound: JSONObject
    ) {
        outbound.remove(
            "domain_strategy"
        )

        outbound.remove(
            "fallback_delay"
        )

        outbound.remove(
            "interrupt_exist_connections"
        )

        val type =
            outbound.optString("type")

        when (type) {
            "vless",
            "vmess",
            "trojan",
            "shadowsocks",
            "hysteria",
            "hysteria2" -> {
                migrateProxyOutbound(
                    outbound
                )
            }

            "selector",
            "urltest" -> {
                migrateGroupOutbound(
                    outbound
                )
            }
        }
    }

    private fun migrateProxyOutbound(
        outbound: JSONObject
    ) {
        if (
            outbound.has("tls")
        ) {
            val tls =
                outbound.optJSONObject(
                    "tls"
                )

            if (
                tls != null
            ) {
                tls.remove(
                    "enabled"
                )

                if (
                    tls.length() == 0
                ) {
                    outbound.remove(
                        "tls"
                    )
                }
            }
        }

        if (
            outbound.has("transport")
        ) {
            val transport =
                outbound.optJSONObject(
                    "transport"
                )

            if (
                transport != null
            ) {
                migrateTransport(
                    transport
                )
            }
        }
    }

    private fun migrateGroupOutbound(
        outbound: JSONObject
    ) {
        val outbounds =
            outbound.optJSONArray(
                "outbounds"
            )
                ?: return

        val cleaned =
            JSONArray()

        val seen =
            mutableSetOf<String>()

        for (
            i in 0 until
                outbounds.length()
        ) {
            val tag =
                outbounds.optString(i)

            if (
                tag.isBlank() ||
                !seen.add(tag)
            ) {
                continue
            }

            cleaned.put(tag)
        }

        outbound.put(
            "outbounds",
            cleaned
        )

        val defaultTag =
            outbound.optString(
                "default"
            )

        if (
            defaultTag.isNotBlank() &&
            defaultTag !in seen
        ) {
            outbound.remove(
                "default"
            )
        }
    }

    private fun migrateTransport(
        transport: JSONObject
    ) {
        val type =
            transport.optString(
                "type"
            )

        when (type) {
            "http" -> {
                if (
                    transport.has(
                        "host"
                    )
                ) {
                    val host =
                        transport.remove(
                            "host"
                        )

                    transport.put(
                        "host",
                        host
                    )
                }

                if (
                    transport.has(
                        "path"
                    )
                ) {
                    val path =
                        transport.optString(
                            "path"
                        )

                    transport.put(
                        "path",
                        if (
                            path.startsWith("/")
                        ) {
                            path
                        } else {
                            "/$path"
                        }
                    )
                }
            }

            "grpc" -> {
                if (
                    transport.has(
                        "serviceName"
                    )
                ) {
                    val serviceName =
                        transport.remove(
                            "serviceName"
                        )

                    transport.put(
                        "service_name",
                        serviceName
                    )
                }
            }

            "ws" -> {
                if (
                    transport.has(
                        "path"
                    )
                ) {
                    val path =
                        transport.optString(
                            "path"
                        )

                    if (
                        path.isNotBlank() &&
                        !path.startsWith("/")
                    ) {
                        transport.put(
                            "path",
                            "/$path"
                        )
                    }
                }
            }
        }
    }
        private fun migrateRoute(
        root: JSONObject
    ) {
        val route =
            root.optJSONObject("route")
                ?: return

        route.remove("auto_detect_interface")

        if (
            route.has("final") &&
            route.optString("final").isBlank()
        ) {
            route.remove("final")
        }

        val rules =
            route.optJSONArray("rules")
                ?: JSONArray()

        val newRules =
            JSONArray()

        for (
            i in 0 until rules.length()
        ) {
            val rule =
                rules.optJSONObject(i)
                    ?: continue

            migrateRouteRule(rule)

            newRules.put(rule)
        }

        route.put(
            "rules",
            newRules
        )
    }

    private fun migrateRouteRule(
        rule: JSONObject
    ) {
        if (
            rule.has("outbound") &&
            !rule.has("action")
        ) {
            val outbound =
                rule.optString("outbound")

            if (
                outbound.isNotBlank()
            ) {
                rule.put(
                    "action",
                    "route"
                )
            }
        }

        if (
            rule.optString("action") ==
            "route"
        ) {
            val outbound =
                rule.optString("outbound")

            if (
                outbound.isBlank()
            ) {
                rule.remove("action")
            }
        }

        if (
            rule.has("inbound_tag")
        ) {
            val value =
                rule.remove("inbound_tag")

            rule.put(
                "inbound",
                value
            )
        }

        if (
            rule.has("network_type")
        ) {
            val value =
                rule.remove("network_type")

            rule.put(
                "network_type",
                value
            )
        }

        if (
            rule.has("protocol")
        ) {
            val protocol =
                rule.opt("protocol")

            if (
                protocol is String &&
                protocol == "dns"
            ) {
                rule.remove("outbound")

                rule.put(
                    "action",
                    "hijack-dns"
                )
            }
        }
    }

    private fun buildSingBoxConfig(
        nodes: List<JSONObject>
    ): String {
        val root =
            JSONObject()

        val outbounds =
            JSONArray()

        val normalTags =
            mutableListOf<String>()

        val whitelistTags =
            mutableListOf<String>()

        for (node in nodes) {
            val outbound =
                JSONObject(node.toString())

            migrateOutbound(outbound)

            var tag =
                outbound.optString("tag")

            if (tag.isBlank()) {
                tag =
                    cleanNodeName(
                        outbound.optString(
                            "server",
                            "Proxy"
                        )
                    )
            }

            outbound.put(
                "tag",
                tag
            )

            outbounds.put(
                outbound
            )

            if (
                SubscriptionRouting
                    .isWhitelistBypassTag(tag)
            ) {
                whitelistTags.add(tag)
            } else {
                normalTags.add(tag)
            }
        }

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

        root.put(
            "outbounds",
            outbounds
        )

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
                SubscriptionRouting.WHITELIST_SELECTOR_TAG,
                whitelistTags
            )
        }

        root.put(
            "route",
            JSONObject().apply {
                put(
                    "rules",
                    JSONArray()
                )

                put(
                    "final",
                    SubscriptionRouting
                        .NORMAL_SELECTOR_TAG
                )
            }
        )

        return root.toString()
    }

    private fun parseUriLines(
        content: String
    ): List<JSONObject> {
        val nodes =
            mutableListOf<JSONObject>()

        val lines =
            content
                .replace("\r", "")
                .split("\n")

        for (rawLine in lines) {
            val line =
                rawLine.trim()

            if (
                line.isBlank() ||
                line.startsWith("#")
            ) {
                continue
            }

            val node =
                when {
                    line.startsWith(
                        "vless://",
                        ignoreCase = true
                    ) -> parseVless(line)

                    line.startsWith(
                        "vmess://",
                        ignoreCase = true
                    ) -> parseVmess(line)

                    line.startsWith(
                        "trojan://",
                        ignoreCase = true
                    ) -> parseTrojan(line)

                    line.startsWith(
                        "ss://",
                        ignoreCase = true
                    ) -> parseShadowsocks(line)

                    line.startsWith(
                        "hysteria2://",
                        ignoreCase = true
                    ) ||
                        line.startsWith(
                            "hy2://",
                            ignoreCase = true
                        ) -> parseHysteria2(line)

                    line.startsWith(
                        "hysteria://",
                        ignoreCase = true
                    ) -> parseHysteria(line)

                    else -> null
                }

            if (node != null) {
                nodes.add(node)
            }
        }

        return nodes
    }

    private fun parseVless(
        link: String
    ): JSONObject? {
        return try {
            val withoutScheme =
                link.substringAfter(
                    "vless://"
                )

            val fragment =
                withoutScheme.substringAfter(
                    "#",
                    ""
                )

            val beforeFragment =
                withoutScheme.substringBefore(
                    "#"
                )

            val query =
                beforeFragment.substringAfter(
                    "?",
                    ""
                )

            val authority =
                beforeFragment.substringBefore(
                    "?"
                )

            val uuid =
                authority.substringBefore("@")

            val serverPort =
                authority.substringAfter("@")

            val server =
                serverPort.substringBeforeLast(
                    ":"
                )

            val port =
                serverPort
                    .substringAfterLast(
                        ":"
                    )
                    .toIntOrNull()
                    ?: 443

            val params =
                parseQueryParameters(
                    query
                )

            val outbound =
                JSONObject()

            outbound.put(
                "type",
                "vless"
            )

            outbound.put(
                "tag",
                cleanNodeName(
                    decodeUrlComponent(
                        fragment.ifBlank {
                            "$server:$port"
                        }
                    )
                )
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

            if (
                params["flow"]
                    ?.isNotBlank() == true
            ) {
                outbound.put(
                    "flow",
                    decodeUrlComponent(
                        params["flow"]!!
                    )
                )
            }

            val encryption =
                params["encryption"]
                    ?.let {
                        decodeUrlComponent(it)
                    }

            if (
                !encryption.isNullOrBlank() &&
                encryption != "none"
            ) {
                outbound.put(
                    "packet_encoding",
                    encryption
                )
            }

            buildTls(
                outbound,
                params
            )

            buildTransport(
                outbound,
                params
            )

            outbound
        } catch (_: Exception) {
            null
        }
    }

    private fun parseVmess(
        link: String
    ): JSONObject? {
        return try {
            val encoded =
                link.substringAfter(
                    "vmess://"
                )

            val decoded =
                String(
                    Base64.decode(
                        encoded,
                        Base64.DEFAULT
                    ),
                    StandardCharsets.UTF_8
                )

            val json =
                JSONObject(decoded)

            val outbound =
                JSONObject()

            val server =
                json.optString("add")

            val port =
                json.optString("port")
                    .toIntOrNull()
                    ?: 443

            outbound.put(
                "type",
                "vmess"
            )

            outbound.put(
                "tag",
                cleanNodeName(
                    json.optString(
                        "ps",
                        "$server:$port"
                    )
                )
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
                json.optString("id")
            )

            val alterId =
                json.optInt(
                    "aid",
                    0
                )

            if (alterId > 0) {
                outbound.put(
                    "alter_id",
                    alterId
                )
            }

            val security =
                json.optString(
                    "scy",
                    "auto"
                )

            if (
                security.isNotBlank() &&
                security != "auto"
            ) {
                outbound.put(
                    "security",
                    security
                )
            }

            val network =
                json.optString(
                    "net",
                    "tcp"
                )

            val host =
                json.optString("host")

            val path =
                json.optString("path")

            val sni =
                json.optString("sni")

            val tlsEnabled =
                json.optString("tls")
                    .equals(
                        "tls",
                        ignoreCase = true
                    )

            if (tlsEnabled) {
                val tls =
                    JSONObject().apply {
                        put(
                            "enabled",
                            true
                        )

                        if (
                            sni.isNotBlank()
                        ) {
                            put(
                                "server_name",
                                sni
                            )
                        }
                    }

                outbound.put(
                    "tls",
                    tls
                )
            }

            when (network) {
                "ws" -> {
                    outbound.put(
                        "transport",
                        JSONObject().apply {
                            put(
                                "type",
                                "ws"
                            )

                            if (
                                host.isNotBlank()
                            ) {
                                put(
                                    "headers",
                                    JSONObject().apply {
                                        put(
                                            "Host",
                                            host
                                        )
                                    }
                                )
                            }

                            if (
                                path.isNotBlank()
                            ) {
                                put(
                                    "path",
                                    if (
                                        path.startsWith("/")
                                    ) {
                                        path
                                    } else {
                                        "/$path"
                                    }
                                )
                            }
                        }
                    )
                }

                "grpc" -> {
                    outbound.put(
                        "transport",
                        JSONObject().apply {
                            put(
                                "type",
                                "grpc"
                            )

                            if (
                                path.isNotBlank()
                            ) {
                                put(
                                    "service_name",
                                    path
                                )
                            }
                        }
                    )
                }
            }

            outbound
        } catch (_: Exception) {
            null
        }
    }
        private fun parseTrojan(
        link: String
    ): JSONObject? {
        return try {
            val withoutScheme =
                link.substringAfter(
                    "trojan://"
                )

            val fragment =
                withoutScheme.substringAfter(
                    "#",
                    ""
                )

            val beforeFragment =
                withoutScheme.substringBefore(
                    "#"
                )

            val query =
                beforeFragment.substringAfter(
                    "?",
                    ""
                )

            val authority =
                beforeFragment.substringBefore(
                    "?"
                )

            val password =
                authority.substringBefore("@")

            val serverPort =
                authority.substringAfter("@")

            val server =
                serverPort.substringBeforeLast(
                    ":"
                )

            val port =
                serverPort
                    .substringAfterLast(
                        ":"
                    )
                    .toIntOrNull()
                    ?: 443

            val params =
                parseQueryParameters(
                    query
                )

            JSONObject().apply {
                put(
                    "type",
                    "trojan"
                )

                put(
                    "tag",
                    cleanNodeName(
                        decodeUrlComponent(
                            fragment.ifBlank {
                                "$server:$port"
                            }
                        )
                    )
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
                    "password",
                    decodeUrlComponent(
                        password
                    )
                )

                buildTls(
                    this,
                    params
                )

                buildTransport(
                    this,
                    params
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseShadowsocks(
        link: String
    ): JSONObject? {
        return try {
            val withoutScheme =
                link.substringAfter(
                    "ss://"
                )

            val fragment =
                withoutScheme.substringAfter(
                    "#",
                    ""
                )

            val beforeFragment =
                withoutScheme.substringBefore(
                    "#"
                )

            val query =
                beforeFragment.substringAfter(
                    "?",
                    ""
                )

            val authority =
                beforeFragment.substringBefore(
                    "?"
                )

            val decodedAuthority =
                if (
                    authority.contains("@")
                ) {
                    authority
                } else {
                    try {
                        String(
                            Base64.decode(
                                authority,
                                Base64.DEFAULT
                            ),
                            StandardCharsets.UTF_8
                        )
                    } catch (_: Exception) {
                        authority
                    }
                }

            val userInfo =
                decodedAuthority.substringBefore("@")

            val serverPort =
                decodedAuthority.substringAfter(
                    "@"
                )

            val methodPassword =
                userInfo.split(
                    ":",
                    limit = 2
                )

            if (
                methodPassword.size < 2
            ) {
                return null
            }

            val server =
                serverPort.substringBeforeLast(
                    ":"
                )

            val port =
                serverPort
                    .substringAfterLast(
                        ":"
                    )
                    .toIntOrNull()
                    ?: return null

            val params =
                parseQueryParameters(
                    query
                )

            JSONObject().apply {
                put(
                    "type",
                    "shadowsocks"
                )

                put(
                    "tag",
                    cleanNodeName(
                        decodeUrlComponent(
                            fragment.ifBlank {
                                "$server:$port"
                            }
                        )
                    )
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
                    decodeUrlComponent(
                        methodPassword[0]
                    )
                )

                put(
                    "password",
                    decodeUrlComponent(
                        methodPassword[1]
                    )
                )

                params["plugin"]
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?.let {
                        put(
                            "plugin",
                            decodeUrlComponent(
                                it
                            )
                        )
                    }

                params["plugin-opts"]
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?.let {
                        put(
                            "plugin_opts",
                            decodeUrlComponent(
                                it
                            )
                        )
                    }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseHysteria2(
        link: String
    ): JSONObject? {
        return try {
            val normalized =
                link.replaceFirst(
                    "hy2://",
                    "hysteria2://"
                )

            val withoutScheme =
                normalized.substringAfter(
                    "hysteria2://"
                )

            val fragment =
                withoutScheme.substringAfter(
                    "#",
                    ""
                )

            val beforeFragment =
                withoutScheme.substringBefore(
                    "#"
                )

            val query =
                beforeFragment.substringAfter(
                    "?",
                    ""
                )

            val authority =
                beforeFragment.substringBefore(
                    "?"
                )

            val password =
                authority.substringBefore("@")

            val serverPort =
                authority.substringAfter("@")

            val server =
                serverPort.substringBeforeLast(
                    ":"
                )

            val port =
                serverPort
                    .substringAfterLast(
                        ":"
                    )
                    .toIntOrNull()
                    ?: 443

            val params =
                parseQueryParameters(
                    query
                )

            JSONObject().apply {
                put(
                    "type",
                    "hysteria2"
                )

                put(
                    "tag",
                    cleanNodeName(
                        decodeUrlComponent(
                            fragment.ifBlank {
                                "$server:$port"
                            }
                        )
                    )
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
                    "password",
                    decodeUrlComponent(
                        password
                    )
                )

                params["obfs"]
                    ?.takeIf {
                        it.equals(
                            "salamander",
                            ignoreCase = true
                        )
                    }
                    ?.let {
                        put(
                            "obfs",
                            JSONObject().apply {
                                put(
                                    "type",
                                    "salamander"
                                )

                                params["obfs-password"]
                                    ?.let { passwordValue ->
                                        put(
                                            "password",
                                            decodeUrlComponent(
                                                passwordValue
                                            )
                                        )
                                    }
                            }
                        )
                    }

                val upMbps =
                    params["upmbps"]
                        ?.toIntOrNull()

                if (upMbps != null) {
                    put(
                        "up_mbps",
                        upMbps
                    )
                }

                val downMbps =
                    params["downmbps"]
                        ?.toIntOrNull()

                if (downMbps != null) {
                    put(
                        "down_mbps",
                        downMbps
                    )
                }

                buildTls(
                    this,
                    params,
                    true
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseHysteria(
        link: String
    ): JSONObject? {
        return try {
            val withoutScheme =
                link.substringAfter(
                    "hysteria://"
                )

            val fragment =
                withoutScheme.substringAfter(
                    "#",
                    ""
                )

            val beforeFragment =
                withoutScheme.substringBefore(
                    "#"
                )

            val query =
                beforeFragment.substringAfter(
                    "?",
                    ""
                )

            val authority =
                beforeFragment.substringBefore(
                    "?"
                )

            val server =
                authority.substringBeforeLast(
                    ":"
                )

            val port =
                authority
                    .substringAfterLast(
                        ":"
                    )
                    .toIntOrNull()
                    ?: 443

            val params =
                parseQueryParameters(
                    query
                )

            JSONObject().apply {
                put(
                    "type",
                    "hysteria"
                )

                put(
                    "tag",
                    cleanNodeName(
                        decodeUrlComponent(
                            fragment.ifBlank {
                                "$server:$port"
                            }
                        )
                    )
                )

                put(
                    "server",
                    server
                )

                put(
                    "server_port",
                    port
                )

                params["auth"]
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?.let {
                        put(
                            "auth",
                            decodeUrlComponent(
                                it
                            )
                        )
                    }

                params["auth_str"]
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?.let {
                        put(
                            "auth_str",
                            decodeUrlComponent(
                                it
                            )
                        )
                    }

                params["upmbps"]
                    ?.toIntOrNull()
                    ?.let {
                        put(
                            "up_mbps",
                            it
                        )
                    }

                params["downmbps"]
                    ?.toIntOrNull()
                    ?.let {
                        put(
                            "down_mbps",
                            it
                        )
                    }

                buildTls(
                    this,
                    params,
                    true
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun buildTls(
        outbound: JSONObject,
        params: Map<String, String>,
        forceEnabled: Boolean = false
    ) {
        val security =
            params["security"]
                ?.let {
                    decodeUrlComponent(
                        it
                    )
                }
                ?.lowercase()

        val enabled =
            forceEnabled ||
                security == "tls" ||
                security == "reality"

        if (!enabled) {
            return
        }

        val tls =
            JSONObject().apply {
                put(
                    "enabled",
                    true
                )

                params["sni"]
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?.let {
                        put(
                            "server_name",
                            decodeUrlComponent(
                                it
                            )
                        )
                    }

                params["allowInsecure"]
                    ?.lowercase()
                    ?.let {
                        if (
                            it == "1" ||
                            it == "true"
                        ) {
                            put(
                                "insecure",
                                true
                            )
                        }
                    }
            }

        if (
            security == "reality"
        ) {
            tls.put(
                "reality",
                JSONObject().apply {
                    put(
                        "enabled",
                        true
                    )

                    params["pbk"]
                        ?.takeIf {
                            it.isNotBlank()
                        }
                        ?.let {
                            put(
                                "public_key",
                                decodeUrlComponent(
                                    it
                                )
                            )
                        }

                    params["sid"]
                        ?.takeIf {
                            it.isNotBlank()
                        }
                        ?.let {
                            put(
                                "short_id",
                                decodeUrlComponent(
                                    it
                                )
                            )
                        }
                }
            )
        }

        val fingerprint =
            params["fp"]
                ?.takeIf {
                    it.isNotBlank()
                }

        if (
            fingerprint != null
        ) {
            tls.put(
                "utls",
                JSONObject().apply {
                    put(
                        "enabled",
                        true
                    )

                    put(
                        "fingerprint",
                        decodeUrlComponent(
                            fingerprint
                        )
                    )
                }
            )
        }

        outbound.put(
            "tls",
            tls
        )
    }
        private fun buildTransport(
        outbound: JSONObject,
        params: Map<String, String>
    ) {
        val type =
            params["type"]
                ?.let {
                    decodeUrlComponent(it)
                }
                ?.lowercase()
                ?.takeIf {
                    it.isNotBlank() &&
                        it != "tcp"
                }
                ?: return

        val transport =
            JSONObject().apply {
                put(
                    "type",
                    type
                )
            }

        when (type) {
            "ws" -> {
                params["path"]?.let {
                    val path =
                        decodeUrlComponent(it)

                    transport.put(
                        "path",
                        if (
                            path.startsWith("/")
                        ) {
                            path
                        } else {
                            "/$path"
                        }
                    )
                }

                params["host"]?.let {
                    val host =
                        decodeUrlComponent(it)

                    if (
                        host.isNotBlank()
                    ) {
                        transport.put(
                            "headers",
                            JSONObject().apply {
                                put(
                                    "Host",
                                    host
                                )
                            }
                        )
                    }
                }

                params["early_data_header_name"]
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?.let {
                        transport.put(
                            "early_data_header_name",
                            decodeUrlComponent(it)
                        )
                    }

                params["max_early_data"]
                    ?.toIntOrNull()
                    ?.let {
                        transport.put(
                            "max_early_data",
                            it
                        )
                    }
            }

            "grpc" -> {
                params["serviceName"]?.let {
                    transport.put(
                        "service_name",
                        decodeUrlComponent(
                            it
                        )
                    )
                }

                params["mode"]?.let {
                    val mode =
                        decodeUrlComponent(it)

                    if (
                        mode.equals(
                            "multi",
                            ignoreCase = true
                        )
                    ) {
                        transport.put(
                            "mode",
                            "multi"
                        )
                    }
                }
            }

            "http" -> {
                params["host"]?.let {
                    val host =
                        decodeUrlComponent(it)

                    if (
                        host.isNotBlank()
                    ) {
                        transport.put(
                            "host",
                            JSONArray().apply {
                                host.split(",")
                                    .map {
                                        it.trim()
                                    }
                                    .filter {
                                        it.isNotBlank()
                                    }
                                    .forEach {
                                        put(it)
                                    }
                            }
                        )
                    }
                }

                params["path"]?.let {
                    val path =
                        decodeUrlComponent(it)

                    if (
                        path.isNotBlank()
                    ) {
                        transport.put(
                            "path",
                            if (
                                path.startsWith("/")
                            ) {
                                path
                            } else {
                                "/$path"
                            }
                        )
                    }
                }

                params["method"]?.let {
                    transport.put(
                        "method",
                        decodeUrlComponent(it)
                    )
                }
            }

            "httpupgrade" -> {
                params["path"]?.let {
                    val path =
                        decodeUrlComponent(it)

                    transport.put(
                        "path",
                        if (
                            path.startsWith("/")
                        ) {
                            path
                        } else {
                            "/$path"
                        }
                    )
                }

                params["host"]?.let {
                    transport.put(
                        "host",
                        decodeUrlComponent(it)
                    )
                }
            }

            "quic" -> {
                params["quicSecurity"]?.let {
                    transport.put(
                        "security",
                        decodeUrlComponent(it)
                    )
                }

                params["key"]?.let {
                    transport.put(
                        "key",
                        decodeUrlComponent(it)
                    )
                }

                params["headerType"]?.let {
                    val headerType =
                        decodeUrlComponent(it)

                    if (
                        headerType.isNotBlank() &&
                        headerType != "none"
                    ) {
                        transport.put(
                            "headers",
                            JSONObject().apply {
                                put(
                                    "type",
                                    headerType
                                )
                            }
                        )
                    }
                }
            }

            "kcp" -> {
                params["seed"]?.let {
                    transport.put(
                        "seed",
                        decodeUrlComponent(it)
                    )
                }

                params["headerType"]?.let {
                    val headerType =
                        decodeUrlComponent(it)

                    if (
                        headerType.isNotBlank() &&
                        headerType != "none"
                    ) {
                        transport.put(
                            "headers",
                            JSONObject().apply {
                                put(
                                    "type",
                                    headerType
                                )
                            }
                        )
                    }
                }
            }
        }

        outbound.put(
            "transport",
            transport
        )
    }

    private fun completeSubscriptionConfig(
        root: JSONObject,
        routingMode: SubscriptionRouting.Mode
    ): String {
        migrateInbounds(root)
        migrateOutbounds(root)
        migrateRoute(root)

        val outbounds =
            root.optJSONArray(
                "outbounds"
            ) ?: JSONArray()

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

            val tag =
                outbound.optString("tag")

            if (
                tag.isBlank()
            ) {
                continue
            }

            if (
                type in listOf(
                    "direct",
                    "block",
                    "dns",
                    "selector",
                    "urltest"
                )
            ) {
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
                SubscriptionRouting.WHITELIST_SELECTOR_TAG,
                whitelistTags
            )
        }

        ensureSpecialOutbounds(root)

        SubscriptionRouting.apply(
            root,
            routingMode
        )

        ensureRouteActions(root)

        return root.toString(2)
    }

    private fun rebuildSelector(
        root: JSONObject,
        selectorTag: String,
        members: List<String>
    ) {
        if (
            members.isEmpty()
        ) {
            return
        }

        val outbounds =
            root.optJSONArray(
                "outbounds"
            ) ?: JSONArray()

        val rebuilt =
            JSONArray()

        var existingSelector: JSONObject? =
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
                existingSelector =
                    outbound

                continue
            }

            rebuilt.put(
                outbound
            )
        }

        val selector =
            existingSelector
                ?: JSONObject()

        selector.put(
            "type",
            "selector"
        )

        selector.put(
            "tag",
            selectorTag
        )

        val uniqueMembers =
            members
                .filter {
                    it.isNotBlank()
                }
                .distinct()

        selector.put(
            "outbounds",
            JSONArray().apply {
                uniqueMembers.forEach {
                    put(it)
                }
            }
        )

        val defaultTag =
            selector.optString(
                "default"
            )

        if (
            defaultTag.isBlank() ||
            defaultTag !in uniqueMembers
        ) {
            selector.put(
                "default",
                uniqueMembers.first()
            )
        }

        rebuilt.put(
            selector
        )

        root.put(
            "outbounds",
            rebuilt
        )
    }

    private fun ensureSpecialOutbounds(
        root: JSONObject
    ) {
        val outbounds =
            root.optJSONArray(
                "outbounds"
            ) ?: JSONArray()

        val tags =
            mutableSetOf<String>()

        for (
            i in 0 until
                outbounds.length()
        ) {
            outbounds
                .optJSONObject(i)
                ?.optString("tag")
                ?.takeIf {
                    it.isNotBlank()
                }
                ?.let {
                    tags.add(it)
                }
        }

        if (
            "direct" !in tags
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
            "block" !in tags
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

        root.put(
            "outbounds",
            outbounds
        )
    }

    private fun ensureRouteActions(
        root: JSONObject
    ) {
        val route =
            root.optJSONObject(
                "route"
            ) ?: return

        val rules =
            route.optJSONArray(
                "rules"
            ) ?: return

        for (
            i in 0 until
                rules.length()
        ) {
            val rule =
                rules.optJSONObject(i)
                    ?: continue

            if (
                rule.has("outbound") &&
                !rule.has("action")
            ) {
                val outbound =
                    rule.optString(
                        "outbound"
                    )

                if (
                    outbound.isNotBlank()
                ) {
                    rule.put(
                        "action",
                        "route"
                    )
                }
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
            }
        }
    }
        private fun buildSubscriptionConfig(
        content: String,
        routingMode: SubscriptionRouting.Mode
    ): String? {
        return try {
            val root =
                JSONObject(content)

            completeSubscriptionConfig(
                root,
                routingMode
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun tryDecodeBase64(
        value: String
    ): String {
        if (
            value.isBlank()
        ) {
            return value
        }

        val normalized =
            value
                .replace("\n", "")
                .replace("\r", "")
                .replace(" ", "")

        return try {
            val decoded =
                Base64.decode(
                    normalized,
                    Base64.DEFAULT
                )

            val result =
                String(
                    decoded,
                    StandardCharsets.UTF_8
                ).trim()

            if (
                result.isBlank()
            ) {
                value
            } else {
                result
            }
        } catch (_: Exception) {
            try {
                val padded =
                    normalized +
                        "=".repeat(
                            (4 -
                                normalized.length % 4) % 4
                        )

                val decoded =
                    Base64.decode(
                        padded,
                        Base64.URL_SAFE or
                            Base64.NO_WRAP
                    )

                String(
                    decoded,
                    StandardCharsets.UTF_8
                ).trim()
            } catch (_: Exception) {
                value
            }
        }
    }

    private fun parseQueryParameters(
        query: String
    ): Map<String, String> {
        if (
            query.isBlank()
        ) {
            return emptyMap()
        }

        val result =
            linkedMapOf<String, String>()

        query.split("&")
            .forEach { item ->
                if (
                    item.isBlank()
                ) {
                    return@forEach
                }

                val key =
                    item.substringBefore(
                        "="
                    )

                val value =
                    item.substringAfter(
                        "=",
                        ""
                    )

                if (
                    key.isNotBlank()
                ) {
                    result[
                        decodeUrlComponent(
                            key
                        )
                    ] =
                        value
                }
            }

        return result
    }

    private fun decodeUrlComponent(
        value: String
    ): String {
        return try {
            URLDecoder.decode(
                value,
                StandardCharsets.UTF_8.name()
            )
        } catch (_: Exception) {
            value
        }
    }

    private fun cleanNodeName(
        value: String
    ): String {
        val decoded =
            decodeUrlComponent(
                value
            )
                .replace("\r", " ")
                .replace("\n", " ")
                .trim()

        if (
            decoded.isBlank()
        ) {
            return "Proxy"
        }

        return decoded
            .replace(
                Regex("\\s+"),
                " "
            )
            .take(128)
    }

    override fun close() {
        try {
            client.close()
        } catch (_: Exception) {
        }
    }
}
