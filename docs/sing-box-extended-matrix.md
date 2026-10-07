# SFAxtnd vs upstream sing-box 1.14.2 vs shtorm-7/sing-box-extended

Snapshot for handoff priority #2. Extended HEAD checked: `5956780` (describe `v1.14.1-extended-2.7.2-2-g5956780`), base merge tag **v1.14.1**.
SFAxtnd pins **SagerNet/sing-box v1.14.2** + selective patches under `patches/`.

| Feature / patch | upstream 1.14.2 | sing-box-extended | SFAxtnd (patches + app) | Conflict | Benefit | Risk | Recommendation |
|-----------------|-----------------|-------------------|-------------------------|----------|---------|------|----------------|
| Core pin | v1.14.2 | v1.14.1 + extended commits | v1.14.2 | Version skew | SFAxtnd stays on newer patch base | Cherry-picks from extended need rebase | **Keep 1.14.2**; do not wholesale swap |
| XHTTP transport | no | yes (transport/v2rayxhttp ~2k LOC) | yes (`sing-box-1.14.2-xhttp.patch` ~4.5k) | Parallel implementations | Already covered | Double-port loses local fixes (x_padding import) | **Keep local XHTTP patch** |
| VLESS encryption / ML-KEM | limited/none | yes (protocol/vless/encryption, reality mlkem option) | yes (`sing-box-1.14.2-vless-encryption.patch`) | Overlap | Already covered | API drift vs extended | **Keep local**; compare option names if interop issues |
| Rule-set download fallback via proxy | download_detour only | no `download_fallback_detour` found | yes (`sing-box-1.14.2-rule-set-fallback.patch` + app RuleSetPolicy) | SFAxtnd ahead | Critical for RU first-start | Upstream may diverge | **Keep local** |
| Ping completion / probe mode | stock | stock-ish | yes (ping-completion, probe-mode patches) | Local-only | Offline/warm ping UX | — | **Keep local** |
| Import compatibility | stock | own parsers | yes (import-compatibility + Kotlin parsers) | Different layers | App-side already strong | — | **Keep local** |
| Failover outbound group | no | **yes** (`protocol/failover`) | no | New surface | Auto server switch / HA | Product + UI + tests cost | **Optional later** as managed feature only |
| Fallback outbound group | stock urltest/selector | extended fallback | selector/urltest via app | Low | — | — | Skip unless product needs |
| DNS sequential fallback | partial | DNS fallback helpers | app + core tests (`dns_client_fallback_test.go`) | Overlap | Already tested in CI | — | Keep SFAxtnd approach |
| REALITY support_x25519mlkem768 | depends on branch | explicit option commit | via vless-encryption patch | Need field-level diff | Interop with new servers | Wrong flag name breaks handshake | **Verify option parity** if users report Reality failures |
| WARP / MASQUE / MTProxy / Call / Sudoku / TrustTunnel | no | yes | no | Large | Niche | Huge maintenance + APK size | **Do not integrate** |
| Amnezia WG | no | yes | no | — | Mobile DPI | Size/complexity | Only on explicit product request |
| OpenVPN / OpenConnect / USBIP / Tailscale bits | no / legacy UI leftovers | build tags in extended | residual screens possible | Noise | — | Dead code weight | **Strip non-product UI** over time |

## Strategy (default)

**A) Keep upstream 1.14.2 + selective local patches** (current).

Do **not** switch core to extended as base until:

1. Field-level diff of XHTTP + VLESS encryption options proves extended is strictly supersets local patches;
2. Failover is product-required and designed in UI as explicit mode;
3. CI matrix rebuilt for extended tags and Android libbox API.

## Next concrete checks (when prioritized)

1. Diff `option` structs for XHTTP and VLESS encryption between local patch and extended.
2. Confirm Reality ML-KEM JSON field names match Xray clients users import.
3. If Failover desired: design as optional outbound group generated from subscription metadata, behind setting, with regression tests.
