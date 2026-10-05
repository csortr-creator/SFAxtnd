# Аудит неудачных запусков GitHub Actions — 2026-10-05

Репозиторий: `csortr-creator/SFAxtnd`; workflow: `Build SFAxtnd`; ветка: `dev`.

Проверены все 120 неудачных запусков, возвращённых API на момент начала аудита. Скачаны и изучены 118 архивов логов. Два запуска не создали задач и записей checks; API логов возвращает HTTP 404. Для них изучены исторические файлы workflow, полученные по SHA коммитов.

## Результаты и изменения

Ошибки старых исходников уже устранены в текущем коде: отсутствующий импорт `IconButton`, двойное объявление `tunStack`, вызовы удалённого окна выбора профиля, ошибки области видимости и параметров функций маршрутизации, отсутствующие строковые ресурсы, несовместимые настройки Kotlin/Compose и обращения к удалённым Ghostty/Xposed API. До аудита [сборка 37315935021](https://github.com/csortr-creator/SFAxtnd/actions/runs/37315935021) успешно завершилась для коммита `4ab86237d6`.

Исправлены два оставшихся недостатка настроек сборки:

- Коммит `1ce91d9`: версия NDK в `app/build.gradle.kts` изменена с `28.0.13004108` на `28.2.13676358`, как в установке workflow и `ANDROID_NDK_HOME`. В старых логах были предупреждения CXX1100 о несовпадении версий; они не являлись причиной последнего падения компиляции Kotlin.
- Коммит `92c424d`: включены `retries=3` и `retryBackOffMs=1000` для скачивания Gradle. Запуск `37288032679` упал из-за HTTP 500 при единственной попытке загрузки. Wrapper из репозитория проверен с локальным сервером, возвращающим HTTP 500: он выполнил четыре попытки — первоначальную и три повторные. [Документация Gradle](https://docs.gradle.org/current/userguide/gradle_wrapper.html#sec:configuring_wrapper_retries).

У двух запусков без задач (`36413551318`, `36415847763`) повреждена структура параметров Actions: `with` пуст в шагах checkout/setup-java/setup-go/release, а предназначенные для него параметры вставлены внутрь shell-скриптов. По исходникам это объясняет сбой до создания задач; точную аннотацию проверки GitHub через checks API не предоставил. В текущем workflow параметры находятся в правильных блоках.

## Проверка

- `git diff --check` прошёл для обоих изменений настроек.
- Все 58 XML-файлов в `app/src` успешно разобраны XML-парсером.
- Версия NDK совпадает с установленным пакетом и путями окружения workflow.
- Gradle Wrapper из репозитория выполнил три повтора после HTTP 500.
- В текущем репозитории нет gitlinks подмодулей, вызывавших исторические ошибки checkout.
- [Сборка после исправления NDK — 37332369682](https://github.com/csortr-creator/SFAxtnd/actions/runs/37332369682): успешно.
- [Сборка последних изменений — 37333175637](https://github.com/csortr-creator/SFAxtnd/actions/runs/37333175637): задача `build` успешно завершила сборку `libbox`, проверку keystore, `assembleOtherDebug` и подготовку APK. Публикация Release пропущена согласно условию workflow.

Проверка охватывает цель workflow `assembleOtherDebug`. Варианты Release/Play/legacy и работа VPN на устройстве Android не проверялись. Старые запуски сохраняют результаты сборки своих исторических версий исходников.

## Группы ошибок

Каждый запуск отнесён к одной основной категории; компиляция могла сообщить несколько ошибок.

| Категория | Запусков |
| --- | ---: |
| Kotlin source | 78 |
| Submodules | 13 |
| Android resources | 9 |
| Compose compiler plugin | 9 |
| Kotlin dependency versions | 2 |
| Workflow structure | 2 |
| Keystore | 2 |
| Go/libbox linker | 2 |
| Android SDK/NDK setup | 2 |
| Gradle download | 1 |

## Диагностика каждого запуска

| Запуск | Коммит | Категория | Основная диагностика |
| --- | --- | --- | --- |
| [37315933437](https://github.com/csortr-creator/SFAxtnd/actions/runs/37315933437) | `c3d0af5786` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/compose/screen/settings/RoutingSettingsScreen.kt:103:9 Conflicting declarations: |
| [37309597478](https://github.com/csortr-creator/SFAxtnd/actions/runs/37309597478) | `36842b320f` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/compose/screen/dashboard/ProfilesCard.kt:343:29 Unresolved reference 'IconButton'. |
| [37309517609](https://github.com/csortr-creator/SFAxtnd/actions/runs/37309517609) | `f54079a87c` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/compose/screen/dashboard/ProfilesCard.kt:343:29 Unresolved reference 'IconButton'. |
| [37309514373](https://github.com/csortr-creator/SFAxtnd/actions/runs/37309514373) | `c4ecf17fc5` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/compose/screen/dashboard/ProfilesCard.kt:343:29 Unresolved reference 'IconButton'. |
| [37309512665](https://github.com/csortr-creator/SFAxtnd/actions/runs/37309512665) | `c5037bdc09` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/compose/screen/dashboard/DashboardCardRenderer.kt:112:17 No parameter with name 'showProfilePickerSheet' found. |
| [37309509981](https://github.com/csortr-creator/SFAxtnd/actions/runs/37309509981) | `ad7fda2433` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/compose/screen/dashboard/DashboardCardRenderer.kt:112:17 No parameter with name 'showProfilePickerSheet' found. |
| [37309507829](https://github.com/csortr-creator/SFAxtnd/actions/runs/37309507829) | `b623c1edbf` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/compose/screen/dashboard/DashboardCardRenderer.kt:112:17 No parameter with name 'showProfilePickerSheet' found. |
| [37309505905](https://github.com/csortr-creator/SFAxtnd/actions/runs/37309505905) | `7e54f50ee6` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/compose/screen/dashboard/ProfilesCard.kt:346:29 Unresolved reference 'IconButton'. |
| [37309503729](https://github.com/csortr-creator/SFAxtnd/actions/runs/37309503729) | `5511435248` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/compose/screen/dashboard/ProfilesCard.kt:346:29 Unresolved reference 'IconButton'. |
| [37308610189](https://github.com/csortr-creator/SFAxtnd/actions/runs/37308610189) | `7e672f5b5a` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/compose/screen/dashboard/ProfilesCard.kt:346:29 Unresolved reference 'IconButton'. |
| [37308383265](https://github.com/csortr-creator/SFAxtnd/actions/runs/37308383265) | `34c1504b3b` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/compose/screen/dashboard/ProfilesCard.kt:346:29 Unresolved reference 'IconButton'. |
| [37308381572](https://github.com/csortr-creator/SFAxtnd/actions/runs/37308381572) | `23ea8f5b8e` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/compose/screen/dashboard/ProfilesCard.kt:346:29 Unresolved reference 'IconButton'. |
| [37308379373](https://github.com/csortr-creator/SFAxtnd/actions/runs/37308379373) | `d0afdea984` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/compose/screen/dashboard/ProfilesCard.kt:346:29 Unresolved reference 'IconButton'. |
| [37308376011](https://github.com/csortr-creator/SFAxtnd/actions/runs/37308376011) | `484b1f5bc9` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/compose/screen/dashboard/ProfilesCard.kt:346:29 Unresolved reference 'IconButton'. |
| [37308374763](https://github.com/csortr-creator/SFAxtnd/actions/runs/37308374763) | `7e672f5b5a` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/compose/screen/dashboard/ProfilesCard.kt:346:29 Unresolved reference 'IconButton'. |
| [37308369957](https://github.com/csortr-creator/SFAxtnd/actions/runs/37308369957) | `fc182976bb` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/compose/screen/dashboard/ProfilesCard.kt:346:29 Unresolved reference 'IconButton'. |
| [37307323975](https://github.com/csortr-creator/SFAxtnd/actions/runs/37307323975) | `deb8625f71` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/compose/screen/dashboard/ProfilesCard.kt:346:29 Unresolved reference 'IconButton'. |
| [37307321661](https://github.com/csortr-creator/SFAxtnd/actions/runs/37307321661) | `708a15ab17` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/compose/screen/dashboard/ProfilesCard.kt:346:29 Unresolved reference 'IconButton'. |
| [37307319126](https://github.com/csortr-creator/SFAxtnd/actions/runs/37307319126) | `85e70a66b5` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/compose/screen/dashboard/ProfilesCard.kt:346:29 Unresolved reference 'IconButton'. |
| [37307316611](https://github.com/csortr-creator/SFAxtnd/actions/runs/37307316611) | `210c0f74c7` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/compose/screen/dashboard/ProfilesCard.kt:346:29 Unresolved reference 'IconButton'. |
| [37294088164](https://github.com/csortr-creator/SFAxtnd/actions/runs/37294088164) | `6b099a2520` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/compose/screen/dashboard/GroupsCard.kt:117:60 Unresolved reference 'toggleSortByPing'. |
| [37288415657](https://github.com/csortr-creator/SFAxtnd/actions/runs/37288415657) | `9d6baf1908` | Kotlin dependency versions | Dependency Kotlin metadata newer than compiler |
| [37288032679](https://github.com/csortr-creator/SFAxtnd/actions/runs/37288032679) | `6b670a93cf` | Gradle download | Gradle distribution server returned HTTP 500; wrapper retries disabled |
| [37288009286](https://github.com/csortr-creator/SFAxtnd/actions/runs/37288009286) | `6601d32ce1` | Android resources | Missing string resources or malformed/truncated strings.xml |
| [37287945866](https://github.com/csortr-creator/SFAxtnd/actions/runs/37287945866) | `5aade2f866` | Android resources | Missing string resources or malformed/truncated strings.xml |
| [37281658097](https://github.com/csortr-creator/SFAxtnd/actions/runs/37281658097) | `3f10358235` | Kotlin dependency versions | Dependency Kotlin metadata newer than compiler |
| [37278063048](https://github.com/csortr-creator/SFAxtnd/actions/runs/37278063048) | `137f844b40` | Compose compiler plugin | Compose compiler plugin missing or nonexistent Kotlin 1.9.22 plugin version |
| [37277689352](https://github.com/csortr-creator/SFAxtnd/actions/runs/37277689352) | `27e81c4502` | Compose compiler plugin | Compose compiler plugin missing or nonexistent Kotlin 1.9.22 plugin version |
| [37277573346](https://github.com/csortr-creator/SFAxtnd/actions/runs/37277573346) | `591bb2d8e6` | Compose compiler plugin | Compose compiler plugin missing or nonexistent Kotlin 1.9.22 plugin version |
| [37277333281](https://github.com/csortr-creator/SFAxtnd/actions/runs/37277333281) | `813465374b` | Compose compiler plugin | Compose compiler plugin missing or nonexistent Kotlin 1.9.22 plugin version |
| [37277243037](https://github.com/csortr-creator/SFAxtnd/actions/runs/37277243037) | `0b1a023481` | Compose compiler plugin | Compose compiler plugin missing or nonexistent Kotlin 1.9.22 plugin version |
| [37277208652](https://github.com/csortr-creator/SFAxtnd/actions/runs/37277208652) | `125bf562cb` | Compose compiler plugin | Compose compiler plugin missing or nonexistent Kotlin 1.9.22 plugin version |
| [37276598111](https://github.com/csortr-creator/SFAxtnd/actions/runs/37276598111) | `0fbd6ceb05` | Compose compiler plugin | Compose compiler plugin missing or nonexistent Kotlin 1.9.22 plugin version |
| [37276503572](https://github.com/csortr-creator/SFAxtnd/actions/runs/37276503572) | `24c16442e7` | Compose compiler plugin | Compose compiler plugin missing or nonexistent Kotlin 1.9.22 plugin version |
| [37272872481](https://github.com/csortr-creator/SFAxtnd/actions/runs/37272872481) | `17350699c8` | Compose compiler plugin | Compose compiler plugin missing or nonexistent Kotlin 1.9.22 plugin version |
| [37227381457](https://github.com/csortr-creator/SFAxtnd/actions/runs/37227381457) | `72cf736181` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/bg/BoxService.kt:37:33 Unresolved reference 'UserRoutingConfig'. |
| [37227271211](https://github.com/csortr-creator/SFAxtnd/actions/runs/37227271211) | `9e3d9c3b6e` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/utils/HTTPClient.kt:121:20 Unresolved reference 'SubscriptionRouting'. |
| [37198443653](https://github.com/csortr-creator/SFAxtnd/actions/runs/37198443653) | `7b1439dc2b` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/compose/screen/settings/RoutingSettingsScreen.kt:40:35 Unresolved reference 'TonalButton'. |
| [37135091885](https://github.com/csortr-creator/SFAxtnd/actions/runs/37135091885) | `a269d3aa5a` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/utils/HTTPClient.kt:754:41 Overload resolution ambiguity between candidates: |
| [37135077537](https://github.com/csortr-creator/SFAxtnd/actions/runs/37135077537) | `37b24c356a` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/utils/HTTPClient.kt:754:41 Overload resolution ambiguity between candidates: |
| [37131680100](https://github.com/csortr-creator/SFAxtnd/actions/runs/37131680100) | `c1acf79320` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/utils/HTTPClient.kt:754:41 Overload resolution ambiguity between candidates: |
| [37109837133](https://github.com/csortr-creator/SFAxtnd/actions/runs/37109837133) | `390dc37c4d` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/compose/screen/settings/SettingsScreen.kt:175:71 Unresolved reference 'TextSnippet'. |
| [37109836294](https://github.com/csortr-creator/SFAxtnd/actions/runs/37109836294) | `577dd57e68` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/compose/screen/settings/SettingsScreen.kt:175:71 Unresolved reference 'TextSnippet'. |
| [37109835357](https://github.com/csortr-creator/SFAxtnd/actions/runs/37109835357) | `e83fce5143` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/compose/navigation/NavigationDestinations.kt:54:29 Unresolved reference 'title_routing'. |
| [37109834406](https://github.com/csortr-creator/SFAxtnd/actions/runs/37109834406) | `e25d9a631a` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/compose/navigation/NavigationDestinations.kt:54:29 Unresolved reference 'title_routing'. |
| [37109833588](https://github.com/csortr-creator/SFAxtnd/actions/runs/37109833588) | `de6b971022` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/compose/navigation/NavigationDestinations.kt:54:29 Unresolved reference 'title_routing'. |
| [37109832736](https://github.com/csortr-creator/SFAxtnd/actions/runs/37109832736) | `e87a29fda2` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/compose/navigation/NavigationDestinations.kt:54:29 Unresolved reference 'title_routing'. |
| [37040873318](https://github.com/csortr-creator/SFAxtnd/actions/runs/37040873318) | `6281b2ae77` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/compose/screen/settings/RoutingSettingsScreen.kt:593:41 None of the following candidates is applicable: |
| [37013827689](https://github.com/csortr-creator/SFAxtnd/actions/runs/37013827689) | `28d204a171` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/compose/screen/settings/EditRoutingRuleScreen.kt:275:20 No parameter with name 'Modifier' found. |
| [37008387001](https://github.com/csortr-creator/SFAxtnd/actions/runs/37008387001) | `b3243cf01a` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/compose/screen/settings/EditRoutingRuleScreen.kt:275:20 No parameter with name 'Modifier' found. |
| [37008384293](https://github.com/csortr-creator/SFAxtnd/actions/runs/37008384293) | `80cab53d10` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/compose/screen/settings/EditRoutingRuleScreen.kt:275:20 No parameter with name 'Modifier' found. |
| [37008383417](https://github.com/csortr-creator/SFAxtnd/actions/runs/37008383417) | `3a01b299ef` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/compose/screen/settings/EditRoutingRuleScreen.kt:275:20 No parameter with name 'Modifier' found. |
| [36998287366](https://github.com/csortr-creator/SFAxtnd/actions/runs/36998287366) | `41d5a341dc` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/compose/screen/tools/ToolsScreen.kt:546:84 Unresolved reference 'DEFAULT_SSH_USERNAME'. |
| [36990486089](https://github.com/csortr-creator/SFAxtnd/actions/runs/36990486089) | `8a95968239` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/compose/screen/tools/ToolsScreen.kt:546:84 Unresolved reference 'DEFAULT_SSH_USERNAME'. |
| [36990484254](https://github.com/csortr-creator/SFAxtnd/actions/runs/36990484254) | `62bd47f03f` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/compose/screen/tools/ToolsScreen.kt:546:84 Unresolved reference 'DEFAULT_SSH_USERNAME'. |
| [36990437030](https://github.com/csortr-creator/SFAxtnd/actions/runs/36990437030) | `c1d614673a` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/compose/screen/tools/ToolsScreen.kt:546:84 Unresolved reference 'DEFAULT_SSH_USERNAME'. |
| [36990434870](https://github.com/csortr-creator/SFAxtnd/actions/runs/36990434870) | `3027aa221b` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/compose/screen/tools/ToolsScreen.kt:546:84 Unresolved reference 'DEFAULT_SSH_USERNAME'. |
| [36990431960](https://github.com/csortr-creator/SFAxtnd/actions/runs/36990431960) | `af590cfc7b` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/compose/screen/settings/TailscaleFontPickerScreen.kt:47:18 Unresolved reference 'sagernet'. |
| [36990433042](https://github.com/csortr-creator/SFAxtnd/actions/runs/36990433042) | `8333eae57d` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/compose/screen/tools/ToolsScreen.kt:546:84 Unresolved reference 'DEFAULT_SSH_USERNAME'. |
| [36990429872](https://github.com/csortr-creator/SFAxtnd/actions/runs/36990429872) | `c191641faa` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/compose/screen/settings/TailscaleFontPickerScreen.kt:47:18 Unresolved reference 'sagernet'. |
| [36990427712](https://github.com/csortr-creator/SFAxtnd/actions/runs/36990427712) | `b93d2285f3` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/compose/screen/settings/TailscaleFontPickerScreen.kt:47:18 Unresolved reference 'sagernet'. |
| [36990425088](https://github.com/csortr-creator/SFAxtnd/actions/runs/36990425088) | `e57c9844d7` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/compose/screen/settings/TailscaleFontPickerScreen.kt:47:18 Unresolved reference 'sagernet'. |
| [36990426426](https://github.com/csortr-creator/SFAxtnd/actions/runs/36990426426) | `ee1ebafaf3` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/compose/screen/settings/TailscaleFontPickerScreen.kt:47:18 Unresolved reference 'sagernet'. |
| [36990423839](https://github.com/csortr-creator/SFAxtnd/actions/runs/36990423839) | `6e7fbea88e` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/compose/screen/settings/TailscaleFontPickerScreen.kt:47:18 Unresolved reference 'sagernet'. |
| [36978369843](https://github.com/csortr-creator/SFAxtnd/actions/runs/36978369843) | `05f54da18f` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/compose/screen/settings/RoutingSettingsScreen.kt:191:20 No parameter with name 'Modifier' found. |
| [36976901607](https://github.com/csortr-creator/SFAxtnd/actions/runs/36976901607) | `ebd63b303b` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/compose/screen/settings/RoutingSettingsScreen.kt:192:20 No parameter with name 'Modifier' found. |
| [36774601568](https://github.com/csortr-creator/SFAxtnd/actions/runs/36774601568) | `302056d495` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/compose/screen/settings/EditRoutingRuleScreen.kt:100:21 Unresolved reference 'decodeRules'. |
| [36761272832](https://github.com/csortr-creator/SFAxtnd/actions/runs/36761272832) | `d85d15767b` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/compose/screen/settings/EditRoutingRuleScreen.kt:340:16 No parameter with name 'Modifier' found. |
| [36752429398](https://github.com/csortr-creator/SFAxtnd/actions/runs/36752429398) | `1df04fdaeb` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/compose/screen/settings/EditRoutingRuleScreen.kt:335:16 No parameter with name 'Modifier' found. |
| [36752427305](https://github.com/csortr-creator/SFAxtnd/actions/runs/36752427305) | `db825e8b80` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/compose/screen/settings/EditRoutingRuleScreen.kt:335:16 No parameter with name 'Modifier' found. |
| [36752317161](https://github.com/csortr-creator/SFAxtnd/actions/runs/36752317161) | `884ca6665b` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/compose/screen/settings/EditRoutingRuleScreen.kt:335:16 No parameter with name 'Modifier' found. |
| [36752315244](https://github.com/csortr-creator/SFAxtnd/actions/runs/36752315244) | `336c6c295a` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/compose/screen/settings/EditRoutingRuleScreen.kt:335:16 No parameter with name 'Modifier' found. |
| [36752313621](https://github.com/csortr-creator/SFAxtnd/actions/runs/36752313621) | `d7af2bb323` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/compose/screen/settings/EditRoutingRuleScreen.kt:335:16 No parameter with name 'Modifier' found. |
| [36752311248](https://github.com/csortr-creator/SFAxtnd/actions/runs/36752311248) | `ee19212f5e` | Android resources | Missing string resources or malformed/truncated strings.xml |
| [36752308185](https://github.com/csortr-creator/SFAxtnd/actions/runs/36752308185) | `e600e80eee` | Android resources | Missing string resources or malformed/truncated strings.xml |
| [36752304064](https://github.com/csortr-creator/SFAxtnd/actions/runs/36752304064) | `4b2c281ff0` | Android resources | Missing string resources or malformed/truncated strings.xml |
| [36752305410](https://github.com/csortr-creator/SFAxtnd/actions/runs/36752305410) | `fe40302aab` | Android resources | Missing string resources or malformed/truncated strings.xml |
| [36743320720](https://github.com/csortr-creator/SFAxtnd/actions/runs/36743320720) | `6817c5811e` | Android resources | Missing string resources or malformed/truncated strings.xml |
| [36743281061](https://github.com/csortr-creator/SFAxtnd/actions/runs/36743281061) | `ec619cf7c8` | Android resources | Missing string resources or malformed/truncated strings.xml |
| [36742454784](https://github.com/csortr-creator/SFAxtnd/actions/runs/36742454784) | `837a00f70f` | Android resources | Missing string resources or malformed/truncated strings.xml |
| [36742453157](https://github.com/csortr-creator/SFAxtnd/actions/runs/36742453157) | `d1a811b1e2` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/compose/screen/settings/EditRoutingRuleScreen.kt:335:16 No parameter with name 'Modifier' found. |
| [36742449776](https://github.com/csortr-creator/SFAxtnd/actions/runs/36742449776) | `b19465af30` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/compose/screen/settings/EditRoutingRuleScreen.kt:335:16 No parameter with name 'Modifier' found. |
| [36741889119](https://github.com/csortr-creator/SFAxtnd/actions/runs/36741889119) | `7f3c578252` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/utils/UserRoutingConfig.kt:367:31 Unresolved reference 'geoToRuleSet'. |
| [36741889641](https://github.com/csortr-creator/SFAxtnd/actions/runs/36741889641) | `5632128658` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/utils/UserRoutingConfig.kt:367:31 Unresolved reference 'geoToRuleSet'. |
| [36741342006](https://github.com/csortr-creator/SFAxtnd/actions/runs/36741342006) | `b4283ffd9c` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/utils/UserRoutingConfig.kt:367:31 Unresolved reference 'geoToRuleSet'. |
| [36741342527](https://github.com/csortr-creator/SFAxtnd/actions/runs/36741342527) | `83838976ba` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/utils/UserRoutingConfig.kt:367:31 Unresolved reference 'geoToRuleSet'. |
| [36741339165](https://github.com/csortr-creator/SFAxtnd/actions/runs/36741339165) | `1b0455706d` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/utils/UserRoutingConfig.kt:367:31 Unresolved reference 'geoToRuleSet'. |
| [36741336685](https://github.com/csortr-creator/SFAxtnd/actions/runs/36741336685) | `1a3c594ed2` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/utils/UserRoutingConfig.kt:367:31 Unresolved reference 'geoToRuleSet'. |
| [36672454449](https://github.com/csortr-creator/SFAxtnd/actions/runs/36672454449) | `6ed30165d8` | Kotlin source | app/src/minApi24/java/io/nekohasekai/sfa/vendor/PackageQueryManager.kt:10:33 Unresolved reference 'HookStatusClient'. |
| [36672454134](https://github.com/csortr-creator/SFAxtnd/actions/runs/36672454134) | `1cdbb327b9` | Kotlin source | app/src/other/java/io/nekohasekai/sfa/vendor/ApkInstaller.kt:8:33 Unresolved reference 'HookStatusClient'. |
| [36612161875](https://github.com/csortr-creator/SFAxtnd/actions/runs/36612161875) | `290514d640` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/bg/DebugInfoExporter.kt:5:33 Unresolved reference 'HookErrorClient'. |
| [36590520053](https://github.com/csortr-creator/SFAxtnd/actions/runs/36590520053) | `d90a20099b` | Submodules | No url found for submodule path SFAxtnd in .gitmodules |
| [36590518732](https://github.com/csortr-creator/SFAxtnd/actions/runs/36590518732) | `10721fc61f` | Submodules | No url found for submodule path SFAxtnd in .gitmodules |
| [36584783764](https://github.com/csortr-creator/SFAxtnd/actions/runs/36584783764) | `f09ae0a465` | Submodules | No url found for submodule path SFAxtnd in .gitmodules |
| [36584781506](https://github.com/csortr-creator/SFAxtnd/actions/runs/36584781506) | `e4153f1685` | Submodules | No url found for submodule path SFAxtnd in .gitmodules |
| [36584779824](https://github.com/csortr-creator/SFAxtnd/actions/runs/36584779824) | `b103c6ec20` | Submodules | No url found for submodule path SFAxtnd in .gitmodules |
| [36576640132](https://github.com/csortr-creator/SFAxtnd/actions/runs/36576640132) | `c8e8bb6a73` | Submodules | No url found for submodule path SFAxtnd in .gitmodules |
| [36576637256](https://github.com/csortr-creator/SFAxtnd/actions/runs/36576637256) | `b1ab4f1152` | Submodules | No url found for submodule path SFAxtnd in .gitmodules |
| [36540634334](https://github.com/csortr-creator/SFAxtnd/actions/runs/36540634334) | `aff42d37bf` | Submodules | No url found for submodule path SFAxtnd in .gitmodules |
| [36532464428](https://github.com/csortr-creator/SFAxtnd/actions/runs/36532464428) | `c9d207d67d` | Submodules | No url found for submodule path SFAxtnd in .gitmodules |
| [36530751277](https://github.com/csortr-creator/SFAxtnd/actions/runs/36530751277) | `74b40bee3f` | Submodules | No url found for submodule path SFAxtnd in .gitmodules |
| [36528520826](https://github.com/csortr-creator/SFAxtnd/actions/runs/36528520826) | `4e49e4a561` | Submodules | No url found for submodule path SFAxtnd in .gitmodules |
| [36525581803](https://github.com/csortr-creator/SFAxtnd/actions/runs/36525581803) | `84b814f1dc` | Submodules | No url found for submodule path SFAxtnd in .gitmodules |
| [36415847763](https://github.com/csortr-creator/SFAxtnd/actions/runs/36415847763) | `f538b4b4fc` | Workflow structure | No jobs/check runs; historical workflow has empty with blocks and misplaced action inputs |
| [36413551318](https://github.com/csortr-creator/SFAxtnd/actions/runs/36413551318) | `cbea7f8b03` | Workflow structure | No jobs/check runs; historical workflow has empty with blocks and misplaced action inputs |
| [36401750151](https://github.com/csortr-creator/SFAxtnd/actions/runs/36401750151) | `22000661fb` | Submodules | No url found for submodule path SFAxtnd in .gitmodules |
| [35017258469](https://github.com/csortr-creator/SFAxtnd/actions/runs/35017258469) | `9757492e09` | Keystore | Invalid/truncated keystore; keytool EOFException or Failed to read key |
| [35015258824](https://github.com/csortr-creator/SFAxtnd/actions/runs/35015258824) | `21d534ff62` | Keystore | Invalid/truncated keystore; keytool EOFException or Failed to read key |
| [34360566785](https://github.com/csortr-creator/SFAxtnd/actions/runs/34360566785) | `9b13c4ed72` | Kotlin source | app/src/github/java/io/nekohasekai/sfa/vendor/ApkDownloader.kt:27:41 Unresolved reference 'userAgent'. |
| [34324033504](https://github.com/csortr-creator/SFAxtnd/actions/runs/34324033504) | `e2a3ea3d4a` | Kotlin source | app/src/github/java/io/nekohasekai/sfa/vendor/ApkDownloader.kt:27:41 Unresolved reference 'userAgent'. |
| [34321765044](https://github.com/csortr-creator/SFAxtnd/actions/runs/34321765044) | `87870e4d89` | Kotlin source | app/src/github/java/io/nekohasekai/sfa/vendor/ApkDownloader.kt:27:41 Unresolved reference 'userAgent'. |
| [34320543462](https://github.com/csortr-creator/SFAxtnd/actions/runs/34320543462) | `2cf99cfcac` | Kotlin source | app/src/github/java/io/nekohasekai/sfa/vendor/ApkDownloader.kt:27:41 Unresolved reference 'userAgent'. |
| [34317563630](https://github.com/csortr-creator/SFAxtnd/actions/runs/34317563630) | `e4a8f086ba` | Kotlin source | app/src/github/java/io/nekohasekai/sfa/vendor/ApkDownloader.kt:27:41 Unresolved reference 'userAgent'. |
| [34313556368](https://github.com/csortr-creator/SFAxtnd/actions/runs/34313556368) | `fabfdb424c` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/utils/HTTPClient.kt:165:16 Unresolved reference 'processSubscriptionContent'. |
| [34311383148](https://github.com/csortr-creator/SFAxtnd/actions/runs/34311383148) | `e89d0841bf` | Kotlin source | app/src/main/java/io/nekohasekai/sfa/utils/HTTPClient.kt:165:16 Unresolved reference 'processSubscriptionContent'. |
| [34230364977](https://github.com/csortr-creator/SFAxtnd/actions/runs/34230364977) | `d8960209eb` | Kotlin source | app/src/github/java/io/nekohasekai/sfa/vendor/ApkDownloader.kt:27:41 Unresolved reference 'userAgent'. |
| [34144884508](https://github.com/csortr-creator/SFAxtnd/actions/runs/34144884508) | `d6b63b14e7` | Go/libbox linker | invalid reference to os.checkPidfdOnce |
| [34144700165](https://github.com/csortr-creator/SFAxtnd/actions/runs/34144700165) | `69de261831` | Android SDK/NDK setup | sdkmanager: command not found |
| [34143876149](https://github.com/csortr-creator/SFAxtnd/actions/runs/34143876149) | `2c4cc68975` | Android SDK/NDK setup | NDK r28d download returned HTTP 404 |
| [34141872936](https://github.com/csortr-creator/SFAxtnd/actions/runs/34141872936) | `c7e3a6e7f4` | Go/libbox linker | invalid reference to os.checkPidfdOnce |
