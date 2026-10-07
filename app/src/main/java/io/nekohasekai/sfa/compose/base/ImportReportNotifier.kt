package io.nekohasekai.sfa.compose.base

import io.nekohasekai.sfa.database.Profile
import io.nekohasekai.sfa.utils.SubscriptionImportReport
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal object ImportReportNotifier {
    suspend fun text(profile: Profile): String =
        withContext(Dispatchers.IO) {
            SubscriptionImportReport.read(profile.typed.path)?.displayText()
                ?: run {
                    val outbounds =
                        JSONObject(File(profile.typed.path).readText()).optJSONArray("outbounds")
                    val count =
                        if (outbounds == null) 0
                        else
                            (0 until outbounds.length()).count {
                                outbounds.optJSONObject(it)?.has("server") == true
                            }
                    SubscriptionImportReport(count, count, emptyList(), format = "sing-box JSON")
                        .displayText()
                }
        }

    suspend fun show(profile: Profile) {
        GlobalEventBus.emit(UiEvent.ImportReport(profile.name, text(profile)))
    }
}
