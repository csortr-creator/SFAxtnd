# Xray capability matrix (A+)

**Scope:** outbound / share-link / Clash proxy import only.  
**Base:** SFAxtnd `dev`, sing-box **1.14.2** + local patches in `patches/`.  
**Strategy:** Xray Compatibility Layer on sing-box — not a second core.

## Evidence levels (do not conflate with status)

| Level | Meaning |
|-------|---------|
| **PARSED** | Importer recognizes the field |
| **MAPPED** | Field written into generated sing-box JSON at a known path |
| **CONFIG_VALIDATED** | `Libbox.checkConfig` / `sing-box check` / native-config fixture exercised in tests |
| **CORE_TESTED** | Go/core tests or CI interop against patched sing-box (see `patches/README.md`) |
| **RUNTIME_VERIFIED** | Live connection against a real server observed (device / instrumented) |

If a cell is empty or marked **UNVERIFIED**, that level has **not** been proven.  
**Status** describes functional intent in code; it is **not** a claim of RUNTIME_VERIFIED.

Statuses: `EXTENDED` | `PARTIAL` | `IGNORED` | `UNSUPPORTED` (avoid `FULL` unless CONFIG_VALIDATED+ and not only parser).

## Contract: unknown fields (A+ stage 1)

| Kind | Behaviour |
|------|-----------|
| Unknown **optional** share-link query keys | **PARSED** as unknown → warning in `SubscriptionImportReport`; **not MAPPED** into outbound |
| Unknown / invalid **critical** fields (e.g. Reality without `pbk`, bad XHTTP mode) | Import **fails** for that line (`issues`) |
| XHTTP nested unknown keys | **Strict** `error()` in `ProxyLinkParser.xhttp` (not soft-tolerate) |
| Xray routing / DNS / inbounds | **IGNORED** by design (app routing / profile) |

Code: `XrayCompatibility.kt`, `SubscriptionContentParser.tolerateParameters`, `ProxyLinkParser.partitionParameters`.

---

## Matrix

| Feature | Xray source | Import formats | Parser | Internal / generated path | Upstream 1.14.2 | Local patch | PARSED | MAPPED | CONFIG_VALIDATED | CORE_TESTED | RUNTIME_VERIFIED | Status | Limits |
|---------|-------------|----------------|--------|---------------------------|-----------------|-------------|--------|--------|------------------|-------------|------------------|--------|--------|
| VLESS base | uuid@host:port | share, Clash, Xray JSON | `parseVless` / Foreign | `type=vless`, `server`, `server_port`, `uuid` | yes | — | yes | yes | partial | — | **UNVERIFIED** | EXTENDED | |
| VMess / Trojan / SS base | share / Clash | share, Clash | parsers | native outbound types | yes | — | yes | yes | partial | — | **UNVERIFIED** | EXTENDED | |
| Reality `pbk` | `pbk` | VLESS share, JSON | `applyReality` | `tls.reality.public_key` | yes | — | yes | yes | — | — | **UNVERIFIED** | EXTENDED | 32-byte URL-safe Base64 required |
| Reality `sid` | `sid` | VLESS share | `applyReality` | `tls.reality.short_id` | yes | — | yes | yes | — | — | **UNVERIFIED** | EXTENDED | hex, even length ≤16 |
| Reality `fp` | `fp` | share | `applyReality` | `tls.utls.fingerprint` | yes | — | yes | yes | — | — | **UNVERIFIED** | EXTENDED | default `chrome` if omitted |
| Reality spiderX / spx | `spiderX`, `spx` | VLESS share | `applyReality` | `tls.reality.spider_x` | no | `reality-spider-x.patch` | yes | yes | — | patch present | **UNVERIFIED** | PARTIAL | fallback path; empty → `/`; **SpiderY unsupported** |
| Reality SpiderY | Xray | — | — | — | — | — | no | no | no | no | no | UNSUPPORTED | |
| XHTTP / SplitHTTP | `type=xhttp\|splithttp` | VLESS/VMess/Trojan share | `transport`→`xhttp` | `transport.type=xhttp` | no in stable | `xhttp.patch` | yes | yes | partial | CI claims (README) | **UNVERIFIED** | EXTENDED | backport, not upstream release claim |
| XHTTP `path` / `mode` | query / extra | share | `xhttp` | `transport.path`, `transport.mode` | via patch | xhttp | yes | yes | partial | CI claims | **UNVERIFIED** | EXTENDED | modes: auto, packet-up, stream-up, stream-one |
| XHTTP xmux / download_settings | extra JSON | share / JSON | `xhttp` | nested under transport | via patch | xhttp + import-compat | yes | yes | partial | partial | **UNVERIFIED** | PARTIAL | unknown nested keys **fail** |
| VLESS Encryption | `encryption=` | share, Xray, Clash | `applyVlessEncryption` | outbound `encryption` | no | `vless-encryption.patch` | yes | yes | native-config write | patch | **UNVERIFIED** | EXTENDED | `mlkem768x25519plus.native.*` format gate; +Vision flow **rejected** |
| Flow Vision | `flow` | share | parseVless | `flow` | yes | — | yes | yes | — | — | **UNVERIFIED** | EXTENDED | incompatible with encryption |
| Packet encoding | `packetEncoding` | share | parseVless | `packet_encoding` | yes | — | yes | yes | — | — | **UNVERIFIED** | EXTENDED | |
| Mux / smux | mux* query | share | multiplex helpers | `multiplex` | yes | — | yes | yes | — | — | **UNVERIFIED** | EXTENDED | |
| SNI / ALPN / allowInsecure | query | share | TLS block | `tls.*` | yes | — | yes | yes | — | — | **UNVERIFIED** | EXTENDED | |
| Transport ws / grpc / h2 / httpupgrade | `type=` | share | `transport` | `transport.type` | yes | — | yes | yes | — | — | **UNVERIFIED** | EXTENDED | |
| Hysteria 1 / 2 | hy/hy2 links | share | `ProxyLinkParser.hysteria` | hysteria(2) outbound | yes | fixtures | yes | yes | partial | partial | **UNVERIFIED** | EXTENDED | |
| H2 fingerprint / chrome parrot | `fp` | hy2 share | hysteria | `tls.utls` + `disable_chrome_parrot` | partial | — | yes | yes | — | — | **UNVERIFIED** | PARTIAL | |
| H2 finalmask / pcs / vcn / pin | stream/JSON | JSON / share | finalmask / TLS | mapped fields | limited | import-compat | yes | yes | partial | partial | **UNVERIFIED** | PARTIAL | pinSHA256 ≠ SPKI (no silent swap) |
| Unknown optional query | any extra key | share | `tolerateParameters` | **not in outbound** | — | — | warn | no | — | — | n/a | PARTIAL | warning only; not applied |
| Xray route/DNS/inbounds | full Xray JSON | JSON | stripped | app | — | — | — | — | — | — | — | IGNORED | by design |

