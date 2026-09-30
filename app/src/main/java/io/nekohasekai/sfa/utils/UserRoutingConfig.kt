package io.nekohasekai.sfa.utils

import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.models.DnsConfig
import io.nekohasekai.sfa.models.GeoFileSources
import io.nekohasekai.sfa.models.RoutingRule
import org.json.JSONArray
import org.json.JSONObject

object UserRoutingConfig {

    fun applyToConfig(jsonStr: String): String {
        val raw = runCatching { Settings.routingConfigJson }.getOrNull().orEmpty()
        if (raw.isBlank()) {
            return jsonStr
        }
        return try {
            val root = JSONObject(jsonStr)
            val user = JSONObject(raw)
            applyDns(root, user.optJSONObject("dns"))
            applyGeo(root, user.optJSONObject("geo"))
            applyRules(root, user.optJSONArray("rules"))
            root.toString(2)
        } catch (_: Exception) {
            jsonStr
        }
    }

    private fun applyDns(root: JSONObject, dnsUser: JSONObject?) {
        if (dnsUser == null) {
            return
        }

        val dns = root.optJSONObject("dns") ?: JSONObject().also { root.put("dns", it) }

        val strategyName = dnsUser.optString("strategy", DnsConfig.Strategy.AUTO.name)
        val strategy = runCatching { DnsConfig.Strategy.valueOf(strategyName) }.getOrDefault(DnsConfig.Strategy.AUTO)
        when (strategy) {
            DnsConfig.Strategy.AUTO -> dns.remove("strategy")
            DnsConfig.Strategy.PREFER_IPV4 -> dns.put("strategy", "prefer_ipv4")
            DnsConfig.Strategy.PREFER_IPV6 -> dns.put("strategy", "prefer_ipv6")
            DnsConfig.Strategy.IPV4_ONLY -> dns.put("strategy", "ipv4_only")
            DnsConfig.Strategy.IPV6_ONLY -> dns.put("strategy", "ipv6_only")
        }

        if (dnsUser.has("cacheEnabled")) {
            val cacheEnabled = dnsUser.optBoolean("cacheEnabled", true)
            if (cacheEnabled) {
                dns.remove("disable_cache")
            } else {
                dns.put("disable_cache", true)
            }
        }

        if (dnsUser.has("independentCache")) {
            if (dnsUser.optBoolean("independentCache", false)) {
                dns.put("independent_cache", true)
            } else {
                dns.remove("independent_cache")
            }
        }

        if (dnsUser.has("reverseMapping")) {
            if (dnsUser.optBoolean("reverseMapping", false)) {
                dns.put("reverse_mapping", true)
            } else {
                dns.remove("reverse_mapping")
            }
        }

        val finalServer = dnsUser.optString("finalServer", "").trim()
        if (finalServer.isNotEmpty()) {
            dns.put("final", finalServer)
        }

        val serversUser = dnsUser.optJSONArray("servers")
        if (serversUser != null && serversUser.length() > 0) {
            val servers = JSONArray()
            for (i in 0 until serversUser.length()) {
                val item = serversUser.optJSONObject(i) ?: continue
                val converted = convertDnsServer(item) ?: continue
                servers.put(converted)
            }
            if (servers.length() > 0) {
                dns.put("servers", servers)
            }
        }
    }

    private fun convertDnsServer(item: JSONObject): JSONObject? {
        val tag = item.optString("tag").trim()
        val address = item.optString("address").trim()
        if (tag.isEmpty() || address.isEmpty()) {
            return null
        }
        val server = JSONObject()
        server.put("tag", tag)

        val detour = item.optString("detour").trim()
        if (detour.isNotEmpty() && detour != "direct") {
            server.put("detour", detour)
        }

        when {
            address == "local" || address.startsWith("rcode://") -> {
                server.put("type", "local")
            }
            address.startsWith("https://") -> {
                server.put("type", "https")
                val clean = address.removePrefix("https://")
                val host = clean.substringBefore("/").substringBefore(":")
                val path = if (clean.contains("/")) "/" + clean.substringAfter("/") else "/dns-query"
                server.put("server", host)
                server.put("path", path)
            }
            address.startsWith("tls://") -> {
                server.put("type", "tls")
                server.put("server", address.removePrefix("tls://").substringBefore("/").substringBefore(":"))
            }
            address.startsWith("quic://") -> {
                server.put("type", "quic")
                server.put("server", address.removePrefix("quic://").substringBefore("/").substringBefore(":"))
            }
            address.startsWith("h3://") -> {
                server.put("type", "h3")
                val clean = address.removePrefix("h3://")
                server.put("server", clean.substringBefore("/").substringBefore(":"))
            }
            address.startsWith("udp://") -> {
                server.put("type", "udp")
                server.put("server", address.removePrefix("udp://").substringBefore(":"))
            }
            address.startsWith("tcp://") -> {
                server.put("type", "tcp")
                server.put("server", address.removePrefix("tcp://").substringBefore(":"))
            }
            else -> {
                server.put("type", "udp")
                server.put("server", address.substringBefore(":"))
            }
        }
        return server
    }

