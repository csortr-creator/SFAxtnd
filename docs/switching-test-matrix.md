# Subscription / server switch test matrix

Logs: `adb logcat -s SFA.Switch`

## Subscription switch (`selectProfile`)

| Case | Steps | Expect |
|------|-------|--------|
| A→B same app session | VPN on, switch profile B | STOP_OLD → OLD_STOPPED → START_NEW → CONNECTED; traffic via B |
| B→A | reverse | same sequence; traffic via A |
| Fast A→B→C | rapid taps | only one transition at a time (`isLoading` / status gate); final profile = last successful |
| During Connecting | switch while Starting | ignored until Started/Stopped |
| After error | fail start then switch other | rollback or clear error; no false Connected |
| VPN off | switch profile while Stopped | selectedProfile updates; no stop/start |
| Background | switch, send app to background | completes or fails with error; no orphan TUN |
| Network flap | switch during Wi‑Fi↔mobile | eventually Connected or explicit error |

## Server within group (`selectGroupItem`)

| Case | Expect |
|------|--------|
| Select other outbound | `selectOutbound` + persisted selection; UI selected matches |
| VPN stopped | selection saved; applied on next start |

## Apply settings change (`restartServiceForApplyChange`)

| Case | Expect |
|------|--------|
| Change requiring restart while Started | APPLY_CHANGE STOP/START logs; returns to Started |
| Stop timeout | log error; no silent success |
