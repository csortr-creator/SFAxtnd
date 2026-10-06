package io.nekohasekai.sfa.utils

import java.net.URI
import org.json.JSONArray
import org.json.JSONObject

object ClientSettingsConfig {
    fun applyCore(root: JSONObject, options: JSONObject) {
        val inbounds = root.optJSONArray("inbounds") ?: JSONArray()
        for (i in 0 until inbounds.length()) {
            val inbound = inbounds.optJSONObject(i) ?: continue
            if (inbound.optString("type") != "tun") continue
            val mtu = options.optInt("mtu")
            if (mtu in 1280..9000) inbound.put("mtu", mtu)
            if (options.has("strictRoute"))
                inbound.put("strict_route", options.optBoolean("strictRoute"))
            val mode = options.optString("ipMode", "profile")
            if (mode in setOf("ipv4", "dual")) {
                val addresses =
                    when (val value = inbound.opt("address")) {
                        is JSONArray -> value
                        is String -> JSONArray().put(value)
                        else -> JSONArray().put("172.19.0.1/30")
                    }
                val updated = JSONArray()
                for (j in 0 until addresses.length()) {
                    val address = addresses.optString(j)
                    if (mode == "dual" || !address.contains(':')) updated.put(address)
                }
                if (updated.length() == 0) updated.put("172.19.0.1/30")
                if (
                    mode == "dual" &&
                        (0 until updated.length()).none { updated.optString(it).contains(':') }
                )
                    updated.put("fdfe:dcba:9876::1/126")
                inbound.put("address", updated)
            }
        }
        val route = root.optJSONObject("route") ?: JSONObject().also { root.put("route", it) }
        if (options.has("findProcess")) route.put("find_process", options.optBoolean("findProcess"))
        val existing = route.optJSONArray("rules") ?: JSONArray()
        val rules = JSONArray()
        val sniff = options.optString("sniff", "profile")
        val hijack = options.optString("hijack", "profile")
        if (sniff == "on") {
            val action =
                JSONObject()
                    .put("action", "sniff")
                    .put("timeout", "${options.optInt("sniffTimeoutMs", 300).coerceIn(50, 5000)}ms")
            val sniffers = values(options.optString("sniffers"))
            if (sniffers.isNotEmpty()) action.put("sniffer", JSONArray(sniffers))
            rules.put(action)
        }
        val outbounds = root.optJSONArray("outbounds") ?: JSONArray()
        if (options.optBoolean("fastFallback"))
            for (i in 0 until outbounds.length()) {
                val outbound = outbounds.optJSONObject(i) ?: continue
                if (
                    outbound.optString("type") in
                        setOf(
                            "direct",
                            "vless",
                            "vmess",
                            "trojan",
                            "shadowsocks",
                            "hysteria",
                            "hysteria2",
                            "tuic",
                            "http",
                            "socks",
                            "anytls",
                            "naive",
                            "ssh",
                        )
                )
                    outbound.put("fallback_delay", "10ms")
            }
        if (hijack == "on")
            rules.put(
                JSONObject()
                    .put("type", "logical")
                    .put("mode", "or")
                    .put(
                        "rules",
                        JSONArray()
                            .put(JSONObject().put("protocol", "dns"))
                            .put(JSONObject().put("port", JSONArray().put(53))),
                    )
                    .put("action", "hijack-dns")
            )
        if (options.optString("resolveMode") == "on") {
            rules.put(
                JSONObject()
                    .put("action", "resolve")
                    .put("strategy", options.optString("resolveStrategy", "prefer_ipv4"))
            )
        }
        for (i in 0 until existing.length()) {
            val rule = existing.optJSONObject(i) ?: continue
            if (sniff != "profile" && rule.optString("action") == "sniff") continue
            if (
                options.optString("resolveMode", "profile") != "profile" &&
                    rule.optString("action") == "resolve"
            )
                continue
            if (hijack != "profile" && rule.optString("action") == "hijack-dns") continue
            // Replace the legacy block outbound action; rejection is a normal route event.
            if (rule.optString("outbound") == "block") {
                rule.remove("outbound")
                rule.put("action", "reject")
            }
            rules.put(rule)
        }
        route.put("rules", rules)
        val level = options.optString("logLevel")
        if (level in setOf("trace", "debug", "info", "warn", "error")) {
            val log = root.optJSONObject("log") ?: JSONObject().also { root.put("log", it) }
            log.put("level", level)
        }
    }

