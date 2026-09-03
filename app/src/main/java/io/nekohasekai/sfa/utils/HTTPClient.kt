package io.nekohasekai.sfa.utils

import android.util.Base64
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.ktx.unwrap
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

class HTTPClient : Closeable {

    private val client = Libbox.newHTTPClient()

    init {
        client.modernTLS()
    }

    fun getString(url: String): String {
        val request = client.newRequest()
        request.setURL(url)

        val hwid = "cdf840c7d055fb2d"
        request.setUserAgent("Happ/3.20.4")
        request.setHeader("HWID", hwid)
        request.setHeader("hwid", hwid)
        request.setHeader("X-HWID", hwid)
        request.setHeader("Device-ID", hwid)
        request.setHeader("Happ-HWID", hwid)
        request.setHeader("App-Name", "Happ")
        request.setHeader("App-Version", "3.20.4")
        request.setHeader("Platform", "Android")
        request.setHeader("Accept", "*/*")

        val response = request.execute()
        val rawContent = response.content.unwrap

        return processSubscriptionContent(rawContent)
    }

    private fun processSubscriptionContent(raw: String): String {
        val trimmed = raw.trim()

        if (trimmed.startsWith("{")) {
            val obj = runCatching { JSONObject(trimmed) }.getOrNull()
            if (obj != null && obj.has("outbounds") && obj.has("route")) {
                return raw
            }
        }

        val parsedNodes = mutableListOf<ParsedNode>()

        if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
            val jsonArray = if (trimmed.startsWith("[")) {
                runCatching { JSONArray(trimmed) }.getOrNull()
            } else {
                runCatching { JSONArray().put(JSONObject(trimmed)) }.getOrNull()
            }
            if (jsonArray != null) {
                parsedNodes.addAll(parseCustomJsonNodes(jsonArray))
            }
        }

        if (parsedNodes.isEmpty()) {
            val decodedText = tryDecodeBase64(trimmed)
            parsedNodes.addAll(parseUriLines(decodedText))
        }

        if (parsedNodes.isEmpty()) {
            return raw
        }

