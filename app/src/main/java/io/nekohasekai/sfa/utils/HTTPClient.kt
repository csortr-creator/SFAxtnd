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

private val WHITELIST_KEYWORDS = listOf(
    // Английские термины
    "whitelist", "white list", "white-list", "white_list", "whitelisted",
    "bypass", "blackout", "shutdown",
    "anti-block", "antiblock", "anti-censorship", "antidpi", "anti-dpi",
    "anti-filter", "antifilter",

    // Русские термины
    "обход", "вайтлист", "блэкаут", "блекаут", "шатдаун", "глушилка", "глушилки",
    "антиблок", "антизапрет", "антифильтр", "антидпи", "тспу",

    // Словоформы "белый список"
    "белые", "белых", "белый", "белого", "белому", "белом", "белосписоч",
    "списков", "списки", "список",

    // Ранги серверов обхода
    "lead", "copper", "cooper", "aluminium",
    "gold", "cobalt", "platinum", "silver", "diamond", "obsidian"
)

private val SHORT_TAG_REGEX = Regex(
    "(?i)(^|[^a-zA-Z0-9а-яА-ЯёЁ])(wl|lte|бс|тспу|tspu)([^a-zA-Z0-9а-яА-ЯёЁ]|$)"
)

private fun isWhitelistServer(tag: String): Boolean {
    val lower = tag.lowercase()
    if (WHITELIST_KEYWORDS.any { lower.contains(it) }) return true
    return SHORT_TAG_REGEX.containsMatchIn(tag)
}

