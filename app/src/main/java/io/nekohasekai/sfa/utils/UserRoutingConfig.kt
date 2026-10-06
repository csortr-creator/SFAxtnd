package io.nekohasekai.sfa.utils

import io.nekohasekai.sfa.models.DnsConfig
import io.nekohasekai.sfa.models.GeoFileSources
import io.nekohasekai.sfa.models.RoutingRule
import org.json.JSONArray
import org.json.JSONObject

object UserRoutingConfig {

    fun applyToConfig(jsonStr: String, userConfigJson: String): String =
        try {
            val root = JSONObject(jsonStr)
            val raw = userConfigJson.trim()
            if (raw.isNotBlank()) {
                val user = JSONObject(raw)
                applyDns(root, user.optJSONObject("dns"))
                applyRules(root, user.optJSONArray("rules"))
                applyGeo(root, user.optJSONObject("geo"))
                ClientSettingsConfig.applyDns(root, user.optJSONObject("dns"))
                ClientSettingsConfig.applyRuleSets(root, user.optJSONArray("ruleSets"))
            }
            finalizeRemoteRuleSets(root)
            root.toString(2)
        } catch (error: Exception) {
            throw IllegalArgumentException(
                "Некорректные настройки маршрутизации или DNS: ${error.message}",
                error,
            )
        }

    private fun applyDns(root: JSONObject, dnsUser: JSONObject?) {
        if (dnsUser == null) return
        val dns = root.optJSONObject("dns") ?: JSONObject().also { root.put("dns", it) }
        val strategyName = dnsUser.optString("strategy", DnsConfig.Strategy.AUTO.name)
        val strategy =
            runCatching { DnsConfig.Strategy.valueOf(strategyName) }
                .getOrDefault(DnsConfig.Strategy.AUTO)
        when (strategy) {
            DnsConfig.Strategy.AUTO -> dns.remove("strategy")
            DnsConfig.Strategy.PREFER_IPV4 -> dns.put("strategy", "prefer_ipv4")
            DnsConfig.Strategy.PREFER_IPV6 -> dns.put("strategy", "prefer_ipv6")
            DnsConfig.Strategy.IPV4_ONLY -> dns.put("strategy", "ipv4_only")
            DnsConfig.Strategy.IPV6_ONLY -> dns.put("strategy", "ipv6_only")
        }
        if (dnsUser.has("cacheEnabled")) {
            if (dnsUser.optBoolean("cacheEnabled", true)) dns.remove("disable_cache")
            else dns.put("disable_cache", true)
        }
        dns.remove("independent_cache")
        if (dnsUser.has("reverseMapping")) {
            if (dnsUser.optBoolean("reverseMapping", false)) dns.put("reverse_mapping", true)
            else dns.remove("reverse_mapping")
        }
        val finalServer = dnsUser.optString("finalServer", "").trim()
        if (finalServer.isNotEmpty()) dns.put("final", finalServer)
        val serversUser = dnsUser.optJSONArray("servers")
        if (serversUser != null && serversUser.length() > 0) {
            val byTag = linkedMapOf<String, JSONObject>()
            val existing = dns.optJSONArray("servers")
            if (existing != null) {
                for (i in 0 until existing.length()) {
                    val item = existing.optJSONObject(i) ?: continue
                    val tag = item.optString("tag").trim()
                    if (tag.isNotEmpty()) byTag[tag] = item
                }
            }
            for (i in 0 until serversUser.length()) {
                val item = serversUser.optJSONObject(i) ?: continue
                val converted = convertDnsServer(item, root) ?: continue
                val tag = converted.optString("tag").trim()
                if (tag.isNotEmpty()) byTag[tag] = converted
            }
            if (byTag.isNotEmpty()) {
                val servers = JSONArray()
                byTag.values.forEach { servers.put(it) }
                dns.put("servers", servers)
            }
        }
        val finalAfter = dns.optString("final", "").trim()
        if (finalAfter.isNotEmpty()) {
            val servers = dns.optJSONArray("servers")
            val tags = buildSet {
                if (servers != null) {
                    for (i in 0 until servers.length()) {
                        val tag = servers.optJSONObject(i)?.optString("tag")?.trim()
                        if (!tag.isNullOrEmpty()) add(tag)
                    }
                }
            }
            if (finalAfter !in tags) dns.remove("final")
        }
    }

    private fun resolveDnsDetour(root: JSONObject, detour: String): String? {
        val raw = detour.trim()
        if (raw.isEmpty()) return null
        when (raw.lowercase()) {
            "direct" -> return null
            "proxy" -> return findProxyOutboundTag(root)
            else -> return if (outboundTagExists(root, raw)) raw else null
        }
    }