    fun applyRuleSets(root: JSONObject, custom: JSONArray?) {
        if (custom == null) return
        val route = root.optJSONObject("route") ?: JSONObject().also { root.put("route", it) }
        val sets = route.optJSONArray("rule_set") ?: JSONArray()
        val byTag = linkedMapOf<String, JSONObject>()
        for (i in 0 until sets.length()) sets.optJSONObject(i)?.let {
            byTag[it.optString("tag")] = it
        }
        for (i in 0 until custom.length()) {
            val item = custom.optJSONObject(i) ?: continue
            if (item.optString("tag").isBlank()) continue
            byTag[item.getString("tag")] = JSONObject(item.toString())
        }
        route.put("rule_set", JSONArray(byTag.values.toList()))
    }

    fun applyDns(root: JSONObject, user: JSONObject?) {
        if (user == null) return
        val dns = root.optJSONObject("dns") ?: JSONObject().also { root.put("dns", it) }
        if (user.has("optimistic")) {
            dns.put(
                "optimistic",
                user.optBoolean("optimistic") && user.optBoolean("cacheEnabled", true),
            )
            dns.remove("disable_expire")
        }
        if (user.has("timeoutSeconds"))
            dns.put("timeout", "${user.optInt("timeoutSeconds", 5).coerceIn(1, 60)}s")
        if (user.has("cacheCapacity"))
            dns.put("cache_capacity", user.optInt("cacheCapacity", 4096).coerceIn(1024, 65536))
        val definitions = user.optJSONArray("servers") ?: JSONArray()
        val servers = dns.optJSONArray("servers") ?: JSONArray().also { dns.put("servers", it) }
        // Apply robust URI parsing, including IPv6 literals and non-default ports.
        for (i in 0 until definitions.length()) {
            val definition = definitions.optJSONObject(i) ?: continue
            val tag = definition.optString("tag")
            val target =
                (0 until servers.length())
                    .mapNotNull { servers.optJSONObject(it) }
                    .firstOrNull { it.optString("tag") == tag } ?: continue
            val address = definition.optString("address")
            if (address == "local") continue
            runCatching {
                    val uri = URI(if (address.contains("://")) address else "udp://$address")
                    val host = uri.host ?: error("Некорректный адрес DNS")
                    target.put("server", host.removePrefix("[").removeSuffix("]"))
                    if (uri.port > 0) target.put("server_port", uri.port)
                    if (uri.scheme == "https" || uri.scheme == "h3")
                        target.put("path", uri.rawPath.ifBlank { "/dns-query" })
                    if (!isIpLiteral(host)) {
                        val bootstrapTag = "sfa-bootstrap"
                        if (
                            (0 until servers.length()).none {
                                servers.optJSONObject(it)?.optString("tag") == bootstrapTag
                            }
                        )
                            servers.put(JSONObject().put("type", "local").put("tag", bootstrapTag))
                        target.put("domain_resolver", bootstrapTag)
                    }
                }
                .getOrThrow()
        }
        if (!user.optBoolean("managed", false)) return
        val tagSet =
            (0 until servers.length())
                .mapNotNull { servers.optJSONObject(it)?.optString("tag") }
                .toSet()
        val direct =
            (0 until definitions.length())
                .mapNotNull { definitions.optJSONObject(it) }
                .filter { it.optString("detour") == "direct" }
                .map { it.optString("tag") }
                .filter { it in tagSet }
        val proxy =
            (0 until definitions.length())
                .mapNotNull { definitions.optJSONObject(it) }
                .filter { it.optString("detour") == "proxy" }
                .map { it.optString("tag") }
                .filter { it in tagSet }
        val groups = mapOf("direct" to direct, "proxy" to proxy)
        val strategy =
            mapOf(
                "direct" to user.optString("directStrategy", "prefer_ipv4"),
                "proxy" to user.optString("proxyStrategy", "prefer_ipv4"),
            )
        val rules = dns.optJSONArray("rules") ?: JSONArray()
        val output = JSONArray()
        var chainId = 0
        fun denyQuery(condition: JSONObject, query: String): JSONObject {
            val clean = JSONObject(condition.toString())
            listOf("action", "server", "timeout", "strategy", "tag").forEach(clean::remove)
            val denied =
                if (clean.optString("type") == "logical")
                    JSONObject()
                        .put("type", "logical")
                        .put("mode", "and")
                        .put(
                            "rules",
                            JSONArray().put(clean).put(JSONObject().put("query_type", query)),
                        )
                else clean.put("query_type", query)
            return denied.put("action", "predefined").put("rcode", "NOERROR")
        }
        fun chain(condition: JSONObject, group: String) {
            val tags = groups[group].orEmpty()
            if (tags.isEmpty()) return
            val base = JSONObject(condition.toString())
            listOf("server", "strategy", "timeout", "action").forEach(base::remove)
            val only = strategy[group]
            if (only in setOf("ipv4_only", "ipv6_only")) {
                output.put(denyQuery(base, if (only == "ipv4_only") "AAAA" else "A"))
            }
            for ((index, tag) in tags.withIndex()) {
                val opts =
                    JSONObject(base.toString())
                        .put("server", tag)
                        .put(
                            "timeout",
                            "${user.optInt("${group}TimeoutSeconds", 5).coerceIn(1, 60)}s",
                        )
                if (index == tags.lastIndex) output.put(opts.put("action", "route"))
                else {
                    val responseTag = "sfa-fallback-${chainId++}"
                    output.put(opts.put("action", "evaluate").put("tag", responseTag))
                    output.put(
                        JSONObject()
                            .put("match_response", responseTag)
                            .put("response_rcode", "NOERROR")
                            .put("action", "respond")
                    )
                }
            }
        }
        // Resolve proxy/DNS endpoint hostnames through the direct group to avoid a loop.
        val bootstrapDomains = linkedSetOf<String>()
        val outbounds = root.optJSONArray("outbounds") ?: JSONArray()
        for (i in 0 until outbounds.length()) outbounds
            .optJSONObject(i)
            ?.optString("server")
            ?.takeIf { it.isNotBlank() && !isIpLiteral(it) }
            ?.let(bootstrapDomains::add)
        for (i in 0 until servers.length()) servers
            .optJSONObject(i)
            ?.optString("server")
            ?.takeIf { it.isNotBlank() && !isIpLiteral(it) }
            ?.let(bootstrapDomains::add)
        if (bootstrapDomains.isNotEmpty())
            chain(JSONObject().put("domain", JSONArray(bootstrapDomains.toList())), "direct")
        for (i in 0 until rules.length()) {
            val rule = rules.optJSONObject(i) ?: continue
            // 1.14 evaluate/respond cannot coexist with legacy address filters or
            // per-rule strategy; keep address matching explicit on a fetched response.
            if (rule.has("outbound")) continue
            val legacyStrategy = rule.optString("strategy")
            fun needsResponse(rule: JSONObject): Boolean {
                val nested = rule.optJSONArray("rules")
                if (nested != null)
                    return (0 until nested.length()).any {
                        nested.optJSONObject(it)?.let(::needsResponse) == true
                    }
                val sets =
                    when (val raw = rule.opt("rule_set")) {
                        is JSONArray -> raw
                        is String -> JSONArray().put(raw)
                        else -> null
                    }
                return !rule.has("match_response") &&
                    (rule.has("ip_cidr") ||
                        rule.has("ip_is_private") ||
                        rule.has("ip_accept_any") ||
                        (sets != null &&
                            (0 until sets.length()).any {
                                !sets.optString(it).startsWith("geosite-")
                            }))
            }
            fun migrate(rule: JSONObject, responseTag: String?) {
                rule.remove("strategy")
                rule.remove("rule_set_ip_cidr_accept_empty")
                if (responseTag != null && needsResponse(rule) && !rule.has("rules"))
                    rule.put("match_response", responseTag)
                val nested = rule.optJSONArray("rules") ?: return
                for (index in 0 until nested.length()) nested.optJSONObject(index)?.let {
                    migrate(it, responseTag)
                }
            }
            val needsResponse = needsResponse(rule)
            if (needsResponse) {
                val evaluateTag = "sfa-migration-${chainId++}"
                val source = proxy.firstOrNull() ?: direct.firstOrNull()
                if (source != null) {
                    output.put(
                        JSONObject()
                            .put("action", "evaluate")
                            .put("server", source)
                            .put("tag", evaluateTag)
                            .put(
                                "timeout",
                                "${user.optInt("proxyTimeoutSeconds", 5).coerceIn(1, 60)}s",
                            )
                    )
                    migrate(rule, evaluateTag)
                }
            }
            migrate(rule, null)
            if (legacyStrategy in setOf("ipv4_only", "ipv6_only")) {
                output.put(denyQuery(rule, if (legacyStrategy == "ipv4_only") "AAAA" else "A"))
            }
            val tag = rule.optString("server")
            val group =
                when {
                    tag in direct || tag in setOf("dns-direct", "local") -> "direct"
                    tag in proxy || tag in setOf("dns-proxy", "dns-remote") -> "proxy"
                    else -> null
                }
            if (
                group != null &&
                    groups[group].orEmpty().isNotEmpty() &&
                    rule.optString("action") in setOf("", "route")
            ) {
                val actionKeys =
                    setOf(
                        "action",
                        "server",
                        "strategy",
                        "timeout",
                        "disable_cache",
                        "disable_optimistic_cache",
                        "rewrite_ttl",
                        "client_subnet",
                    )
                if (rule.keys().asSequence().any { it !in actionKeys }) chain(rule, group)
            } else output.put(rule)
        }
        if (user.optBoolean("fakeip", false)) {
            val fakeTag = "sfa-fakeip"
            servers.put(
                JSONObject()
                    .put("type", "fakeip")
                    .put("tag", fakeTag)
                    .put("inet4_range", "198.18.0.0/15")
                    .put("inet6_range", "fc00::/18")
            )
            output.put(
                JSONObject()
                    .put("query_type", JSONArray(listOf("A", "AAAA")))
                    .put("server", fakeTag)
            )
        }
        val finalGroup = user.optString("defaultGroup", "proxy")
        dns.put("strategy", strategy[finalGroup])
        chain(JSONObject(), finalGroup)
        dns.put("rules", output)
        groups[finalGroup]?.firstOrNull()?.let { dns.put("final", it) }
        val resolver = user.optString("resolverServer").ifBlank { direct.firstOrNull().orEmpty() }
        if (resolver in tagSet) {
            val route = root.optJSONObject("route") ?: JSONObject().also { root.put("route", it) }
            route.put(
                "default_domain_resolver",
                JSONObject()
                    .put("server", resolver)
                    .put("strategy", strategy["direct"])
                    .put("timeout", "${user.optInt("directTimeoutSeconds", 5).coerceIn(1, 60)}s"),
            )
        }
    }

    private fun isIpLiteral(host: String) =
        host.contains(':') || host.matches(Regex("[0-9]+(?:\\.[0-9]+){3}"))

    private fun values(raw: String) =
        raw.split(',', ';', '\n').map(String::trim).filter(String::isNotEmpty)
}
