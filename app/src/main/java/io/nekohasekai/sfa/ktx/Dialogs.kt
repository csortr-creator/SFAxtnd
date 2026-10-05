package io.nekohasekai.sfa.ktx

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sfa.R

fun Context.errorDialogBuilder(@StringRes messageId: Int): MaterialAlertDialogBuilder =
    errorDialogBuilder(getString(messageId))

fun Context.errorDialogBuilder(message: String): MaterialAlertDialogBuilder {
    val summary =
        when {
            message.contains("fingerprint mismatch", true) ->
                "Сертификат сервера не совпадает с ожидаемым. Проверьте настройки или обновите подписку."
            message.contains("Unsupported", true) ||
                message.contains("cannot be converted", true) ->
                "Не удалось обработать настройки сервера. Проверьте формат подписки и версию приложения."
            message.contains("timeout", true) || message.contains("timed out", true) ->
                "Сервер не ответил вовремя. Проверьте соединение или попробуйте другой сервер."
            message.contains("resolve", true) || message.contains("no such host", true) ->
                "Не удалось определить адрес сервера. Проверьте интернет и настройки DNS."
            message.contains("command.sock", true) || message.contains("rpc error", true) ->
                "Сервис подключения пока недоступен. Дождитесь запуска или повторите подключение."
            message.length <= 220 && message.any { it in 'А'..'я' } -> message
            else -> "Не удалось выполнить действие. Подробности помогут определить причину."
        }
    return MaterialAlertDialogBuilder(this)
        .setTitle(R.string.error_title)
        .setMessage(summary)
        .setNeutralButton("Подробности") { _, _ ->
            MaterialAlertDialogBuilder(this)
                .setTitle("Технические подробности")
                .setView(buildSelectableMessageView(message))
                .setNeutralButton(R.string.per_app_proxy_action_copy) { _, _ ->
                    copyToClipboard(message)
                }
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
        .setPositiveButton(android.R.string.ok, null)
}

fun Context.errorDialogBuilder(exception: Throwable): MaterialAlertDialogBuilder =
    errorDialogBuilder(exception.localizedMessage ?: exception.toString())

private fun Context.buildSelectableMessageView(message: String): ScrollView {
    val density = resources.displayMetrics.density
    val padding = (16 * density).toInt()
    val textView =
        TextView(this).apply {
            text = message
            setTextIsSelectable(true)
            setPadding(padding, padding, padding, padding)
        }
    return ScrollView(this).apply { addView(textView) }
}

private fun Context.copyToClipboard(text: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.error_title), text))
    Toast.makeText(this, getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
}
