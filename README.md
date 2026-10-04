# SFAxtnd

**SFAxtnd** — Android-клиент для [sing-box](https://github.com/SagerNet/sing-box).  
Основан на [sing-box-for-android (SFA)](https://github.com/SagerNet/sing-box-for-android), развивается отдельно под свои сценарии: подписки, маршрутизация, обновления с собственного репозитория.

- Репозиторий: https://github.com/csortr-creator/SFAxtnd  
- Ветка разработки: `dev`  
- Текущая версия: **1.0.0**  
- Лицензия: **GNU GPL v3.0** (как у upstream). Это **не** официальный продукт SagerNet / sing-box.

## Что умеет

### Профили и подписки
- Импорт URI: `vless`, `vmess`, `trojan`, `ss`
- Base64-списки и готовый JSON sing-box
- Стабильный HWID на каждую ссылку подписки (для панелей, которые его требуют)
- Режимы маршрутизации подписки: обычный и Whitelist Bypass
- Редактор профиля, группы, дашборд подключений

### Роутинг (Настройки → Роутинг)
- **DNS**: серверы вручную, strategy, кэш, final; без обязательных пресетов
- **Geo**: опциональный источник rule-set или свои базовые URL для geosite/geoip (`.srs`)
- **Правила**: полноэкранный редактор
  - домен / суффикс / keyword  
  - IP/CIDR, порты, исходный IP/порт  
  - приложения (выбор из установленных)  
  - tag набора `.srs`  
  - сеть, протокол, Wi‑Fi SSID/BSSID, Clash mode  
  - выход: proxy / direct / block  
  - опционально DNS-правило по тем же match  
- При **старте и reload** VPN настройки подмешиваются в рабочий конфиг (файл профиля не перезаписывается)
- После смены роутинга нужен перезапуск или reload сервиса

### Обновления
- Проверка обновлений с **GitHub Releases** этого репозитория
- Выбор APK по ABI устройства

### Сборка и поставка
- GitHub Actions: сборка APK, артефакты на push
- **Release** только по тегу `v*` или ручному `workflow_dispatch` (не на каждый коммит)
- Ядро: **sing-box v1.14.2** (stable) → `libbox.aar`
- Release APK: minify + shrink resources
- ABI: **arm64-v8a**, **armeabi-v7a** (и universal из них)
- Локали в APK: en, ru
- Модуль Xposed / hide-VPN hooks **не входит** (меньше размер, проще поддержка)

### Ограничения (важно)
- Root **auto-redirect** недоступен на stable 1.14.x (API только в более новых ветках ядра)
- Geo URL должны указывать на каталог с файлами `{tag}.srs` в формате sing-box rule-set
- Часть функций upstream (Tailscale/терминал и др.) может присутствовать в коде, но не является фокусом 1.0.0

## Установка

1. Открой [Releases](https://github.com/csortr-creator/SFAxtnd/releases)
2. Скачай APK:

| Файл | Для кого |
|------|----------|
| `SFAxtnd-*-arm64-v8a.apk` | Большинство современных телефонов |
| `SFAxtnd-*-armeabi-v7a.apk` | Старые 32-bit устройства |
| `SFAxtnd-*-universal.apk` | arm64 + armeabi-v7a в одном пакете |

Разреши установку из неизвестных источников при необходимости.

## Сборка у себя

Нужны JDK 17+, Android SDK, NDK (как в CI), Go (см. `version.properties`).

```text
# CI: .github/workflows/build.yml
git clone --depth 1 --branch v1.14.2 https://github.com/SagerNet/sing-box.git
# → build_libbox -target android → app/libs/libbox.aar
# → assemble *Release
```

Версия приложения задаётся в `version.properties`:

```text
VERSION_CODE=100
VERSION_NAME=1.0.0
```

## Структура (основное)

```text
app/src/main/java/io/nekohasekai/sfa/
  utils/HTTPClient.kt              # загрузка и разбор подписок
  utils/SubscriptionRouting.kt     # NORMAL / Whitelist Bypass
  utils/UserRoutingConfig.kt       # DNS / geo / rules → runtime-конфиг
  compose/screen/settings/
    RoutingSettingsScreen.kt
    EditRoutingRuleScreen.kt
  models/DnsConfig.kt, RoutingRule.kt, GeoFileSource.kt
  bg/BoxService.kt                 # start/reload + UserRoutingConfig
app/src/github/.../GitHubUpdateChecker.kt
.github/workflows/build.yml
version.properties
```

## Релиз 1.0.0

Перед публикацией:

1. История `dev` при необходимости схлопывается в аккуратные коммиты (или один baseline)
2. `VERSION_NAME=1.0.0`, тег `v1.0.0`
3. Release через workflow (tag / manual dispatch)

## Лицензия

Форк SFA, **GNU GPL v3.0**.  
Название **SFAxtnd** и этот репозиторий не аффилированы с SagerNet официально.