    private fun outboundTagExists(root: JSONObject, tag: String): Boolean {
        val outbounds = root.optJSONArray("outbounds") ?: return false
        for (i in 0 until outbounds.length()) {
            if (outbounds.optJSONObject(i)?.optString("tag") == tag) return true
        }
        return false
    }

    private fun findProxyOutboundTag(root: JSONObject): String? {
        val outbounds = root.optJSONArray("outbounds") ?: return null
        for (i in 0 until outbounds.length()) {
            val item = outbounds.optJSONObject(i) ?: continue
            val type = item.optString("type")
            val tag = item.optString("tag").trim()
            if (tag.isNotEmpty() && type in setOf("selector", "urltest")) return tag
        }
        val routeFinal = root.optJSONObject("route")?.optString("final")?.trim().orEmpty()
        if (
            routeFinal.isNotEmpty() &&
                routeFinal != "direct" &&
                routeFinal != "block" &&
                outboundTagExists(root, routeFinal)
        )
            return routeFinal
        for (i in 0 until outbounds.length()) {
            val item = outbounds.optJSONObject(i) ?: continue
            val type = item.optString("type")
            val tag = item.optString("tag").trim()
            if (tag.isEmpty()) continue
            if (type in setOf("direct", "block", "dns")) continue
            return tag
        }
        return null
    }