    private fun applyGeo(root: JSONObject, geoUser: JSONObject?) {
        if (geoUser == null) {
            return
        }
        val sourceId = geoUser.optString("sourceId", "").trim()
        val customGeosite = geoUser.optString("geositeUrl", "").trim()
        val customGeoip = geoUser.optString("geoipUrl", "").trim()

        val source = if (sourceId.isNotEmpty()) GeoFileSources.find(sourceId) else null
        val geositeBase = customGeosite.ifBlank { source?.geosite_url?.trim().orEmpty() }.trimEnd('/')
        val geoipBase = customGeoip.ifBlank { source?.geoip_url?.trim().orEmpty() }.trimEnd('/')
        if (geositeBase.isEmpty() && geoipBase.isEmpty()) {
            return
        }

        val route = root.optJSONObject("route") ?: JSONObject().also { root.put("route", it) }
        val ruleSet = route.optJSONArray("rule_set") ?: JSONArray().also { route.put("rule_set", it) }

        for (i in 0 until ruleSet.length()) {
            val item = ruleSet.optJSONObject(i) ?: continue
            val tag = item.optString("tag")
            when {
                tag.startsWith("geosite") && geositeBase.isNotEmpty() -> {
                    item.put("url", "$geositeBase/$tag.srs")
                }
                tag.startsWith("geoip") && geoipBase.isNotEmpty() -> {
                    item.put("url", "$geoipBase/$tag.srs")
                }
            }
        }
    }

    private fun applyRules(root: JSONObject, rulesUser: JSONArray?) {
        if (rulesUser == null || rulesUser.length() == 0) {
            return
        }

        val route = root.optJSONObject("route") ?: JSONObject().also { root.put("route", it) }
        val existing = route.optJSONArray("rules") ?: JSONArray()
        val merged = JSONArray()

        var needBlock = false
        val dnsRulesExtra = JSONArray()
        for (i in 0 until rulesUser.length()) {
            val item = rulesUser.optJSONObject(i) ?: continue
            val rule = buildSingBoxRule(item) ?: continue
            if (rule.optString("outbound") == "block") {
                needBlock = true
            }
            merged.put(rule)
            if (item.optBoolean("dnsRule", false)) {
                buildDnsRuleFromRoute(item)?.let { dnsRulesExtra.put(it) }
            }
        }

        for (i in 0 until existing.length()) {
            merged.put(existing.get(i))
        }
        route.put("rules", merged)

        if (needBlock) {
            ensureBlockOutbound(root)
        }
        if (dnsRulesExtra.length() > 0) {
            mergeDnsRules(root, dnsRulesExtra)
        }
    }

    private fun buildDnsRuleFromRoute(item: JSONObject): JSONObject? {
        val dnsRule = JSONObject()
        var hasMatch = false
        fun putList(key: String, raw: String) {
            val values = splitValues(raw)
            if (values.isEmpty()) return
            val arr = JSONArray()
            values.forEach { arr.put(it) }
            dnsRule.put(key, arr)
            hasMatch = true
        }
        putList("domain", item.optString("domain"))
        putList("domain_suffix", item.optString("domainSuffix"))
        putList("domain_keyword", item.optString("domainKeyword"))
        putList("rule_set", item.optString("ruleSet"))
        if (!hasMatch) {
            val value = item.optString("value").trim()
            if (value.isEmpty()) return null
            when (
                runCatching { RoutingRule.Type.valueOf(item.optString("type", RoutingRule.Type.DOMAIN.name)) }
                    .getOrDefault(RoutingRule.Type.DOMAIN)
            ) {
                RoutingRule.Type.DOMAIN -> dnsRule.put("domain", JSONArray().put(value))
                RoutingRule.Type.DOMAIN_SUFFIX -> dnsRule.put("domain_suffix", JSONArray().put(value))
                RoutingRule.Type.DOMAIN_KEYWORD -> dnsRule.put("domain_keyword", JSONArray().put(value))
                else -> return null
            }
        }
        val outbound = mapOutbound(item.optString("outbound", RoutingRule.OUTBOUND_PROXY))
        val server = when (outbound) {
            "direct", "block" -> "dns-direct"
            else -> "dns-proxy"
        }
        dnsRule.put("server", server)
        return dnsRule
    }

