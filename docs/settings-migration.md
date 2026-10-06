# Settings migration (sing-box 1.14.2)

The root navigation is Servers, Subscriptions, Settings. Routing moves to
Settings with a back button; existing route names remain available for links.

Settings has separate Routing, DNS, Core, Appearance, Android VPN service,
updates/behavior and diagnostics destinations. Developer attribution links to
csortr-creator; this does not claim authorship of upstream sing-box.

Existing key/value settings, routing rules, DNS additions, selected subscriptions,
server choices and ping cache are retained. Routing updates preserve other JSON
sections and new DNS fields. Stored subscription files are never rewritten by
these settings; options are applied to the runtime configuration.

Routing supports domain/suffix/keyword, IP/CIDR, geosite/geoip mapped to SRS,
protocol, network, ports/ranges, application and Wi-Fi criteria. Explicit outbound
selection is populated from the selected subscription; when switching to a profile
without that outbound, runtime falls back to the profile selector. Rule order can
be changed. Local SRS/source JSON files are copied into app-private storage and
can be selected by tag; remote files use the existing cached rule-set updater.
Rules must release a custom list before it can be removed.

DNS custom mode is opt-in. Servers are grouped into direct/proxy chains with
ordered evaluate/respond actions. The next query starts on error, DNS failure or
a per-group timeout, rather than querying all servers simultaneously. IP-only
modes produce an empty NOERROR answer for the excluded A/AAAA type. IP preference
applies to internal domain resolution: direct endpoint resolver and default DNS
strategy. Forwarded DNS packets keep the requesting application's query type.
Custom DNS hostname endpoints bootstrap via a local resolver to avoid cycles.
Optimistic caching, cache size, reverse mapping, per-group strategy and timeout,
DNS hijack, FakeIP and presets are configurable. Specific domain rules precede
FakeIP; endpoint bootstrap precedes both. Legacy DNS address filters are migrated
to explicit response matching before evaluate/respond is enabled.

Core options include system/gvisor/mixed, an optional MTU override, tunnel IPv4
or dual-stack addresses, IPv6 rejection, strict routing, process detection and
log level. Sniffing protocols and timeout and list update frequency belong to
routing. DNS hijack belongs to DNS. Inherited values stay inherited until edited.

Appearance supports system/light/dark, Android dynamic colors, blue/green/purple/
amber accents, black dark background and 85/100/115/130% text scale, applied on top
of Android's accessibility font scale. Changes are immediate.

The reported `outbound/block[block]: operation not permitted` log means a routing
rule intentionally blocked the connection, not a socket permission failure.
Block routes now use the native `reject` action, classified as a normal rejection
by the router. The screenshot alone does not identify the matching rule/domain;
the ad preset is a possibility but cannot be confirmed from an IP-only message.

Validation: JVM regression tests exercise settings transformations and export
configs for the actual 1.14.2 parser/router constructor. Go tests exercise ordered
DNS timeout fallback and assert no backup query after primary success. Android
compilation and APK generation run in GitHub Actions. Device UI/network testing
requires an Android device and remains separate from those checks.

Follow-up: Appearance includes group/subscription vs active-server notification
titles, refreshed on selector changes even with speed notifications disabled.
Nested selections resolve to their leaf; absent/cyclic selections retain the
subscription name. The duplicate Groups sheet/rail item and footer shortcut are
removed, with old links redirected to Servers. The subscription picker remains.
Manual URL tests use an immediate client-side gate and a non-queuing core RPC
lock; the RPC acknowledges actual completion. Loading indicators remain active
throughout the test, including failed probes. Native regression tests block a
dial to assert the RPC waits and ten duplicate requests start no extra probes.