        return buildFullSingBoxConfig(parsedNodes)
    }

    private data class ParsedNode(
        val tag: String,
        val jsonObject: JSONObject
    )

    private fun tryDecodeBase64(input: String): String {
        val clean = input.replace("\r", "").replace("\n", "").trim()
        return try {
            val bytes = Base64.decode(clean, Base64.DEFAULT)
            String(bytes, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            input
        }
    }

    private fun countryCodeToEmoji(code: String): String {
        if (code.length != 2) return ""
        val upper = code.uppercase(Locale.ROOT)
        val first = Character.toChars(0x1F1E6 + (upper[0] - 'A'))
        val second = Character.toChars(0x1F1E6 + (upper[1] - 'A'))
        return String(first) + String(second)
    }

    private fun formatTagWithFlag(rawName: String): String {
        val trimmed = rawName.trim()
        val match = Regex("""[\uD83C][\uDDE6-\uDDFF][\uD83C][\uDDE6-\uDDFF]""").find(trimmed)
        if (match != null) return trimmed

        val patterns = listOf(
            Regex("""(?i)\b(RU|Russia|Russian|Россия|Москва|Новосибирск|Екатеринбург|Казань|Питер|СПб)\b""") to "RU",
            Regex("""(?i)\b(US|USA|United States|America|США|Америка)\b""") to "US",
            Regex("""(?i)\b(DE|Germany|Deutschland|Германия|Франкфурт)\b""") to "DE",
            Regex("""(?i)\b(NL|Netherlands|Holland|Нидерланды|Амстердам)\b""") to "NL",
            Regex("""(?i)\b(FI|Finland|Финляндия|Хельсинки)\b""") to "FI",
            Regex("""(?i)\b(GB|UK|United Kingdom|Great Britain|Великобритания|Англия|Лондон)\b""") to "GB",
            Regex("""(?i)\b(FR|France|Франция|Париж)\b""") to "FR",
            Regex("""(?i)\b(SE|Sweden|Швеция|Стокгольм)\b""") to "SE",
            Regex("""(?i)\b(PL|Poland|Польша|Варшава)\b""") to "PL",
            Regex("""(?i)\b(TR|Turkey|Türkiye|Турция|Стамбул)\b""") to "TR",
            Regex("""(?i)\b(KZ|Kazakhstan|Казахстан|Алматы|Астана)\b""") to "KZ",
            Regex("""(?i)\b(JP|Japan|Япония|Токио)\b""") to "JP",
            Regex("""(?i)\b(SG|Singapore|Сингапур)\b""") to "SG",
            Regex("""(?i)\b(HK|Hong Kong|Гонконг)\b""") to "HK",
            Regex("""(?i)\b(CH|Switzerland|Швейцария|Цюрих)\b""") to "CH",
            Regex("""(?i)\b(AT|Austria|Австрия|Вена)\b""") to "AT",
            Regex("""(?i)\b(ES|Spain|Испания|Мадрид)\b""") to "ES",
            Regex("""(?i)\b(IT|Italy|Италия|Рим|Милан)\b""") to "IT",
            Regex("""(?i)\b(AE|UAE|Dubai|ОАЭ|Дубай)\b""") to "AE"
        )

        for ((regex, code) in patterns) {
            if (regex.containsMatchIn(trimmed)) {
                val emoji = countryCodeToEmoji(code)
                return "$emoji $trimmed"
            }
        }

        return "🌐 $trimmed"
    }

    private fun parseUriLines(text: String): List<ParsedNode> {
        val nodes = mutableListOf<ParsedNode>()
        val seenTags = mutableSetOf<String>()
        val lines = text.lines()

        for (line in lines) {
            val rawLine = line.trim()
            if (rawLine.isEmpty()) continue
            val node = parseSingleUri(rawLine, seenTags)
            if (node != null) {
                nodes.add(node)
                seenTags.add(node.tag)
            }
        }
        return nodes
    }

    private fun parseSingleUri(uriStr: String, seenTags: Set<String>): ParsedNode? {
        return try {
            val uri = URI(uriStr)
            val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
            val rawName = uri.fragment?.let { URLDecoder.decode(it, "UTF-8") } ?: "${scheme.uppercase()}_Node"
            val displayName = formatTagWithFlag(rawName)

            var uniqueTag = displayName
            var count = 1
            while (seenTags.contains(uniqueTag)) {
                uniqueTag = "$displayName ($count)"
                count++
            }

            val queryParams = parseQueryParams(uri.rawQuery)
            val obj = JSONObject()
            obj.put("tag", uniqueTag)

            when (scheme) {
                "vless" -> {
                    obj.put("type", "vless")
                    obj.put("server", uri.host)
                    obj.put("server_port", if (uri.port > 0) uri.port else 443)
                    obj.put("uuid", uri.userInfo)
                    obj.put("flow", queryParams["flow"] ?: "")
                    obj.put("packet_encoding", "xudp")
                    obj.put("tcp_fast_open", true)

                    val security = queryParams["security"] ?: "none"
                    if (security == "tls" || security == "reality") {
                        val tlsObj = JSONObject().apply {
                            put("enabled", true)
                            put("server_name", queryParams["sni"] ?: uri.host)
                            put("utls", JSONObject().apply {
                                put("enabled", true)
                                put("fingerprint", queryParams["fp"] ?: "firefox")
                            })
                            if (security == "reality") {
                                put("reality", JSONObject().apply {
                                    put("enabled", true)
                                    put("public_key", queryParams["pbk"] ?: "")
                                    put("short_id", queryParams["sid"] ?: "")
                                })
                            }
                        }
                        obj.put("tls", tlsObj)
                    }
                }
                "trojan" -> {
                    obj.put("type", "trojan")
                    obj.put("server", uri.host)
                    obj.put("server_port", if (uri.port > 0) uri.port else 443)
                    obj.put("password", uri.userInfo)
                    obj.put("tcp_fast_open", true)
                    val tlsObj = JSONObject().apply {
                        put("enabled", true)
                        put("server_name", queryParams["sni"] ?: uri.host)
                    }
                    obj.put("tls", tlsObj)
                }
                "ss" -> {
                    obj.put("type", "shadowsocks")
                    obj.put("server", uri.host)
                    obj.put("server_port", if (uri.port > 0) uri.port else 8388)
                    val userInfoDecoded = tryDecodeBase64(uri.userInfo ?: "")
                    val parts = userInfoDecoded.split(":", limit = 2)
                    if (parts.size == 2) {
                        obj.put("method", parts[0])
                        obj.put("password", parts[1])
                    } else {
                        return null
                    }
                }
                else -> return null
            }
            ParsedNode(uniqueTag, obj)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseQueryParams(query: String?): Map<String, String> {
        if (query.isNullOrEmpty()) return emptyMap()
        val result = mutableMapOf<String, String>()
        for (pair in query.split("&")) {
            val parts = pair.split("=", limit = 2)
            if (parts.isNotEmpty()) {
                val key = parts[0]
                val value = if (parts.size > 1) URLDecoder.decode(parts[1], "UTF-8") else ""
                result[key] = value
            }
        }
        return result
    }

    private fun parseCustomJsonNodes(array: JSONArray): List<ParsedNode> {
        val seenTags = mutableSetOf<String>()
        val result = mutableListOf<ParsedNode>()

        for (i in 0 until array.length()) {
            val profile = array.optJSONObject(i) ?: continue
            val label = profile.optString("remarks").ifEmpty { "Node_$i" }
            val formattedTag = formatTagWithFlag(label)
            if (seenTags.contains(formattedTag)) continue

            val outbounds = profile.optJSONArray("outbounds") ?: continue
            for (j in 0 until outbounds.length()) {
                val out = outbounds.optJSONObject(j) ?: continue
                if (out.optString("protocol") != "vless") continue

                val settings = out.optJSONObject("settings") ?: continue
                val vnext = settings.optJSONArray("vnext")?.optJSONObject(0) ?: continue
                val user = vnext.optJSONArray("users")?.optJSONObject(0) ?: continue
                val address = vnext.optString("address")

                if (address.contains("edge.", ignoreCase = true) ||
                    address.contains("brdg.", ignoreCase = true) ||
                    address.contains("bridge", ignoreCase = true)
                ) {
                    continue
                }

                val streamSettings = out.optJSONObject("streamSettings")
                val realitySettings = streamSettings?.optJSONObject("realitySettings")

                val nodeObj = JSONObject().apply {
                    put("tag", formattedTag)
                    put("type", "vless")
                    put("server", address)
                    put("server_port", vnext.optInt("port", 443))
                    put("uuid", user.optString("id"))
                    put("flow", user.optString("flow", ""))
                    put("packet_encoding", "xudp")
                    put("tcp_fast_open", true)

                    val tlsObj = JSONObject().apply {
                        put("enabled", true)
                        put("server_name", realitySettings?.optString("serverName")?.ifEmpty { address } ?: address)
                        put("utls", JSONObject().apply {
                            put("enabled", true)
                            put("fingerprint", realitySettings?.optString("fingerprint")?.ifEmpty { "firefox" } ?: "firefox")
                        })
                        if (realitySettings != null) {
                            put("reality", JSONObject().apply {
                                put("enabled", true)
                                put("public_key", realitySettings.optString("publicKey"))
                                put("short_id", realitySettings.optString("shortId", ""))
                            })
                        }
                    }
                    put("tls", tlsObj)
                }

                result.add(ParsedNode(formattedTag, nodeObj))
                seenTags.add(formattedTag)
                break
            }
        }
        return result
    }

    private fun buildFullSingBoxConfig(nodes: List<ParsedNode>): String {
        val root = JSONObject()

        root.put("log", JSONObject().apply {
            put("level", "info")
            put("timestamp", true)
        })

        root.put("experimental", JSONObject().apply {
            put("cache_file", JSONObject().apply {
                put("enabled", true)
                put("path", "cache.db")
                put("store_fakeip", false)
            })
        })

        root.put("dns", JSONObject().apply {
            val servers = JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "udp")
                    put("tag", "yandex-dns")
                    put("server", "77.88.8.8")
                    put("server_port", 53)
                })
                put(JSONObject().apply {
                    put("type", "https")
                    put("tag", "remote-doh")
                    put("server", "1.1.1.1")
                    put("path", "/dns-query")
                    put("detour", "Список серверов")
                })
                put(JSONObject().apply {
                    put("type", "local")
                    put("tag", "local-dns")
                })
            }
            put("servers", servers)
            val dnsRules = JSONArray().apply {
                put(JSONObject().apply {
                    put("rule_set", JSONArray().put("geosite-ru"))
                    put("server", "yandex-dns")
                })
            }
            put("rules", dnsRules)
            put("strategy", "ipv4_only")
        })

        root.put("inbounds", JSONArray().apply {
            put(JSONObject().apply {
                put("type", "tun")
                put("tag", "tun-in")
                put("address", JSONArray().put("172.19.0.1/30"))
                put("auto_route", true)
                put("strict_route", false)
                put("stack", "mixed")
            })
            put(JSONObject().apply {
                put("type", "mixed")
                put("tag", "mixed-in")
                put("listen", "127.0.0.1")
                put("listen_port", 2080)
            })
        })

        val outbounds = JSONArray()
        val allTags = JSONArray()

        for (node in nodes) {
            allTags.put(node.tag)
        }

        outbounds.put(JSONObject().apply {
            put("tag", "Список серверов")
            put("type", "selector")
            put("outbounds", allTags)
        })

        for (node in nodes) {
            outbounds.put(node.jsonObject)
        }

        outbounds.put(JSONObject().apply {
            put("tag", "direct")
            put("type", "direct")
        })
        outbounds.put(JSONObject().apply {
            put("tag", "block")
            put("type", "block")
        })

        root.put("outbounds", outbounds)

        val rules = JSONArray().apply {
            put(JSONObject().apply { put("action", "sniff") })
            put(JSONObject().apply { put("protocol", "dns"); put("action", "hijack-dns") })
            put(JSONObject().apply { put("port", 53); put("action", "hijack-dns") })
            put(JSONObject().apply { put("ip_cidr", JSONArray().put("::/0")); put("outbound", "block") })
            put(JSONObject().apply { put("ip_is_private", true); put("outbound", "direct") })

            put(JSONObject().apply {
                put("package_name", JSONArray(listOf(
                    "ru.vk.store", "ru.rustore.installer", "com.vk.store", "com.google.android.gms",
                    "com.oplus.postmanservice", "com.heytap.mcs", "com.oplus.athena", "com.oplus.safecenter",
                    "com.coloros.mcs", "com.xiaomi.xmsf", "com.huawei.android.pushagent",
                    "com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.ss.android.ugc.aweme"
                )))
                put("outbound", "direct")
            })

            put(JSONObject().apply {
                put("package_name", JSONArray(listOf(
                    "com.miHoYo.GenshinImpact", "com.HoYoverse.hkrpgoversea", "com.dts.freefireth",
                    "com.mobile.legends", "com.tencent.ig", "com.pubg.krmobile", "net.wargaming.wot.blitz"
                )))
                put("outbound", "direct")
            })

            put(JSONObject().apply { put("domain_keyword", JSONArray().put("tiktok")); put("outbound", "direct") })
            put(JSONObject().apply {
                put("domain_suffix", JSONArray(listOf(
                    "hoyoverse.com", "mihoyo.com", "battle.net", "steamcontent.com", "steampowered.com"
                )))
                put("outbound", "direct")
            })

            put(JSONObject().apply {
                put("domain_suffix", JSONArray(listOf(
                    "connectivitycheck.gstatic.com", "connectivitycheck.android.com", "clients3.google.com", "msftconnecttest.com"
                )))
                put("outbound", "direct")
            })
            put(JSONObject().apply { put("protocol", JSONArray().put("bittorrent")); put("outbound", "direct") })

            put(JSONObject().apply {
                put("ip_cidr", JSONArray(listOf(
                    "185.73.195.0/24", "213.24.64.175/32", "213.24.64.181/32", "79.133.160.0/19",
                    "193.238.128.0/20", "95.173.136.0/21", "178.248.232.0/21", "87.250.250.0/24", "77.88.8.0/24"
                )))
                put("outbound", "direct")
            })
            put(JSONObject().apply { put("rule_set", JSONArray().put("geosite-ru").put("geoip-ru")); put("outbound", "direct") })
            put(JSONObject().apply { put("domain_suffix", JSONArray(listOf("ru", "su", "by", "kz", "xn--p1ai"))); put("outbound", "direct") })
        }

        root.put("route", JSONObject().apply {
            put("default_domain_resolver", "local-dns")
            put("rule_set", JSONArray().apply {
                put(JSONObject().apply {
                    put("tag", "geosite-ru")
                    put("type", "remote")
                    put("format", "binary")
                    put("url", "https://raw.githubusercontent.com/SagerNet/sing-geosite/rule-set/geosite-category-ru.srs")
                    put("download_detour", "direct")
                })
                put(JSONObject().apply {
                    put("tag", "geoip-ru")
                    put("type", "remote")
                    put("format", "binary")
                    put("url", "https://raw.githubusercontent.com/SagerNet/sing-geoip/rule-set/geoip-ru.srs")
                    put("download_detour", "direct")
                })
            })
            put("rules", rules)
            put("auto_detect_interface", true)
            put("final", "Список серверов")
        })

        return root.toString()
    }

    override fun close() {
        client.close()
    }
}