private val WHITELIST_DOMAINS = listOf(
    "anytimeru.com", "dbankcloud.ru", "1c-bitrix.ru", "1c.ru", "1cfresh.com", "1cloud.ru", "1internet.tv",
    "2gis.ae", "2gis.am", "2gis.az", "2gis.by", "2gis.com", "2gis.com.cy", "2gis.cz", "2gis.ge", "2gis.kg",
    "2gis.kz", "2gis.ru", "2gis.tj", "2gis.ua", "2gis.uz", "2ip.ru", "47news.ru", "4meeting.me", "5ka.ru",
    "5post.market", "abr.ru", "aclub.ru", "adfox.ru", "admetrica.ru", "adygeya.ru", "aeroflot.ru",
    "alfa-bank.com", "alfa-bank.ru", "alfa-finance.com", "alfa-fx.com", "alfa-pc.com", "alfa-usa.com",
    "alfabank.com", "alfabank.ru", "alfafinance.biz", "alfafinance.ru", "alfafuture.com", "alfafuture.ru",
    "alfafx.com", "alfaleasing.ru", "alfaprivate.com", "alformacap.com", "alformacapital.com", "altai.ru",
    "amur.ru", "arkhangelsk.ru", "astrakhan.ru", "auth-nsdi.ru", "auto.ru", "av.ru", "avito.ru", "avito.st",
    "baltbank.ru", "banka-ui.dev", "banki.ru", "bankline.ru", "bashkiria.ru", "beeline.ru", "belgorod.ru",
    "beta-bank.com", "bir.ru", "bitrix24.ru", "bronevik.com", "bryansk.ru", "buryatia.ru", "cbg.ru", "cbr.ru",
    "cdn-tinkoff.ru", "cdn-vk.ru", "checkip.amazonaws.com", "chel.ru", "chelyabinsk.ru", "chita.ru",
    "chizhik.club", "chukotka.ru", "chuvashia.ru", "cikrf.ru", "citydrive.ru", "clstorage.net", "credistory.ru",
    "crimea.ru", "csat.ru", "cscampus.ru", "dagestan.ru", "dbo-dengi.online", "dellin.ru", "dixy.ru",
    "dnevnik.ru", "dns-shop.ru", "dodopizza.ru", "dom.ru", "domclick.ru", "donationalerts.com", "drweb.ru",
    "dzen.ru", "dzeninfra.ru", "e5.ru", "ebs.ru", "edadeal.io", "edadeal.ru", "edu.ru", "ekburg.ru",
    "emias.info", "fastvps.ru", "finuslugi.ru", "fivepost.ru", "fix-price.com", "gazeta.ru", "gazprombank.ru",
    "gazprombank.tech", "gazprompay.ru", "gorodpay.ru", "goskey.ru", "gosuslugi.ru", "gov.ru", "government.ru",
    "gpb.ru", "gpmdi.ru", "grfc.ru", "grozny.ru", "gu-st.ru", "hh.ru", "i-ola.ru", "idx5.ru", "ifconfig.me",
    "imgsmail.ru", "investalfabank.com", "ip.sb", "ipapi.is", "ipify.org", "iplocate.io", "irk.ru", "irkutsk.ru",
    "ivanovo.ru", "iz.ru", "izbirkom.ru", "izhevsk.ru", "jamal.ru", "jar.ru", "jivo.ru", "jivochat.com",
    "jivosite.com", "jx5.ru", "kalmykia.ru", "kaluga.ru", "kamchatka.ru", "karelia.ru", "kaspersky.com",
    "kaspersky.ru", "kazan.ru", "kazanexpress.ru", "kchr.ru", "kemerovo.ru", "khabarovsk.ru", "khakassia.ru",
    "khv.ru", "kinopoisk-ru.clstorage.net", "kinopoisk.ru", "kirov.ru", "koenig.ru", "kommersant.ru",
    "kostroma.ru", "kp.ru", "krasnodar.su", "krasnoyarsk.ru", "krasyar.ru", "krd.ru", "kremlin.ru", "kuban.ru",
    "kuper.ru", "kurgan.ru", "kursk.ru", "lead-pro2023.online", "lemanapro.ru", "lenta.com", "lenta.ru",
    "lipetsk.ru", "lmru.tech", "magadan.ru", "magnit.ru", "mail.ru", "mari-el.ru", "mari.ru", "marine.ru",
    "max.ru", "megafon.ru", "megamarket.ru", "megamarket.tech", "memealerts.com", "mgfoms.ru", "mil.ru",
    "mirpayonline.ru", "miya-news.online", "mm.ru", "mnogolososya.ru", "moex.com", "mordovia.ru", "mos.ru",
    "mosreg.ru", "mradx.net", "mts.ru", "mtsdengi.ru", "murmansk.ru", "mvk.com", "myapelsin.ru", "mycdn.me",
    "mymts.ru", "nalchik.ru", "nalog.ru", "naydex.net", "nbki.ru", "netmonet.co", "nn.ru", "nov.ru",
    "novosibirsk.ru", "nsk.ru", "nspk.ru", "ok.ru", "okcdn.ru", "okko.sport", "okko.tv", "okolo.app",
    "omsk.ru", "oneme.ru", "orb.ru", "oryol.ru", "ozon.ru", "ozone.ru", "ozonusercontent.com", "penza.ru",
    "perekrestok.ru", "perm.ru", "pochta.ru", "psbank.ru", "psblog.ru", "psk", "psk.ru", "pskov.ru", "ptz.ru",
    "qms.ru", "rambler.ru", "rbc.ru", "res-nsdi.ru", "rkomi.ru", "rnd.ru", "rostaxi.org", "rostelecom.ru",
    "rshb.ru", "rt.ru", "rtbcdn.ru", "russiacalling.com", "rutube.ru", "rutubelist.ru", "ryazan.ru",
    "rzd-bonus.ru", "rzd.ru", "sakhalin.ru", "samara.ru", "saratov.ru", "sbermarket.ru", "sbermegamarket.ru",
    "sbpgpb.ru", "sev.ru", "sevastopol.ru", "simbirsk.ru", "sistema-capital.com", "smolensk.ru", "spb.ru",
    "spvb.ru", "static-storage.net", "stavropol.ru", "stv.ru", "surgut.ru", "svoy.academy", "t2.ru", "tambov.ru",
    "tamtam.chat", "tatarstan.ru", "taximaxim.ru", "taxsee.com", "tbank-online.com", "tele2.ru", "timeweb.cloud",
    "timeweb.com", "tips.tips", "tnt-online.ru", "tochka-tech.com", "tochka.com", "tom.ru", "tomsk.ru",
    "topdelivery.ru", "trbcdn.net", "tsaritsyn.ru", "tsk.ru", "tsx.x5static.net", "tu-tu.ru", "tula.ru",
    "turbopages.org", "tutu.ru", "tuva.ru", "tver.ru", "tyumen.ru", "udm.ru", "udmurtia.ru", "ulan-ude.ru",
    "usedesk.ru", "userapi.com", "uxfeedback.ru", "vgtrk.ru", "victoria-group.ru", "vk-analytics.ru",
    "vk-apps.com", "vk-apps.ru", "vk-cdn.me", "vk-cdn.net", "vk-portal.net", "vk.cc", "vk.com", "vk.company",
    "vk.design", "vk.link", "vk.me", "vk.ru", "vk.team", "vkcache.com", "vkcloud-static.ru", "vkgo.app",
    "vklive.app", "vkmessenger.app", "vkmessenger.com", "vkontakte.ru", "vkuser.net", "vkuseraudio.com",
    "vkuseraudio.net", "vkuseraudio.ru", "vkusercdn.ru", "vkuserlive.net", "vkuserphoto.ru", "vkuservideo.com",
    "vkuservideo.net", "vkuservideo.ru", "vkusnoitochka.ru", "vkusvill.ru", "vkvideo.ru", "vl.ru",
    "vladikavkaz.ru", "vladimir.ru", "vladivostok.ru", "vlg.ru", "volgograd.ru", "vologda.ru", "voronezh.ru",
    "vrn.ru", "vtb-grants.fut.ru", "vtb-liga.fut.ru", "vtb-russia.com", "vtb.bank.in", "vtb.com", "vtb.corp.ru",
    "vtb.digital", "vtb.fut.ru", "vtb.promo", "vtb.ru", "vtb24.com", "vtb24.ru", "vtbcareer.com", "vtbfamily.ru",
    "vtbindia.com", "vtbkep.site", "vtbpartners.com", "vtbrussia.com", "vtbrussia.ru", "vtbstrana.ru",
    "vyatka.ru", "wb.ru", "webvisor.com", "webvisor.org", "whoosh.bike", "wildberries.ru", "windsurf.com",
    "wink.ru", "x5.ru", "x5.tech", "x5club.ru", "x5id.ru", "x5l.ru", "x5paket.ru", "x5q.ru",
    "xn----7sb7akeedqd.xn--p1ai", "xn--80aacoonefzg3am8b1fsb.xn--p1ai", "xn--80acgfbsl1azdqr.xn--p1ai",
    "xn--80ajghhoc2aj1c8b.xn--p1ai", "xn--90ab2c.xn--p1ai", "xn--90aifd0aza.site", "xn--b1aew.xn--p1ai",
    "xn--d1acpjx3f.xn--p1ai", "ya.ru", "yads.tech", "yakutia.ru", "yamal.ru", "yandex", "yandex-bank.net",
    "yandex-images.clstorage.net", "yandex-team.ru", "yandex.aero", "yandex.az", "yandex.by", "yandex.cloud",
    "yandex.co.il", "yandex.com", "yandex.com.am", "yandex.com.ge", "yandex.com.ru", "yandex.com.tr",
    "yandex.com.ua", "yandex.de", "yandex.ee", "yandex.eu", "yandex.fi", "yandex.fr", "yandex.jobs", "yandex.kg",
    "yandex.kz", "yandex.lt", "yandex.lv", "yandex.md", "yandex.net", "yandex.org", "yandex.pl", "yandex.ru",
    "yandex.st", "yandex.sx", "yandex.tj", "yandex.tm", "yandex.tr", "yandex.ua", "yandex.uz",
    "yandexadexchange.net", "yandexcloud.net", "yandexcom.net", "yandexmetrica.com", "yandexwebcache.net",
    "yandexwebcache.org", "yaroslavl.ru", "yastat.net", "yastatic-net.ru", "yastatic.net", "yota.ru",
    "youla-web-static.mrgcdn.ru", "youla.io", "youla.ru", "yuzhno-sakhalinsk.ru", "zdrav10.ru", "zentotem.net",
    "freestylediabetes.ru", "hematonix.ru", "ican-sinocare.ru", "lumiflex.ru", "medtrum.eu", "medtrum.ru",
    "rsscenter.cloud", "ozon.app", "ozon.travel", "ozon.by", "ozon.kz", "cdek.ru", "cdek.shopping",
    "tanki.su", "lesta.ru"
)

