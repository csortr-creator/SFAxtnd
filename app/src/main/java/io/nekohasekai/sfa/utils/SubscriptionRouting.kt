package io.nekohasekai.sfa.utils

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

enum class ServerRoutingType {
    NORMAL,
    WHITELIST_BYPASS
}

object SubscriptionRouting {

    /**
     * Определяет тип сервера по его названию.
     *
     * Обычный сервер:
     * всё, что доступно в РФ -> direct
     * остальное -> proxy
     *
     * Сервер обхода белых списков:
     * используется специальный роутинг провайдера,
     * обычно определяется словом "обход" в названии.
     */
    fun detectServerType(tag: String?): ServerRoutingType {
        val normalized = tag
            .orEmpty()
            .lowercase(Locale.getDefault())

        return when {
            normalized.contains("обход") -> ServerRoutingType.WHITELIST_BYPASS
            normalized.contains("white list") -> ServerRoutingType.WHITELIST_BYPASS
            normalized.contains("whitelist") -> ServerRoutingType.WHITELIST_BYPASS
            normalized.contains("белый список") -> ServerRoutingType.WHITELIST_BYPASS

            else -> ServerRoutingType.NORMAL
        }
    }

    fun buildRouting(
        serverType: ServerRoutingType,
        proxyOutbound: String = "proxy",
        directOutbound: String = "direct"
    ): JSONObject {
        return when (serverType) {
            ServerRoutingType.NORMAL -> {
                buildNormalRouting(
                    proxyOutbound = proxyOutbound,
                    directOutbound = directOutbound
                )
            }

            ServerRoutingType.WHITELIST_BYPASS -> {
                buildWhitelistBypassRouting(
                    proxyOutbound = proxyOutbound,
                    directOutbound = directOutbound
                )
            }
        }
    }

    /**
     * Обычный сервер.
     *
     * Логика:
     *
     * 1. DNS перехватывается.
     * 2. Локальные IP идут напрямую.
     * 3. Российские домены идут напрямую.
     * 4. Российские IP идут напрямую.
     * 5. Всё остальное идёт через proxy.
     *
     * Именно такая схема соответствует основной логике:
     *
     * доступное в РФ -> direct
     * остальное -> proxy
     */
    private fun buildNormalRouting(
        proxyOutbound: String,
        directOutbound: String
    ): JSONObject {

        val rules = JSONArray()

        rules.put(
            JSONObject().apply {
                put("action", "sniff")
            }
        )

        rules.put(
            JSONObject().apply {
                put("protocol", "dns")
                put("action", "hijack-dns")
            }
        )

        rules.put(
            JSONObject().apply {
                put("ip_is_private", true)
                put("outbound", directOutbound)
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

                put("outbound", directOutbound)
            }
        )

        rules.put(
            JSONObject().apply {
                put(
                    "rule_set",
                    JSONArray().apply {
                        put("geosite-category-ru")
                        put("geoip-ru")
                    }
                )

                put("outbound", directOutbound)
            }
        )

        return JSONObject().apply {
            put("rules", rules)
            put("final", proxyOutbound)
            put("auto_detect_interface", true)
            put("default_domain_resolver", "dns-direct")
        }
    }

    /**
     * Сервер для обхода белых списков.
     *
     * Важно:
     *
     * Такой сервер не получает обычный routing
     * с категорией доступных в РФ ресурсов.
     *
     * Здесь сохраняется отдельная логика маршрутизации.
     *
     * Пока базовая схема такая:
     *
     * локальные сети -> direct
     * служебные DNS/IP -> direct
     * остальное -> proxy
     *
     * В дальнейшем сюда будет вынесен полноценный
     * отдельный список белого маршрута.
     */
    private fun buildWhitelistBypassRouting(
        proxyOutbound: String,
        directOutbound: String
    ): JSONObject {

        val rules = JSONArray()

        rules.put(
            JSONObject().apply {
                put("action", "sniff")
            }
        )

        rules.put(
            JSONObject().apply {
                put("protocol", "dns")
                put("action", "hijack-dns")
            }
        )

        rules.put(
            JSONObject().apply {
                put("ip_is_private", true)
                put("outbound", directOutbound)
            }
        )

        rules.put(
            JSONObject().apply {
                put(
                    "ip_cidr",
                    JSONArray().apply {
                        put("213.24.64.175/32")
                        put("213.24.64.181/32")
                        put("185.73.195.0/24")
                    }
                )

                put("outbound", directOutbound)
            }
        )

        return JSONObject().apply {
            put("rules", rules)
            put("final", proxyOutbound)
            put("auto_detect_interface", true)
            put("default_domain_resolver", "dns-direct")
        }
    }
}
