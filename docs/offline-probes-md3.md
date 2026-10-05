# Offline server checks and Material 3 refresh

The server list observes selected-profile preferences and profile updates. Lists,
subscription titles and probe results are scoped to the selected profile; stale
results are discarded when its content changes. Clearing or removing a profile
also clears its offline list.

Before connecting, URL tests create an isolated native sing-box instance with only
the requested outbounds and their dependencies, Android's underlying network and
local DNS. It opens no TUN, command socket, API listeners or cache files. Tests use
the actual configured protocol, including UDP-based Hysteria, with four concurrent
requests and a 12-second timeout. Selecting another subscription, starting VPN or
closing the ViewModel cancels the session. An unreachable server is shown in the
list rather than generating a modal error for every failed request.

Offline selections are saved per profile, validated against current membership,
and passed to the native selector at startup. The patched `force_default` selector
option prevents a previous native cache entry from overriding that explicit choice.
Online selections continue to use the command server and are also saved locally.

The routing page uses shared MD3 sections, outlined icons, restrained dividers,
accessible controls and animated disclosure. Presets and routing rules stay visible;
DNS, network options and geodata sources expand on demand. Servers, subscriptions,
navigation, status controls and static light/dark palettes use matching MD3 roles.
System dynamic colors remain supported.

## Russia Whitelist preset

The preset uses the maintained domain and IP lists from hydraponique:

- https://github.com/hydraponique/roscomvpn-geosite
- https://github.com/hydraponique/roscomvpn-geoip

Actual SRS URLs are recorded in `RoutingPresets.kt`. Both were downloaded and
decoded with sing-box 1.14.2: domain SRS version 3, IP SRS version 1. The preset
replaces the broad Russian-direct preset, preserves other user rules, routes its
allowed domains/IPs directly and routes remaining traffic through the profile's
actual proxy selector. Existing sniff/DNS interception actions run first. Disabling
both whitelist rules removes the catch-all policy on the next connection. Local
network access and IPv6 blocking remain before the catch-all. IPv6 settings apply
at startup, including when switching the setting on an existing subscription.

Runetfreedom also documents `ru-whitelist` IP ranges sourced from
https://github.com/hxehex/russia-mobile-internet-whitelist. Hydraponique was chosen
because it provides both domain and IP lists together. Lists update through the
existing rule-set updater and configured update interval. The APK includes verified
bootstrap SRS snapshots so first use does not require reaching GitHub before VPN
connects. Runetfreedom's `geoip-ru-whitelist.srs` was also downloaded and decoded.

## Validation

- JVM tests exercise production config transforms and routing presets, including
  dependency pruning, protocol preservation, cache override, idempotence, DNS
  ordering and disabling the preset. Existing import tests remain enabled.
- Native race tests cover real HTTP checks, missing outbounds, cancelling an active
  request, closing repeatedly and overriding a persisted selector choice.
- CI builds patched libbox and all Android APK variants.
- Phone acceptance checks: switch subscriptions while stopped; select a server and
  connect; run checks before connecting; switch subscriptions/start VPN during a
  check; try both themes and large text; edit/toggle/delete route and DNS entries.
