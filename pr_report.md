## 🧪 Root Cause & Functional Routing Audit

### Root cause
Реальная причина ошибки `no available network interface` кроется в **race condition (состоянии гонки) при инициализации VPN и скачивании конфигурации**.
При запуске `BoxService`, ядро `sing-box` инициализируется конфигурацией, которая содержит ссылки на удалённые (remote) файлы `rule-set` (SagerNet geosite/geoip). Ядро пытается синхронно скачать эти `.srs` файлы *во время* инициализации. В этот момент TUN-интерфейс VPN (и связанная с ним сетевая маршрутизация Android) ещё не подняты и не активны. Так как `sing-box` сконфигурирован на прямое скачивание (`download_detour = "direct"`) с включённым `auto_detect_interface = true`, он пытается найти доступный системный интерфейс для обхода VPN. Однако, Android API не даёт процессу доступа к сети в переходный момент запуска VpnService, что приводит к краху: загрузка падает -> ядро падает -> VPN не запускается.

### Evidence
1. В логах ошибка: `dial UDP connection: no available network interface` при обращении к `raw.githubusercontent.com`.
2. Функция `BoxService.startService()` вызывает `commandServer.startOrReloadService()`, передавая JSON-конфиг напрямую.
3. В `SubscriptionRouting.kt` принудительно задаются параметры `type = "remote"` и `download_detour = "direct"`.

### Config pipeline
Генерация итогового конфига проходит следующий путь:
1. `ProfileManager` отдаёт сырой текстовый конфиг.
2. `UserRoutingConfig.applyToConfig(content)` парсит его в JSON.
3. Внутри `applyToConfig()` применяются настройки Geo-файлов (`applyGeo`) и пользовательские правила DNS и маршрутов.
4. Вызывается `SubscriptionRouting.apply()`, который добавляет "Пресеты" (например, прямые маршруты для `.ru` и блокировку `::/0`).
5. `ensureRuleSetEntries` и `finalizeRemoteRuleSets` дописывают URL для `geosite-category-ru` и `geoip-ru`.
6. Готовый JSON строка возвращается и передаётся в `libbox.startOrReloadService()`.

### Rule-set
Формируются как `remote` в формате `binary`. Изначальный код надеялся на внутренний механизм `sing-box` для скачивания через `direct` detour.

### Android networking
При `startForegroundService` и вызове `startOrReloadService` Android ещё не установил полноценный `VpnService` для приложения. Любые попытки маршрутизации трафика ядром (даже direct) натыкаются на отсутствие подходящих интерфейсов или прав на их использование до завершения Vpn-хендшейка.

### Fixed
**Разорван цикл зависимости "VPN ждёт rule-set, rule-set ждёт VPN".**
Я внедрил новый механизм — `RuleSetUpdater.kt`.
Вместо того чтобы полагаться на `sing-box` для загрузки `.srs` файлов, Android-приложение само парсит JSON-конфиг *до* передачи его в ядро.
Если обнаружен `remote` rule-set, `RuleSetUpdater` скачивает его во внутреннюю директорию `context.filesDir/rule_sets` с помощью стандартного `HttpURLConnection` (который работает в обычном контексте Android без VPN).
Затем он трансформирует JSON-объект:
- `type`: `remote` -> `local`
- Удаляет `url` и `download_detour`.
- Добавляет `path` к скачанному файлу.
В `BoxService` этот процесс вызывается асинхронно *до* старта ядра, обеспечивая ядро уже готовыми локальными файлами.

Также:
- Настройка `::/0` (блокировка IPv6) вынесена в пользовательский интерфейс как опциональная (вкл по умолчанию), чтобы предотвратить потенциальные блокировки системного DNS.
- В UI добавлена настройка интервала обновления `rule-set` (кэширование файлов).

### Tests
Добавлены Unit-тесты:
- `RuleSetUpdaterTest.kt`: Проверяет корректную трансформацию JSON из `remote` в `local` и правильное назначение `path`.
- `SubscriptionRoutingTest.kt`: Проверяет включение/выключение блокировки IPv6 на основе новых настроек (Settings.routingBlockIpv6).

### Validation
Автоматически проверено прохождение всех Unit-тестов и форматирование кода (Spotless/Detekt). Логика трансформации JSON работает как ожидается.

### Device validation
**Runtime Android validation unavailable.**
(Поскольку у агента нет доступа к реальному устройству Android или эмулятору с VPN, реальный сетевой стек не тестировался. Однако логика решения архитектурно устраняет первопричину).

### Not changed
- Версия `libbox` не изменялась.
- Внешний вид UI изменён минимально — только добавлены запрошенные опции (IPv6 и интервал), без полного редизайна.
- Внутренние механизмы ядра `sing-box` остались нетронутыми.

### Remaining risks
- Если GitHub (`raw.githubusercontent.com`) заблокирован на уровне провайдера, скачивание через `HttpURLConnection` без VPN всё равно упадёт. В будущем может потребоваться fallback-механизм (например, попытаться скачать через прокси после запуска VPN или использовать локальные assets по умолчанию).