    private fun mergeDnsRules(root: JSONObject, extra: JSONArray) {
        val dns = root.optJSONObject("dns") ?: JSONObject().also { root.put("dns", it) }
        val existing = dns.optJSONArray("rules") ?: JSONArray()
        val merged = JSONArray()
        for (i in 0 until extra.length()) {
            merged.put(extra.get(i))
        }
        for (i in 0 until existing.length()) {
            merged.put(existing.get(i))
        }
        dns.put("rules", merged)
    }

    private fun buildSingBoxRule(item: JSONObject): JSONObject? {
        val rule = JSONObject()
        var hasMatch = false

        fun putStringList(key: String, raw: String) {
            val values = splitValues(raw)
            if (values.isEmpty()) return
            val arr = JSONArray()
            values.forEach { arr.put(it) }
            rule.put(key, arr)
            hasMatch = true
        }

        putStringList("domain", item.optString("domain"))
        putStringList("domain_suffix", item.optString("domainSuffix"))
        putStringList("domain_keyword", item.optString("domainKeyword"))
        putStringList("ip_cidr", item.optString("ipCidr"))
        putStringList("source_ip_cidr", item.optString("sourceIpCidr"))
        putStringList("package_name", item.optString("packageName"))
        putStringList("rule_set", item.optString("ruleSet"))
        putStringList("wifi_ssid", item.optString("wifiSsid"))
        putStringList("wifi_bssid", item.optString("wifiBssid"))

        val port = item.optString("port").trim()
        if (port.isNotEmpty()) {
            rule.put("port", port)
            hasMatch = true
        }
        val sourcePort = item.optString("sourcePort").trim()
        if (sourcePort.isNotEmpty()) {
            rule.put("source_port", sourcePort)
            hasMatch = true
        }

        val network = item.optString("network").trim()
        if (network.isNotEmpty() && network != "tcp,udp" && !network.equals("TCP и UDP", true)) {
            val nets = splitValues(network.replace("и", ",").lowercase())
                .map { it.trim() }
                .filter { it == "tcp" || it == "udp" }
            if (nets.size == 1) {
                rule.put("network", nets[0])
                hasMatch = true
            }
        }

        val protocol = item.optString("protocol").trim()
        if (protocol.isNotEmpty()) {
            putStringList("protocol", protocol)
        }

        val clashMode = item.optString("clashMode").trim()
        if (clashMode.isNotEmpty()) {
            rule.put("clash_mode", clashMode)
            hasMatch = true
        }

        if (!hasMatch) {
            val typeName = item.optString("type", RoutingRule.Type.DOMAIN.name)
            val type = runCatching { RoutingRule.Type.valueOf(typeName) }.getOrDefault(RoutingRule.Type.DOMAIN)
            val value = item.optString("value").trim()
            if (value.isEmpty()) {
                return null
            }
            when (type) {
                RoutingRule.Type.DOMAIN -> rule.put("domain", JSONArray().put(value))
                RoutingRule.Type.DOMAIN_SUFFIX -> rule.put("domain_suffix", JSONArray().put(value))
                RoutingRule.Type.DOMAIN_KEYWORD -> rule.put("domain_keyword", JSONArray().put(value))
                RoutingRule.Type.GEOSITE -> rule.put("geosite", JSONArray().put(value))
                RoutingRule.Type.IP_CIDR -> rule.put("ip_cidr", JSONArray().put(value))
                RoutingRule.Type.GEOIP -> rule.put("geoip", JSONArray().put(value))
                RoutingRule.Type.PACKAGE_NAME -> rule.put("package_name", JSONArray().put(value))
                RoutingRule.Type.PROTOCOL -> rule.put("protocol", JSONArray().put(value))
            }
            hasMatch = true
        }

        if (!hasMatch) {
            return null
        }

        val outboundRaw = item.optString("outbound", RoutingRule.OUTBOUND_PROXY).trim()
        rule.put("outbound", mapOutbound(outboundRaw))
        return rule
    }

    private fun splitValues(raw: String): List<String> {
        return raw.split(',', '\n', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun mapOutbound(raw: String): String {
        return when (raw.lowercase()) {
            RoutingRule.OUTBOUND_DIRECT, "direct" -> "direct"
            RoutingRule.OUTBOUND_BLOCK, "block", "reject" -> "block"
            RoutingRule.OUTBOUND_PROXY, "proxy" -> SubscriptionRouting.NORMAL_SELECTOR_TAG
            else -> raw.ifBlank { "direct" }
        }
    }

    private fun ensureBlockOutbound(root: JSONObject) {
        val outbounds = root.optJSONArray("outbounds") ?: JSONArray().also { root.put("outbounds", it) }
        for (i in 0 until outbounds.length()) {
            if (outbounds.optJSONObject(i)?.optString("tag") == "block") {
                return
            }
        }
        outbounds.put(
            JSONObject()
                .put("type", "block")
                .put("tag", "block"),
        )
    }
}
