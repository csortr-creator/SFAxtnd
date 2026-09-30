#!/bin/bash

# Получаем строку, где кончается класс HTTPClient
LINE=$(grep -n "override fun close()" app/src/main/java/io/nekohasekai/sfa/utils/HTTPClient.kt | cut -d: -f1)

# Вставляем новую функцию перед close()
sed -i "${LINE}i\\
\\
private fun detectModeFromConfig(root: JSONObject): SubscriptionRouting.Mode {\\
    val outbounds = root.optJSONArray(\"outbounds\") ?: return SubscriptionRouting.Mode.NORMAL\\
    for (i in 0 until outbounds.length()) {\\
        val tag = outbounds.optJSONObject(i)?.optString(\"tag\") ?: continue\\
        if (SubscriptionRouting.isWhitelistBypassTag(tag)) {\\
            return SubscriptionRouting.Mode.WHITELIST_BYPASS\\
        }\\
    }\\
    return SubscriptionRouting.Mode.NORMAL\\
}" app/src/main/java/io/nekohasekai/sfa/utils/HTTPClient.kt

# Меняем buildSingBoxConfig на версию с SubscriptionRouting
sed -i 's/return root.toString(2)/SubscriptionRouting.apply(root, detectModeFromConfig(root))\n        return root.toString(2)/' app/src/main/java/io/nekohasekai/sfa/utils/HTTPClient.kt

echo "✅ Патч применён"
