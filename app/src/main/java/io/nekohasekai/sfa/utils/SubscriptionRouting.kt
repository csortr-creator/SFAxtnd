package io.nekohasekai.sfa.utils

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

object SubscriptionRouting {

    const val NORMAL_SELECTOR_TAG = "Proxy"
    const val WHITELIST_SELECTOR_TAG = "Whitelist Bypass"

    enum class Mode {
        NORMAL,
        WHITELIST_BYPASS
    }

    private enum class ServerRoutingType {
        NORMAL,
        WHITELIST_BYPASS
    }

    fun detectMode(
        content: String
    ): Mode {
        val normalized =
            content.lowercase(
                Locale.ROOT
            )

        return when {
            normalized.contains(
                "white-list"
            ) -> Mode.WHITELIST_BYPASS

            normalized.contains(
                "whitelist"
            ) -> Mode.WHITELIST_BYPASS

            normalized.contains(
                "white list"
            ) -> Mode.WHITELIST_BYPASS

            normalized.contains(
                "обход белых списков"
            ) -> Mode.WHITELIST_BYPASS

            else -> Mode.NORMAL
        }
    }

    fun isWhitelistBypassTag(
        tag: String
    ): Boolean {
        return detectServerType(tag) ==
            ServerRoutingType.WHITELIST_BYPASS
    }

    private fun detectServerType(
        tag: String
    ): ServerRoutingType {
        val normalized =
            tag
                .lowercase(
                    Locale.ROOT
                )
                .trim()

        return when {
            normalized.contains(
                "обход"
            ) &&
                normalized.contains(
                    "бел"
                ) ->
                ServerRoutingType.WHITELIST_BYPASS

            normalized.contains(
                "white list"
            ) ->
                ServerRoutingType.WHITELIST_BYPASS

            normalized.contains(
                "whitelist"
            ) ->
                ServerRoutingType.WHITELIST_BYPASS

            normalized.contains(
                "white-list"
            ) ->
                ServerRoutingType.WHITELIST_BYPASS

            normalized.contains(
                "bypass"
            ) &&
                (
                    normalized.contains(
                        "white"
                    ) ||
                        normalized.contains(
                            "list"
                        )
                    ) ->
                ServerRoutingType.WHITELIST_BYPASS

            else ->
                ServerRoutingType.NORMAL
        }
    }

    fun apply(
        root: JSONObject,
        mode: Mode
    ) {
        ensureRoute(root)

        when (mode) {
            Mode.NORMAL -> {
                applyNormalRouting(root)
            }

            Mode.WHITELIST_BYPASS -> {
                applyWhitelistRouting(root)
            }
        }
    }

    private fun ensureRoute(
        root: JSONObject
    ): JSONObject {
        val route =
            root.optJSONObject(
                "route"
            ) ?: JSONObject().also {
                root.put(
                    "route",
                    it
                )
            }

        if (
            route.optJSONArray(
                "rules"
            ) == null
        ) {
            route.put(
                "rules",
                JSONArray()
            )
        }

        return route
    }

    private fun applyNormalRouting(
        root: JSONObject
    ) {
        val route =
            ensureRoute(root)

        val oldRules =
            route.optJSONArray(
                "rules"
            ) ?: JSONArray()

        val rules =
            JSONArray()

        appendSystemRules(
            rules,
            oldRules
        )

        appendNormalDirectRules(
            rules
        )

        route.put(
            "rules",
            rules
        )

        route.put(
            "final",
            NORMAL_SELECTOR_TAG
        )

        root.put(
            "route",
            route
        )
    }

    private fun applyWhitelistRouting(
        root: JSONObject
    ) {
        val route =
            ensureRoute(root)

        val oldRules =
            route.optJSONArray(
                "rules"
            ) ?: JSONArray()

        val rules =
            JSONArray()

        appendSystemRules(
            rules,
            oldRules
        )

        appendWhitelistDirectRules(
            rules
        )

        route.put(
            "rules",
            rules
        )

        val availableTags =
            getOutboundTags(root)

        route.put(
            "final",
            if (
                WHITELIST_SELECTOR_TAG in
                availableTags
            ) {
                WHITELIST_SELECTOR_TAG
            } else {
                NORMAL_SELECTOR_TAG
            }
        )

        root.put(
            "route",
            route
        )
    }

    private fun appendSystemRules(
        target: JSONArray,
        source: JSONArray
    ) {
        var hasSniff =
            false

        var hasHijackDns =
            false

        for (
            i in 0 until
                source.length()
        ) {
            val rule =
                source.optJSONObject(i)
                    ?: continue

            val action =
                rule.optString(
                    "action"
                )

            val protocol =
                rule.optString(
                    "protocol"
                )

            if (
                action == "sniff"
            ) {
                hasSniff =
                    true
            }

            if (
                action == "hijack-dns" ||
                protocol == "dns"
            ) {
                hasHijackDns =
                    true
            }

            target.put(
                JSONObject(
                    rule.toString()
                )
            )
        }

        if (
            !hasSniff
        ) {
            target.put(
                0,
                JSONObject().apply {
                    put(
                        "action",
                        "sniff"
                    )
                }
            )
        }

        if (
            !hasHijackDns
        ) {
            val dnsRule =
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

            target.put(
                if (hasSniff) 1 else 1,
                dnsRule
            )
        }
    }

    private fun appendNormalDirectRules(
        rules: JSONArray
    ) {
        rules.put(
            JSONObject().apply {
                put(
                    "ip_is_private",
                    true
                )

                put(
                    "action",
                    "route"
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
                    "action",
                    "route"
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
                        put(".ru")
                        put(".su")
                        put(".xn--p1ai")
                        put(".by")
                        put(".kz")
                    }
                )

                put(
                    "action",
                    "route"
                )

                put(
                    "outbound",
                    "direct"
                )
            }
        )
    }

    private fun appendWhitelistDirectRules(
        rules: JSONArray
    ) {
        rules.put(
            JSONObject().apply {
                put(
                    "ip_is_private",
                    true
                )

                put(
                    "action",
                    "route"
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
                    "action",
                    "route"
                )

                put(
                    "outbound",
                    "direct"
                )
            }
        )
    }

    private fun getOutboundTags(
        root: JSONObject
    ): Set<String> {
        val tags =
            mutableSetOf<String>()

        val outbounds =
            root.optJSONArray(
                "outbounds"
            ) ?: return tags

        for (
            i in 0 until
                outbounds.length()
        ) {
            val tag =
                outbounds
                    .optJSONObject(i)
                    ?.optString(
                        "tag"
                    )
                    ?.trim()

            if (
                !tag.isNullOrBlank()
            ) {
                tags.add(tag)
            }
        }

        return tags
    }
}
