# SFAxtnd

Клиент [sing-box](https://github.com/SagerNet/sing-box) для Android на базе [SFA](https://github.com/SagerNet/sing-box-for-android).

Репозиторий: https://github.com/csortr-creator/SFAxtnd  
Ветка разработки: `dev`

## Возможности

- Подписки: URI (`vless`, `vmess`, `trojan`, `ss`), base64, JSON sing-box
- Стабильный HWID на каждую ссылку подписки
- Режимы маршрутизации NORMAL / Whitelist Bypass (`SubscriptionRouting`)
- Экран **Настройки → Роутинг**: DNS, источники Geo, пользовательские правила (хранение в приложении)
- Проверка обновлений с релизов этого репозитория
- Сборка APK в GitHub Actions (libbox из sing-box **v1.14.2** (stable). Root auto-redirect отключён — API нет в 1.14.x)

## Установка

1. Открой [Releases](https://github.com/csortr-creator/SFAxtnd/releases)
2. Скачай APK под свою архитектуру:

| Файл | Назначение |
|------|------------|
| `SFAxtnd-*-arm64-v8a.apk` | Современные телефоны (рекомендуется) |
| `SFAxtnd-*-armeabi-v7a.apk` | Старые 32-bit устройства |
| `SFAxtnd-*-universal.apk` | Все ABI в одном пакете |

Установка из неизвестных источников должна быть разрешена в системе.

## Обновления в приложении

Источник: GitHub Releases `csortr-creator/SFAxtnd`.  
Версия берётся из тега (`v1.0.xxx`) и APK в assets.

## Сборка (CI)

Workflow: `.github/workflows/build.yml`

- Push в `dev` / `main` / `master` → сборка APK, артефакты Actions
- **GitHub Release** только при:
  - push тега `v*`, или
  - `workflow_dispatch` с `publish_release = true`

Ядро: `git clone --branch v1.14.2` → `go run ./cmd/internal/build_libbox -target android` → `app/libs/libbox.aar`.

## Роутинг (UI)

Пока настройки DNS / Geo / rules сохраняются в `Settings.routingConfigJson` и **не подставляются автоматически** в рабочий конфиг sing-box. Интеграция с генерацией конфига — следующий этап.

## Структура (важное)

```
app/src/main/java/io/nekohasekai/sfa/
  utils/HTTPClient.kt
  utils/SubscriptionRouting.kt
  compose/screen/settings/RoutingSettingsScreen.kt
  models/DnsConfig.kt, RoutingRule.kt, GeoFileSource.kt
app/src/github/java/.../vendor/GitHubUpdateChecker.kt
.github/workflows/build.yml
```

## Лицензия

Форк SFA (Nekohasekai / SagerNet), **GNU GPL v3.0**.  
Название **SFAxtnd** — отдельный продукт, не официальный sing-box / SFA.

## Примечание

Модуль Xposed / hide-VPN hooks удалён для уменьшения размера APK.
