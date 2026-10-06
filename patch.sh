#!/bin/bash
set -euo pipefail

# Backport the XHTTP transport onto the pinned core; never silently build without it.
CORE_PATCH="$(dirname "$0")/patches/sing-box-1.14.2-xhttp.patch"
if git -C sing-box-core apply --reverse --check "$PWD/$CORE_PATCH" 2>/dev/null; then
    echo "XHTTP core patch already applied"
else
    git -C sing-box-core apply --check "$PWD/$CORE_PATCH"
    git -C sing-box-core apply "$PWD/$CORE_PATCH"
fi

# Interop fixtures must finish workers before test teardown and avoid
# allocating future listener ports from the outgoing ephemeral range.
FIXTURE_PATCH="$(dirname "$0")/patches/sing-box-1.14.2-test-fixtures.patch"
if git -C sing-box-core apply --reverse --check "$PWD/$FIXTURE_PATCH" 2>/dev/null; then
    echo "Interop fixture patch already applied"
else
    git -C sing-box-core apply --check "$PWD/$FIXTURE_PATCH"
    git -C sing-box-core apply "$PWD/$FIXTURE_PATCH"
fi

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

# Патчим box.go для удаления panic recovery (// TODO: remove catch error)
BOX_GO=""
if [ -f "libbox/box.go" ]; then
    BOX_GO="libbox/box.go"
elif [ -f "sing-box-core/box.go" ]; then
    BOX_GO="sing-box-core/box.go"
fi

if [ -n "$BOX_GO" ]; then
    awk '/\/\/ TODO: remove catch error/ {
        in_defer = 1
        next
    }
    in_defer {
        if ($0 ~ /\}\(\)/) {
            in_defer = 0
        }
        next
    }
    { print }' "$BOX_GO" > "${BOX_GO}.tmp" && mv "${BOX_GO}.tmp" "$BOX_GO"
    echo "✅ Патч $BOX_GO применён"
else
    echo "⚠️ Файл box.go не найден для патчинга!"
fi