class HTTPClient : Closeable {

    private val client = Libbox.newHTTPClient()

    init {
        client.modernTLS()
    }

    fun getString(url: String): String {
        val request = client.newRequest()
        request.setURL(url)

        val hwid = getOrCreateHwid(url)

        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val rawModel = Build.MODEL
        val model = if (rawModel.startsWith(manufacturer, ignoreCase = true)) rawModel else "$manufacturer $rawModel"
        val androidVer = Build.VERSION.RELEASE
        val buildId = Build.ID.ifEmpty { "UKQ1.231003.002" }

        val userAgentStr = "sing-box/1.14.0 SFAxtnd/0.0.8 (Linux; Android $androidVer; $model Build/$buildId) HWID/$hwid"

        request.setUserAgent(userAgentStr)
        request.setHeader("HWID", hwid)
        request.setHeader("hwid", hwid)
        request.setHeader("X-HWID", hwid)
        request.setHeader("Device-ID", hwid)
        request.setHeader("Happ-HWID", hwid)

        val fullDeviceTitle = "$model (Android $androidVer)"
        request.setHeader("Device-Name", fullDeviceTitle)
        request.setHeader("X-Device-Name", fullDeviceTitle)
        request.setHeader("Happ-Device-Name", fullDeviceTitle)
        request.setHeader("Device-Model", model)
        request.setHeader("X-Device-Model", model)
        request.setHeader("Device-OS", "Android $androidVer")
        request.setHeader("X-Device-OS", "Android $androidVer")

        request.setHeader("App-Name", "SFAxtnd")
        request.setHeader("Platform", "Android")
        request.setHeader("Accept", "*/*")

        val response = request.execute()
        val rawContent = response.content.unwrap

        return processSubscriptionContent(rawContent)
    }

    private fun getOrCreateHwid(url: String): String {
        val normalizedUrl = url.trim()
        val urlKey = try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(normalizedUrl.toByteArray(StandardCharsets.UTF_8))
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            normalizedUrl.hashCode().toString()
        }

        val memoryHwid = hwidMemoryCache[urlKey]
        if (!memoryHwid.isNullOrBlank()) {
            return memoryHwid
        }

        val context = getApplicationContext()
        if (context != null) {
            val prefs = context.getSharedPreferences("subscription_hwid_store", Context.MODE_PRIVATE)
            val savedHwid = prefs.getString(urlKey, null)
            if (!savedHwid.isNullOrBlank()) {
                hwidMemoryCache[urlKey] = savedHwid
                return savedHwid
            }

            val newHwid = UUID.randomUUID().toString().replace("-", "").take(16)
            prefs.edit().putString(urlKey, newHwid).apply()
            hwidMemoryCache[urlKey] = newHwid
            return newHwid
        }

