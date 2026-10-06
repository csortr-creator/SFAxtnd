package io.nekohasekai.sfa.utils

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

internal data class SubscriptionImportIssue(val line: Int, val name: String, val reason: String)

internal data class SubscriptionImportReport(
    val received: Int,
    val imported: Int,
    val issues: List<SubscriptionImportIssue>,
) {
    fun summary(): String =
        "Получено: $received · Импортировано: $imported · Пропущено: ${issues.size}"

    fun displayText(): String =
        summary() +
            if (issues.isEmpty()) ""
            else
                "\n\n" +
                    issues.joinToString("\n\n") { "Строка ${it.line} · ${it.name}\n${it.reason}" }

    fun save(configPath: String) {
        val json =
            JSONObject()
                .put("received", received)
                .put("imported", imported)
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
                    )
                }
                .getOrNull()
    }
}

internal data class SubscriptionImportResult(
    val config: String,
    val report: SubscriptionImportReport,
)
