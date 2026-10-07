package io.nekohasekai.sfa.utils

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

internal data class SubscriptionImportIssue(val line: Int, val name: String, val reason: String)

internal data class SubscriptionImportReport(
    val received: Int,
    val imported: Int,
    val issues: List<SubscriptionImportIssue>,
    val warnings: List<String> = emptyList(),
    val format: String = "Список ссылок",
) {
    fun summary(): String =
        "Получено: $received · Импортировано: $imported · Пропущено: ${issues.size}"

    fun displayText(): String = buildString {
        append("Формат: $format\n")
        append(summary())
        if (warnings.isNotEmpty()) append("\n\n" + warnings.joinToString("\n\n"))
        for (issue in issues) {
            val location = if (format == "Список ссылок") "Строка" else "Запись"
            append("\n\n$location ${issue.line} · ${issue.name}\n${issue.reason}")
        }
    }

    fun save(configPath: String) {
        val json =
            JSONObject()
                .put("received", received)
                .put("imported", imported)
                .put("warnings", JSONArray(warnings))
                .put("format", format)
                .put(
                    "issues",
                    JSONArray(
                        issues.map {
                            JSONObject()
                                .put("line", it.line)
                                .put("name", it.name)
                                .put("reason", it.reason)
                        }
                    ),
                )
        File("$configPath.import.json").writeText(json.toString())
    }

    companion object {
        fun read(configPath: String): SubscriptionImportReport? =
            runCatching {
                    val json = JSONObject(File("$configPath.import.json").readText())
                    val array = json.getJSONArray("issues")
                    SubscriptionImportReport(
                        json.getInt("received"),
                        json.getInt("imported"),
                        (0 until array.length()).map { i ->
                            val issue = array.getJSONObject(i)
                            SubscriptionImportIssue(
                                issue.getInt("line"),
                                issue.getString("name"),
                                issue.getString("reason"),
                            )
                        },
                        json.optJSONArray("warnings")?.let { list -> (0 until list.length()).map { list.getString(it) } }.orEmpty(),
                        json.optString("format", "Список ссылок"),
                    )
                }
                .getOrNull()
    }
}

internal data class SubscriptionImportResult(
    val config: String,
    val report: SubscriptionImportReport,
    val profileName: String? = null,
    val updateIntervalMinutes: Int? = null,
)