        val fallbackHwid = urlKey.take(16)
        hwidMemoryCache[urlKey] = fallbackHwid
        return fallbackHwid
    }

    private fun getApplicationContext(): Context? {
        return try {
            val appClass = Class.forName("io.nekohasekai.sfa.Application")
            val field = appClass.getDeclaredField("application")
            field.isAccessible = true
            field.get(null) as? Context
        } catch (e: Exception) {
            try {
                val activityThreadClass = Class.forName("android.app.ActivityThread")
                val currentAppMethod = activityThreadClass.getMethod("currentApplication")
                currentAppMethod.invoke(null) as? Context
            } catch (e2: Exception) {
                null
            }
        }
    }
        private fun processSubscriptionContent(raw: String): String {
        val trimmed = raw.trim()

        if (trimmed.startsWith("{") && (trimmed.contains("\"outbounds\"") || trimmed.contains("\"route\""))) {
            return sanitizeAndMigrateConfig(trimmed)
        }

        val contentToParse = tryDecodeBase64(trimmed)

        if (contentToParse.startsWith("{") && (contentToParse.contains("\"outbounds\"") || contentToParse.contains("\"route\""))) {
            return sanitizeAndMigrateConfig(contentToParse)
        }

        if (contentToParse.startsWith("[")) {
            try {
                val jsonArray = JSONArray(contentToParse)
                val nodes = mutableListOf<JSONObject>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.optJSONObject(i) ?: continue
                    nodes.add(obj)
                }
                if (nodes.isNotEmpty()) {
                    return buildSingBoxConfig(nodes)
                }
            } catch (e: Exception) {
            }
        }

        val nodes = parseUriLines(contentToParse)
        if (nodes.isNotEmpty()) {
            return buildSingBoxConfig(nodes)
        }

        return sanitizeAndMigrateConfig(trimmed)
    }

    private fun sanitizeAndMigrateConfig(jsonStr: String): String {
        return try {
            val fixedRaw = jsonStr
                .replace("https://raw.githubusercontent.com/SagerNet/sing-geosite/rule-set/geosite-ru.srs", "https://raw.githubusercontent.com/SagerNet/sing-geosite/rule-set/geosite-category-ru.srs")
                .replace("\"geosite-ru\"", "\"geosite-category-ru\"")

            val root = JSONObject(fixedRaw)
            val dns = root.optJSONObject("dns")
            if (dns != null) {
                dns.remove("independent_cache")

                if (dns.has("address_strategy")) {
                    val strat = dns.remove("address_strategy")
                    if (!dns.has("strategy")) {
                        dns.put("strategy", strat)
                    }
                }

                val servers = dns.optJSONArray("servers")
                if (servers != null) {
                    val cleanedServers = JSONArray()
                    for (i in 0 until servers.length()) {
                        val server = servers.optJSONObject(i) ?: continue

                        server.remove("strategy")
                        server.remove("address_strategy")

                        if (server.optString("type") == "rcode") {
                            continue
                        }

                        if (server.optString("detour") == "direct") {
                            server.remove("detour")
                        }

                        if (server.has("address")) {
                            val addr = server.remove("address").toString().trim()
                            server.remove("address_resolver")

                            when {
                                addr == "local" || addr.startsWith("local") -> {
                                    server.put("type", "local")
                                }
                                addr.startsWith("https://") -> {
                                    server.put("type", "https")
                                    val cleanAddr = addr.removePrefix("https://")
                                    val host = cleanAddr.substringBefore("/").substringBefore(":")
                                    val path = if (cleanAddr.contains("/")) "/" + cleanAddr.substringAfter("/") else "/dns-query"
                                    val port = cleanAddr.substringBefore("/").substringAfter(":", "").toIntOrNull()
                                    server.put("server", host)
                                    if (port != null && port != 443) server.put("server_port", port)
                                    server.put("path", path)
                                }
                                addr.startsWith("tls://") -> {
                                    server.put("type", "tls")
                                    val cleanAddr = addr.removePrefix("tls://")
                                    val host = cleanAddr.substringBefore(":")
                                    val port = cleanAddr.substringAfter(":", "").toIntOrNull()
                                    server.put("server", host)
                                    if (port != null && port != 853) server.put("server_port", port)
                                }
                                addr.startsWith("tcp://") -> {
                                    server.put("type", "tcp")
                                    val cleanAddr = addr.removePrefix("tcp://")
                                    val host = cleanAddr.substringBefore(":")
                                    val port = cleanAddr.substringAfter(":", "").toIntOrNull()
                                    server.put("server", host)
                                    if (port != null && port != 53) server.put("server_port", port)
                                }
                                addr.startsWith("udp://") -> {
                                    server.put("type", "udp")
                                    val cleanAddr = addr.removePrefix("udp://")
                                    val host = cleanAddr.substringBefore(":")
                                    val port = cleanAddr.substringAfter(":", "").toIntOrNull()
                                    server.put("server", host)
                                    if (port != null && port != 53) server.put("server_port", port)
                                }
                                addr.startsWith("rcode://") -> {
                                    continue
                                }
                                else -> {
                                    server.put("type", "udp")
                                    val host = addr.substringBefore(":")
                                    val port = addr.substringAfter(":", "").toIntOrNull()
                                    server.put("server", host)
                                    if (port != null && port != 53) server.put("server_port", port)
                                }
                            }
                        }

                        server.remove("address")
                        server.remove("address_resolver")

                        cleanedServers.put(server)
                    }
                    dns.put("servers", cleanedServers)
                }
            }

            val inbounds = root.optJSONArray("inbounds")
            if (inbounds != null) {
                for (i in 0 until inbounds.length()) {
                    val inbound = inbounds.optJSONObject(i) ?: continue

                    inbound.remove("sniff")
                    inbound.remove("sniff_override_destination")
                    inbound.remove("domain_strategy")
                    inbound.remove("udp_disable_domain_unmapping")
                    inbound.remove("auto_detect_interface")

                    if (inbound.optString("type") == "tun") {
                        val addresses = JSONArray()
                        if (inbound.has("inet4_address")) {
                            val v = inbound.remove("inet4_address")
                            if (v is JSONArray) {
                                for (j in 0 until v.length()) addresses.put(v.get(j))
                            } else {
                                addresses.put(v)
                            }
                        }
                        if (inbound.has("inet6_address")) {
                            val v = inbound.remove("inet6_address")
                            if (v is JSONArray) {
                                for (j in 0 until v.length()) addresses.put(v.get(j))
                            } else {
                                addresses.put(v)
                            }
                        }
                        if (addresses.length() > 0 && !inbound.has("address")) {
                            inbound.put("address", addresses)
                        }
                    }
                }
            }

            val outbounds = root.optJSONArray("outbounds")
            if (outbounds != null) {
                val cleanedOutbounds = JSONArray()
                for (i in 0 until outbounds.length()) {
                    val ob = outbounds.optJSONObject(i) ?: continue
                    if (ob.optString("type") != "dns") {
                        cleanedOutbounds.put(ob)
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
                    var hasSniff = false
                    for (i in 0 until rules.length()) {
                        val rule = rules.optJSONObject(i) ?: continue
                        if (rule.optString("action") == "sniff") {
                            hasSniff = true
                        }
                        if (rule.optString("protocol") == "dns" || rule.optString("outbound") == "dns-out") {
                            rule.remove("outbound")
                            rule.put("action", "hijack-dns")
                        }
                    }
                    if (!hasSniff) {
                        val newRules = JSONArray()
                        newRules.put(JSONObject().apply { put("action", "sniff") })
                        for (i in 0 until rules.length()) {
                            newRules.put(rules.get(i))
                        }
                        route.put("rules", newRules)
                    }
                }
            }

            root.toString(2)
        } catch (e: Exception) {
            jsonStr
        }
    }

    private fun tryDecodeBase64(text: String): String {
        val trimmed = text.trim()
        if (trimmed.contains("://") || trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return trimmed
        }
        val nonCommentLines = text.lines().filter {
            val t = it.trim()
            t.isNotEmpty() && !t.startsWith("//") && !t.startsWith("#")
        }.joinToString("")

        for (flags in intArrayOf(Base64.DEFAULT, Base64.URL_SAFE)) {
            try {
                val decoded = Base64.decode(nonCommentLines, flags)
                val decodedStr = String(decoded, StandardCharsets.UTF_8).trim()
                if (decodedStr.contains("://") || decodedStr.startsWith("{") || decodedStr.startsWith("[")) {
                    return decodedStr
                }
            } catch (e: Exception) {
            }
        }
        return trimmed
    }

    private fun cleanNodeName(rawTag: String?): String {
        if (rawTag.isNullOrBlank()) return "Proxy"
        val trimmed = rawTag.trim()
        if (!trimmed.contains("%")) return trimmed
        return try {
            URLDecoder.decode(trimmed, StandardCharsets.UTF_8.name()).trim()
        } catch (e: Exception) {
            trimmed
        }
    }
        private fun parseUriLines(text: String): List<JSONObject> {
        val outbounds = mutableListOf<JSONObject>()
        for (rawLine in text.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) continue

            try {
                when {
                    line.startsWith("vless://") -> parseVless(line)?.let { outbounds.add(it) }
                    line.startsWith("vmess://") -> parseVmess(line)?.let { outbounds.add(it) }
                    line.startsWith("trojan://") -> parseTrojan(line)?.let { outbounds.add(it) }
                    line.startsWith("ss://") -> parseShadowsocks(line)?.let { outbounds.add(it) }
                }
            } catch (e: Exception) {
            }
        }
        return outbounds
    }

    private fun parseVless(line: String): JSONObject? {
        val rawTag = if (line.contains("#")) line.substringAfter("#") else ""
        val tag = cleanNodeName(rawTag)

        val withoutTag = line.substringBefore("#")
        val queryStr = if (withoutTag.contains("?")) withoutTag.substringAfter("?") else ""
        val mainPart = withoutTag.substringBefore("?").removePrefix("vless://")

        if (!mainPart.contains("@")) return null

        val uuid = mainPart.substringBefore("@").trim()
        val hostPort = mainPart.substringAfter("@").trim()
        if (uuid.isEmpty() || hostPort.isEmpty()) return null

        val server: String
        val port: Int

        if (hostPort.startsWith("[") && hostPort.contains("]:")) {
            server = hostPort.substringBefore("]:") + "]"
            port = hostPort.substringAfter("]:").toIntOrNull() ?: 443
        } else if (hostPort.contains(":") && !hostPort.startsWith("[")) {
            server = hostPort.substringBeforeLast(":")
            port = hostPort.substringAfterLast(":").toIntOrNull() ?: 443
        } else {
            server = hostPort
            port = 443
        }

        if (server.isEmpty()) return null

        val params = parseQueryParams(queryStr)
        val outbound = JSONObject()
        outbound.put("type", "vless")
        outbound.put("tag", tag)
        outbound.put("server", server)
        outbound.put("server_port", port)
        outbound.put("uuid", uuid)

        params["flow"]?.let { outbound.put("flow", it) }
        outbound.put("packet_encoding", params["packetEncoding"] ?: "xudp")

        val security = params["security"] ?: "none"
        if (security == "tls" || security == "reality") {
            val tlsObj = JSONObject()
            tlsObj.put("enabled", true)
            params["sni"]?.let { tlsObj.put("server_name", it) }

            val utlsObj = JSONObject()
            utlsObj.put("enabled", true)
            utlsObj.put("fingerprint", params["fp"] ?: "chrome")
            tlsObj.put("utls", utlsObj)

            if (security == "reality") {
                val realityObj = JSONObject()
                realityObj.put("enabled", true)
                params["pbk"]?.let { realityObj.put("public_key", it) }
                params["sid"]?.let { realityObj.put("short_id", it) }
                tlsObj.put("reality", realityObj)
            }
            outbound.put("tls", tlsObj)
        }

        val transportType = params["type"] ?: "tcp"
        if (transportType == "ws" || transportType == "grpc" || transportType == "http") {
            val transportObj = JSONObject()
            transportObj.put("type", transportType)
            if (transportType == "ws") {
                params["path"]?.let { transportObj.put("path", URLDecoder.decode(it, "UTF-8")) }
                params["host"]?.let {
                    val headers = JSONObject()
                    headers.put("Host", URLDecoder.decode(it, "UTF-8"))
                    transportObj.put("headers", headers)
                }
            } else if (transportType == "grpc") {
                params["serviceName"]?.let { transportObj.put("service_name", URLDecoder.decode(it, "UTF-8")) }
            }
            outbound.put("transport", transportObj)
        }

        return outbound
    }

    private fun parseVmess(line: String): JSONObject? {
        val b64 = line.removePrefix("vmess://").trim()
        val jsonStr = try {
            String(Base64.decode(b64, Base64.DEFAULT), StandardCharsets.UTF_8)
        } catch (e1: Exception) {
            String(Base64.decode(b64, Base64.URL_SAFE), StandardCharsets.UTF_8)
        }
        val vmessJson = JSONObject(jsonStr)

        val server = vmessJson.optString("add")
        val port = vmessJson.optInt("port", 443)
        val uuid = vmessJson.optString("id")
        val tag = cleanNodeName(vmessJson.optString("ps"))

        val outbound = JSONObject()
        outbound.put("type", "vmess")
        outbound.put("tag", tag)
        outbound.put("server", server)
        outbound.put("server_port", port)
        outbound.put("uuid", uuid)
        outbound.put("alter_id", vmessJson.optInt("aid", 0))
        outbound.put("security", vmessJson.optString("scy", "auto"))

        if (vmessJson.optString("tls").equals("tls", ignoreCase = true)) {
            val tlsObj = JSONObject()
            tlsObj.put("enabled", true)
            val sni = vmessJson.optString("sni").ifEmpty { vmessJson.optString("host") }
            if (sni.isNotEmpty()) tlsObj.put("server_name", sni)
            outbound.put("tls", tlsObj)
        }

        val net = vmessJson.optString("net", "tcp")
        if (net == "ws" || net == "grpc") {
            val transportObj = JSONObject()
            transportObj.put("type", net)
            val path = vmessJson.optString("path")
            if (net == "ws") {
                if (path.isNotEmpty()) transportObj.put("path", path)
                val host = vmessJson.optString("host")
                if (host.isNotEmpty()) {
                    val headers = JSONObject()
                    headers.put("Host", host)
                    transportObj.put("headers", headers)
                }
            } else if (net == "grpc" && path.isNotEmpty()) {
                transportObj.put("service_name", path)
            }
            outbound.put("transport", transportObj)
        }

        return outbound
    }

    private fun parseTrojan(line: String): JSONObject? {
        val rawTag = if (line.contains("#")) line.substringAfter("#") else ""
        val tag = cleanNodeName(rawTag)

        val withoutTag = line.substringBefore("#")
        val queryStr = if (withoutTag.contains("?")) withoutTag.substringAfter("?") else ""
        val mainPart = withoutTag.substringBefore("?").removePrefix("trojan://")

        if (!mainPart.contains("@")) return null

        val password = mainPart.substringBefore("@").trim()
        val hostPort = mainPart.substringAfter("@").trim()
        if (password.isEmpty() || hostPort.isEmpty()) return null

        val server: String
        val port: Int

        if (hostPort.startsWith("[") && hostPort.contains("]:")) {
            server = hostPort.substringBefore("]:") + "]"
            port = hostPort.substringAfter("]:").toIntOrNull() ?: 443
        } else if (hostPort.contains(":") && !hostPort.startsWith("[")) {
            server = hostPort.substringBeforeLast(":")
            port = hostPort.substringAfterLast(":").toIntOrNull() ?: 443
        } else {
            server = hostPort
            port = 443
        }

        if (server.isEmpty()) return null

        val params = parseQueryParams(queryStr)
        val outbound = JSONObject()
        outbound.put("type", "trojan")
        outbound.put("tag", tag)
        outbound.put("server", server)
        outbound.put("server_port", port)
        outbound.put("password", password)

        val tlsObj = JSONObject()
        tlsObj.put("enabled", true)
        tlsObj.put("server_name", params["sni"] ?: params["peer"] ?: server)
        outbound.put("tls", tlsObj)

        val transportType = params["type"] ?: "tcp"
        if (transportType == "ws" || transportType == "grpc") {
            val transportObj = JSONObject()
            transportObj.put("type", transportType)
            if (transportType == "ws") {
                params["path"]?.let { transportObj.put("path", URLDecoder.decode(it, "UTF-8")) }
            } else if (transportType == "grpc") {
                params["serviceName"]?.let { transportObj.put("service_name", URLDecoder.decode(it, "UTF-8")) }
            }
            outbound.put("transport", transportObj)
        }

        return outbound
    }

    private fun parseShadowsocks(line: String): JSONObject? {
        val rawTag = if (line.contains("#")) line.substringAfter("#") else ""
        val tag = cleanNodeName(rawTag)

        val withoutTag = line.substringBefore("#").removePrefix("ss://").trim()
        var method = ""
        var password = ""
        var server = ""
        var port = 8388

        if (withoutTag.contains("@")) {
            val userPart = withoutTag.substringBefore("@")
            val hostPort = withoutTag.substringAfter("@")

            val decodedUserInfo = try {
                String(Base64.decode(userPart, Base64.DEFAULT), StandardCharsets.UTF_8)
            } catch (e: Exception) {
                userPart
            }

            val parts = decodedUserInfo.split(":", limit = 2)
            if (parts.size == 2) {
                method = parts[0]
                password = parts[1]
            }

            if (hostPort.startsWith("[") && hostPort.contains("]:")) {
                server = hostPort.substringBefore("]:") + "]"
                port = hostPort.substringAfter("]:").toIntOrNull() ?: 8388
            } else if (hostPort.contains(":")) {
                server = hostPort.substringBeforeLast(":")
                port = hostPort.substringAfterLast(":").toIntOrNull() ?: 8388
            } else {
                server = hostPort
            }
        } else {
            val decoded = try {
                String(Base64.decode(withoutTag, Base64.DEFAULT), StandardCharsets.UTF_8)
            } catch (e: Exception) {
                try {
                    String(Base64.decode(withoutTag, Base64.URL_SAFE), StandardCharsets.UTF_8)
                } catch (e2: Exception) {
                    ""
                }
            }
            val atSplit = decoded.split("@", limit = 2)
            if (atSplit.size == 2) {
                val creds = atSplit[0].split(":", limit = 2)
                method = creds[0]
                password = creds.getOrElse(1) { "" }
                val hostPort = atSplit[1]
                if (hostPort.startsWith("[") && hostPort.contains("]:")) {
                    server = hostPort.substringBefore("]:") + "]"
                    port = hostPort.substringAfter("]:").toIntOrNull() ?: 8388
                } else if (hostPort.contains(":")) {
                    server = hostPort.substringBeforeLast(":")
                    port = hostPort.substringAfterLast(":").toIntOrNull() ?: 8388
                } else {
                    server = hostPort
                }
            }
        }

        if (server.isEmpty() || method.isEmpty()) return null

        val outbound = JSONObject()
        outbound.put("type", "shadowsocks")
        outbound.put("tag", tag)
        outbound.put("server", server)
        outbound.put("server_port", port)
        outbound.put("method", method)
        outbound.put("password", password)
        return outbound
    }

    private fun parseQueryParams(query: String?): Map<String, String> {
        if (query.isNullOrEmpty()) return emptyMap()
        val result = mutableMapOf<String, String>()
        for (pair in query.split("&")) {
            val idx = pair.indexOf("=")
            if (idx > 0) {
                result[pair.substring(0, idx)] = pair.substring(idx + 1)
            }
        }
        return result
    }
        private fun buildSingBoxConfig(nodes: List<JSONObject>): String {
        val validNodes = nodes.filter { it.optString("type") != "dns" }

        val usedTags = mutableMapOf<String, Int>()
        usedTags["Выбор сервера"] = 1
        usedTags["direct"] = 1
        usedTags["block"] = 1

        val proxyTags = mutableListOf<String>()
        var hasWhitelistServers = false

        for (node in validNodes) {
            val rawTag = node.optString("tag").ifEmpty {
                node.optString("server").ifEmpty { "Proxy" }
            }

            val cleanTag = cleanNodeName(rawTag)
            val count = usedTags.getOrDefault(cleanTag, 0)

            val uniqueTag = if (count > 0) {
                "$cleanTag ($count)"
            } else {
                cleanTag
            }

            usedTags[cleanTag] = count + 1
            node.put("tag", uniqueTag)

            val type = node.optString("type")

            if (type !in listOf(
                    "selector",
                    "urltest",
                    "direct",
                    "block",
                    "dns"
                )
            ) {
                proxyTags.add(uniqueTag)
                if (isWhitelistServer(uniqueTag)) {
                    hasWhitelistServers = true
                }
            }
        }

        val root = JSONObject()

        root.put("log", JSONObject().apply {
            put("level", "warn")
            put("timestamp", true)
        })

        /*
         * DNS: Строгий типизированный формат Sing-box 1.14
         */
        val dnsObj = JSONObject()
        dnsObj.put("servers", JSONArray().apply {
            put(JSONObject().apply {
                put("tag", "dns-direct")
                put("type", "udp")
                put("server", "77.88.8.8")
                put("server_port", 53)
            })

            put(JSONObject().apply {
                put("tag", "dns-remote")
                put("type", "https")
                put("server", "1.1.1.1")
                put("path", "/dns-query")
                put("domain_resolver", "dns-direct")
                put("detour", "Выбор сервера")
            })
        })

        dnsObj.put("rules", JSONArray().apply {
            if (hasWhitelistServers) {
                put(JSONObject().apply {
                    put("domain_suffix", JSONArray().apply {
                        put("nalog.ru")
                        put("gov.ru")
                    })
                    put("server", "dns-direct")
                })
            } else {
                put(JSONObject().apply {
                    put("rule_set", JSONArray().apply {
                        put("geosite-category-ru")
                    })
                    put("server", "dns-direct")
                })
                put(JSONObject().apply {
                    put("domain_suffix", JSONArray().apply {
                        put("ru")
                        put("su")
                        put("xn--p1ai")
                    })
                    put("server", "dns-direct")
                })
            }
        })

        dnsObj.put("final", "dns-remote")
        dnsObj.put("strategy", "ipv4_only")
        root.put("dns", dnsObj)

        /*
         * TUN (без sniff, auto_detect_interface и legacy-полей)
         */
        root.put("inbounds", JSONArray().apply {
            put(JSONObject().apply {
                put("type", "tun")
                put("tag", "tun-in")
                put("interface_name", "tun0")
                put("address", JSONArray().apply {
                    put("172.19.0.1/30")
                })
                put("auto_route", true)
                put("strict_route", false)
                put("stack", "gvisor")
            })
        })

        /*
         * OUTBOUNDS: Единственный селектор "Выбор сервера"
         */
        val outboundsArr = JSONArray()

        val serverSelector = JSONObject().apply {
            put("type", "selector")
            put("tag", "Выбор сервера")
            put("outbounds", JSONArray().apply {
                for (tag in proxyTags) {
                    put(tag)
                }
                put("direct")
            })
            if (proxyTags.isNotEmpty()) {
                put("default", proxyTags.first())
            } else {
                put("default", "direct")
            }
        }
        outboundsArr.put(serverSelector)

        for (node in validNodes) {
            outboundsArr.put(node)
        }

        outboundsArr.put(JSONObject().apply {
            put("type", "direct")
            put("tag", "direct")
        })

        outboundsArr.put(JSONObject().apply {
            put("type", "block")
            put("tag", "block")
        })

        root.put("outbounds", outboundsArr)

        /*
         * ROUTING
         */
        root.put("route", JSONObject().apply {
            put("default_domain_resolver", "dns-direct")

            if (!hasWhitelistServers) {
                put("rule_set", JSONArray().apply {
                    put(JSONObject().apply {
                        put("tag", "geosite-category-ru")
                        put("type", "remote")
                        put("format", "binary")
                        put("url", "https://raw.githubusercontent.com/SagerNet/sing-geosite/rule-set/geosite-category-ru.srs")
                        put("download_detour", "Выбор сервера")
                    })
                    put(JSONObject().apply {
                        put("tag", "geoip-ru")
                        put("type", "remote")
                        put("format", "binary")
                        put("url", "https://raw.githubusercontent.com/SagerNet/sing-geoip/rule-set/geoip-ru.srs")
                        put("download_detour", "Выбор сервера")
                    })
                })
            }

            put("rules", JSONArray().apply {
                put(JSONObject().apply {
                    put("action", "sniff")
                })
                put(JSONObject().apply {
                    put("protocol", "dns")
                    put("action", "hijack-dns")
                })

                if (hasWhitelistServers) {
                    put(JSONObject().apply {
                        put("ip_version", 6)
                        put("outbound", "block")
                    })
                    put(JSONObject().apply {
                        put("ip_is_private", true)
                        put("outbound", "direct")
                    })
                    put(JSONObject().apply {
                        put("protocol", "bittorrent")
                        put("outbound", "direct")
                    })
                    put(JSONObject().apply {
                        put("ip_cidr", JSONArray().apply {
                            put("185.73.195.0/24")
                            put("213.24.64.175/32")
                            put("213.24.64.181/32")
                        })
                        put("outbound", "direct")
                    })
                    put(JSONObject().apply {
                        put("domain_suffix", JSONArray().apply {
                            for (domain in WHITELIST_DOMAINS) {
                                put(domain)
                            }
                        })
                        put("outbound", "direct")
                    })
                } else {
                    put(JSONObject().apply {
                        put("ip_is_private", true)
                        put("outbound", "direct")
                    })
                    put(JSONObject().apply {
                        put("package_name", JSONArray().apply {
                            put("ru.vk.store")
                            put("com.vk.store")
                        })
                        put("outbound", "direct")
                    })
                    put(JSONObject().apply {
                        put("rule_set", JSONArray().apply {
                            put("geosite-category-ru")
                        })
                        put("outbound", "direct")
                    })
                    put(JSONObject().apply {
                        put("rule_set", JSONArray().apply {
                            put("geoip-ru")
                        })
                        put("outbound", "direct")
                    })
                    put(JSONObject().apply {
                        put("domain_suffix", JSONArray().apply {
                            put("ru")
                            put("su")
                            put("xn--p1ai")
                        })
                        put("outbound", "direct")
                    })
                }
            })

            put("final", "Выбор сервера")
            put("auto_detect_interface", true)
        })

        return root.toString(2)
    }

    override fun close() {
        client.close()
    }

    companion object {
        const val userAgent = "SFAxtnd"
    }
}