    private fun convertDnsServer(item: JSONObject, root: JSONObject): JSONObject? {
        val tag = item.optString("tag").trim()
        val address = item.optString("address").trim()
        if (tag.isEmpty() || address.isEmpty()) return null
        val server = JSONObject()
        server.put("tag", tag)
        val resolvedDetour = resolveDnsDetour(root, item.optString("detour").trim())
        if (!resolvedDetour.isNullOrEmpty()) server.put("detour", resolvedDetour)
        when {
            address == "local" || address.startsWith("rcode://") -> server.put("type", "local")
            address.startsWith("https://") -> {
                server.put("type", "https")
                val clean = address.removePrefix("https://")
                server.put("server", clean.substringBefore("/").substringBefore(":"))
                server.put(
                    "path",
                    if (clean.contains("/")) "/" + clean.substringAfter("/") else "/dns-query",
                )
            }
            address.startsWith("tls://") -> {
                server.put("type", "tls")
                server.put(
                    "server",
                    address.removePrefix("tls://").substringBefore("/").substringBefore(":"),
                )
            }
            address.startsWith("quic://") -> {
                server.put("type", "quic")
                server.put(
                    "server",
                    address.removePrefix("quic://").substringBefore("/").substringBefore(":"),
                )
            }
            address.startsWith("h3://") -> {
                server.put("type", "h3")
                server.put(
                    "server",
                    address.removePrefix("h3://").substringBefore("/").substringBefore(":"),
                )
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
        if (geoUser == null) return
        val sourceId = geoUser.optString("sourceId", "").trim()
        val customGeosite = geoUser.optString("geositeUrl", "").trim()
        val customGeoip = geoUser.optString("geoipUrl", "").trim()
        val source = if (sourceId.isNotEmpty()) GeoFileSources.find(sourceId) else null
        val geositeBase =
            customGeosite.ifBlank { source?.geosite_url?.trim().orEmpty() }.trimEnd('/')
        val geoipBase = customGeoip.ifBlank { source?.geoip_url?.trim().orEmpty() }.trimEnd('/')
        if (geositeBase.isEmpty() && geoipBase.isEmpty()) return
        val route = root.optJSONObject("route") ?: JSONObject().also { root.put("route", it) }
        val ruleSet =
            route.optJSONArray("rule_set") ?: JSONArray().also { route.put("rule_set", it) }
        for (i in 0 until ruleSet.length()) {
            val item = ruleSet.optJSONObject(i) ?: continue
            val tag = item.optString("tag")
            when {
                tag.startsWith("geosite") && geositeBase.isNotEmpty() -> {
                    item.put("type", "remote")
                    item.put("format", "binary")
                    item.put("url", "$geositeBase/$tag.srs")
                    item.put("download_detour", "direct")
                }
                tag.startsWith("geoip") && geoipBase.isNotEmpty() -> {
                    item.put("type", "remote")
                    item.put("format", "binary")
                    item.put("url", "$geoipBase/$tag.srs")
                    item.put("download_detour", "direct")
                }
            }
        }
    }

    private fun applyRules(root: JSONObject, rulesUser: JSONArray?) {
        if (rulesUser == null || rulesUser.length() == 0) return
        val route = root.optJSONObject("route") ?: JSONObject().also { root.put("route", it) }
        val existing = route.optJSONArray("rules") ?: JSONArray()
        val merged = JSONArray()
        var needBlock = false
        val dnsRulesExtra = JSONArray()
        val usedRuleSetTags = linkedSetOf<String>()
        for (i in 0 until rulesUser.length()) {
            val item = rulesUser.optJSONObject(i) ?: continue
            if (!item.optBoolean("enabled", true)) continue
            val rule = buildSingBoxRule(item) ?: continue
            if (item.optString("outbound", "proxy") == "proxy")
                rule.put("outbound", findProxyOutboundTag(root))
            if (rule.optString("outbound") == "block") {
                rule.remove("outbound")
                rule.put("action", "reject")
            }
            val target = rule.optString("outbound")
            if (
                target.isNotBlank() &&
                    target !in setOf("direct", "proxy") &&
                    !outboundTagExists(root, target)
            ) {
                rule.put("outbound", findProxyOutboundTag(root))
            }
            merged.put(rule)
            val rs = rule.optJSONArray("rule_set")
            if (rs != null) {
                for (j in 0 until rs.length()) {
                    val tag = rs.optString(j)
                    if (tag.isNotBlank()) usedRuleSetTags.add(tag)
                }
            }
            if (item.optBoolean("dnsRule", false)) {
                buildDnsRuleFromRoute(item, root)?.let { dnsRulesExtra.put(it) }
            }
        }
        val whitelistActive = usedRuleSetTags.any { it in RoutingPresets.remoteRuleSets }
        val combined = JSONArray()
        fun essential(rule: JSONObject): Boolean =
            rule.optString("action") in setOf("sniff", "hijack-dns") ||
                (rule.optBoolean("ip_is_private") && rule.optString("outbound") == "direct") ||
                (rule.optJSONArray("ip_cidr")?.let {
                    it.length() == 1 && it.optString(0) == "::/0"
                } == true &&
                    (rule.optString("outbound") == "block" || rule.optString("action") == "reject"))
        // DNS interception and sniffing must run before a Whitelist catch-all route.
        for (i in 0 until existing.length()) {
            val rule = existing.optJSONObject(i) ?: continue
            if (essential(rule)) combined.put(rule)
        }
        for (i in 0 until merged.length()) combined.put(merged.get(i))
        if (whitelistActive) combined.put(JSONObject().put("outbound", findProxyOutboundTag(root)))
        for (i in 0 until existing.length()) {
            val rule = existing.optJSONObject(i) ?: continue
            if (!essential(rule)) combined.put(rule)
        }
        route.put("rules", combined)
        ensureRuleSetEntries(route, usedRuleSetTags)
        if (needBlock) ensureBlockOutbound(root)
        if (dnsRulesExtra.length() > 0) mergeDnsRules(root, dnsRulesExtra)
    }

    private fun ensureRuleSetEntries(route: JSONObject, tags: Set<String>) {
        if (tags.isEmpty()) return
        val ruleSet =
            route.optJSONArray("rule_set") ?: JSONArray().also { route.put("rule_set", it) }
        val existing =
            (0 until ruleSet.length())
                .mapNotNull {
                    ruleSet.optJSONObject(it)?.optString("tag")?.takeIf { tag -> tag.isNotBlank() }
                }
                .toHashSet()
        val geositeBase = "https://raw.githubusercontent.com/SagerNet/sing-geosite/rule-set"
        val geoipBase = "https://raw.githubusercontent.com/SagerNet/sing-geoip/rule-set"
        for (tag in tags) {
            if (tag in existing) continue
            val customUrl = RoutingPresets.remoteRuleSets[tag]
            val base =
                when {
                    tag.startsWith("geosite") -> geositeBase
                    tag.startsWith("geoip") -> geoipBase
                    else -> if (customUrl == null) continue else ""
                }
            ruleSet.put(
                JSONObject()
                    .put("type", "remote")
                    .put("tag", tag)
                    .put("format", "binary")
                    .put("url", customUrl ?: "$base/$tag.srs")
                    .put("download_detour", "direct")
            )
            existing.add(tag)
        }
    }

    private fun buildDnsRuleFromRoute(item: JSONObject, root: JSONObject): JSONObject? {
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
        putList(
            "rule_set",
            splitValues(item.optString("ruleSet")).map { geoToRuleSet(it) ?: it }.joinToString(","),
        )
        if (!hasMatch) {
            val value = item.optString("value").trim()
            if (value.isEmpty()) return null
            when (
                runCatching {
                        RoutingRule.Type.valueOf(
                            item.optString("type", RoutingRule.Type.DOMAIN.name)
                        )
                    }
                    .getOrDefault(RoutingRule.Type.DOMAIN)
            ) {
                RoutingRule.Type.DOMAIN -> dnsRule.put("domain", JSONArray().put(value))
                RoutingRule.Type.DOMAIN_SUFFIX ->
                    dnsRule.put("domain_suffix", JSONArray().put(value))
                RoutingRule.Type.DOMAIN_KEYWORD ->
                    dnsRule.put("domain_keyword", JSONArray().put(value))
                else -> return null
            }
        }
        val outbound = mapOutbound(item.optString("outbound", RoutingRule.OUTBOUND_PROXY))
        if (outbound == "block") dnsRule.put("action", "reject")
        else dnsRule.put("server", resolveDnsRuleServer(root, outbound))
        return dnsRule
    }

    private fun resolveDnsRuleServer(root: JSONObject, outbound: String): String {
        val tags = linkedSetOf<String>()
        val servers = root.optJSONObject("dns")?.optJSONArray("servers")
        if (servers != null) {
            for (i in 0 until servers.length()) {
                val tag = servers.optJSONObject(i)?.optString("tag")?.trim()
                if (!tag.isNullOrEmpty()) tags.add(tag)
            }
        }
        if (outbound == "direct") {
            for (i in 0 until (servers?.length() ?: 0)) {
                val server = servers?.optJSONObject(i) ?: continue
                if (server.optString("detour") == "direct" || server.optString("type") == "local") {
                    return server.optString("tag")
                }
            }
        }
        val prefer =
            when (outbound) {
                "direct",
                "block" -> listOf("dns-direct", "local")
                else -> listOf("dns-proxy", "dns-remote", "dns-direct")
            }
        for (p in prefer) if (p in tags) return p
        return tags.firstOrNull() ?: "dns-direct"
    }

    private fun mergeDnsRules(root: JSONObject, extra: JSONArray) {
        val dns = root.optJSONObject("dns") ?: JSONObject().also { root.put("dns", it) }
        val existing = dns.optJSONArray("rules") ?: JSONArray()
        val merged = JSONArray()
        for (i in 0 until extra.length()) merged.put(extra.get(i))
        for (i in 0 until existing.length()) merged.put(existing.get(i))
        dns.put("rules", merged)
    }

    private fun buildSingBoxRule(item: JSONObject): JSONObject? {
        val rule = JSONObject()
        var hasMatch = false
        val ruleSetTags = linkedSetOf<String>()
        fun addRuleSet(tag: String) {
            val clean = tag.trim()
            if (clean.isEmpty()) return
            ruleSetTags.add(clean)
            hasMatch = true
        }
        fun putStringList(key: String, raw: String) {
            val values = splitValues(raw)
            if (values.isEmpty()) return
            val arr = JSONArray()
            values.forEach { value ->
                val asRuleSet = geoToRuleSet(value)
                if (asRuleSet != null) {
                    addRuleSet(asRuleSet)
                } else {
                    arr.put(value)
                }
            }
            if (arr.length() > 0) {
                rule.put(key, arr)
                hasMatch = true
            }
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
        fun putPorts(key: String, raw: String) {
            val entries = splitValues(raw)
            val ports =
                entries
                    .filter { !it.contains(':') }
                    .map { it.toInt().also { port -> require(port in 1..65535) } }
            val ranges =
                entries
                    .filter { it.contains(':') }
                    .onEach {
                        val limits = it.split(':')
                        require(limits.size == 2)
                        val start = limits[0].toInt()
                        val end = limits[1].toInt()
                        require(start in 1..65535 && end in start..65535)
                    }
            if (ports.isNotEmpty()) {
                rule.put(key, JSONArray(ports))
                hasMatch = true
            }
            if (ranges.isNotEmpty()) {
                rule.put("${key}_range", JSONArray(ranges))
                hasMatch = true
            }
        }
        putPorts("port", item.optString("port"))
        putPorts("source_port", item.optString("sourcePort"))
        val network = item.optString("network").trim()
        if (network.isNotEmpty() && network != "tcp,udp" && !network.equals("TCP и UDP", true)) {
            val nets = splitValues(network.replace("и", ",").lowercase())
            if (nets.isNotEmpty()) {
                rule.put("network", JSONArray(nets))
                hasMatch = true
            }
        }
        val protocol = item.optString("protocol").trim()
        if (protocol.isNotEmpty()) {
            rule.put("protocol", JSONArray(splitValues(protocol)))
            hasMatch = true
        }
        val clashMode = item.optString("clashMode").trim()
        if (clashMode.isNotEmpty()) {
            rule.put("clash_mode", clashMode)
            hasMatch = true
        }
        val value = item.optString("value").trim()
        if (!hasMatch && value.isNotEmpty()) {
            when (
                runCatching {
                        RoutingRule.Type.valueOf(
                            item.optString("type", RoutingRule.Type.DOMAIN.name)
                        )
                    }
                    .getOrDefault(RoutingRule.Type.DOMAIN)
            ) {
                RoutingRule.Type.DOMAIN -> rule.put("domain", JSONArray().put(value))
                RoutingRule.Type.DOMAIN_SUFFIX -> rule.put("domain_suffix", JSONArray().put(value))
                RoutingRule.Type.DOMAIN_KEYWORD ->
                    rule.put("domain_keyword", JSONArray().put(value))
                RoutingRule.Type.GEOSITE ->
                    addRuleSet(geoToRuleSet(value) ?: ("geosite-" + value.removePrefix("geosite-")))
                RoutingRule.Type.IP_CIDR -> {
                    val asRuleSet = geoToRuleSet(value)
                    if (asRuleSet != null) {
                        addRuleSet(asRuleSet)
                    } else if (value.contains('/')) {
                        rule.put("ip_cidr", JSONArray().put(value))
                    }
                }
                RoutingRule.Type.GEOIP ->
                    addRuleSet(geoToRuleSet(value) ?: ("geoip-" + value.removePrefix("geoip-")))
                RoutingRule.Type.PACKAGE_NAME -> rule.put("package_name", JSONArray().put(value))
                RoutingRule.Type.PROTOCOL -> rule.put("protocol", JSONArray().put(value))
            }
            hasMatch = true
        }
        if (ruleSetTags.isNotEmpty()) {
            val arr = JSONArray()
            ruleSetTags.forEach { arr.put(it) }
            rule.put("rule_set", arr)
        }
        if (!hasMatch && ruleSetTags.isEmpty()) return null
        rule.put(
            "outbound",
            mapOutbound(item.optString("outbound", RoutingRule.OUTBOUND_PROXY).trim()),
        )
        return rule
    }

    private fun geoToRuleSet(raw: String): String? {
        val v = raw.trim()
        if (v.isEmpty()) return null
        return when {
            v.startsWith("geoip:", ignoreCase = true) -> "geoip-" + v.substringAfter(':').trim()
            v.startsWith("geosite:", ignoreCase = true) -> "geosite-" + v.substringAfter(':').trim()
            v.startsWith("geoip-", ignoreCase = true) ||
                v.startsWith("geosite-", ignoreCase = true) -> v
            else -> null
        }
    }

    private fun splitValues(raw: String): List<String> =
        raw.split(',', '\n', ';').map { it.trim() }.filter { it.isNotEmpty() }

    private fun mapOutbound(raw: String): String =
        when (raw.lowercase()) {
            RoutingRule.OUTBOUND_DIRECT,
            "direct" -> "direct"
            RoutingRule.OUTBOUND_BLOCK,
            "block",
            "reject" -> "block"
            RoutingRule.OUTBOUND_PROXY,
            "proxy" -> "proxy"
            else -> raw.ifBlank { "direct" }
        }

    private fun finalizeRemoteRuleSets(root: JSONObject) {
        ensureDirectOutbound(root)
        val route = root.optJSONObject("route") ?: return
        val ruleSet = route.optJSONArray("rule_set") ?: return
        for (i in 0 until ruleSet.length()) {
            val item = ruleSet.optJSONObject(i) ?: continue
            val type = item.optString("type")
            val url = item.optString("url").trim()
            if (type == "remote" || url.startsWith("http://") || url.startsWith("https://")) {
                item.put("type", "remote")
                if (!item.has("format") || item.optString("format").isBlank())
                    item.put("format", "binary")
                item.put("download_detour", "direct")
            }
        }
    }

    private fun ensureDirectOutbound(root: JSONObject) {
        val outbounds =
            root.optJSONArray("outbounds") ?: JSONArray().also { root.put("outbounds", it) }
        for (i in 0 until outbounds.length()) {
            if (outbounds.optJSONObject(i)?.optString("tag") == "direct") return
        }
        outbounds.put(JSONObject().put("type", "direct").put("tag", "direct"))
    }

    private fun ensureBlockOutbound(root: JSONObject) {
        val outbounds =
            root.optJSONArray("outbounds") ?: JSONArray().also { root.put("outbounds", it) }
        for (i in 0 until outbounds.length()) {
            if (outbounds.optJSONObject(i)?.optString("tag") == "block") return
        }
        outbounds.put(JSONObject().put("type", "block").put("tag", "block"))
    }
}