## Patches (core)

Required by `scripts/validate.sh` (`REQUIRED_CORE_PATCHES`):

- `sing-box-1.14.2-xhttp.patch`
- `sing-box-1.14.2-test-fixtures.patch`
- `sing-box-1.14.2-ping-completion.patch`
- `sing-box-1.14.2-probe-mode.patch`
- `sing-box-1.14.2-import-compatibility.patch`
- `sing-box-1.14.2-vless-encryption.patch`
- `sing-box-1.14.2-client-options.patch`
- `sing-box-1.14.2-rule-set-fallback.patch`
- `sing-box-1.14.2-reality-spider-x.patch`

## Regression tests

| Scenario | Primary test location |
|----------|----------------------|
| Reality nested mapping (pbk/sid/fp/spider_x) | `XrayCapabilityMappingTest`, `ProxyLinkParserTest.realityAppliesSpiderXAndFingerprint` |
| Unknown optional not applied + warning | `XrayCapabilityMappingTest`, `SubscriptionContentParserTest.unknownConnectionParametersAreReported` |
| Critical Reality without pbk | `SubscriptionContentParserTest.realityWithoutPublicKeyIsRejectedInsteadOfDowngraded` |
| XHTTP path/mode | `XrayCapabilityMappingTest`, existing `ProxyLinkParserTest` xhttp* |
| VLESS encryption | `VlessEncryptionImportTest` |
| Hysteria2 fingerprint | `ProxyLinkParserTest.hysteria2AppliesFingerprintAndChromeParrot` |

## Explicitly not claimed

- Full Xray protocol parity  
- RUNTIME_VERIFIED for any row above unless a device log is attached  
- That `PARSED` + warning means the parameter affects the connection  


## x_padding_bytes (A+1)

| Stage | Status |
|-------|--------|
| PARSED | yes (query + extra.xPaddingBytes) |
| MAPPED | yes `transport.x_padding_bytes.{from,to}` |
| CORE_CHECKED | yes (XHTTP patch `XPaddingBytes`; native fixture import-xhttp-padding.json) |
| RUNTIME_VERIFIED | partial — Go `TestXHTTPXRayInteropParameters` with padding range in core patch; not device |
| Conflict query≠extra | reject |


## finalmask A+2a (QUIC subset only)

| Parameter | PARSED | MAPPED | CORE_CHECKED | WIRE | ANDROID |
|-----------|--------|--------|--------------|------|---------|
| quicParams.congestion=bbr | yes | validated only (no outbound field) | n/a | no | no |
| bbrProfile → bbr_profile | yes | yes | native H2 option | no | no |
| disablePathMTUDiscovery → disable_path_mtu_discovery | yes | yes | QUICOptions | no | no |
| udpHop.ports → server_ports | yes | yes | native H2 | no | no |
| udpHop.interval → hop_interval | yes | yes (`Ns`) | native H2 | no | no |
| non-empty udp/tcp wire masks | yes | reject | n/a | **UNSUPPORTED** (open) | no |

Wire FinalMask (Sudoku, noise, salamander, …): **UNSUPPORTED** in A+2a; A+2b/c HOLD. Not roadmap-excluded permanently.
