# Single-core subscription compatibility

SFAxtnd uses its pinned sing-box core. Import translates equivalent settings;
it does not make an unsupported wire protocol work by dropping its options.
The table describes implemented paths, not a guarantee that a remote server is reachable.

| Input | Native representation | Scope / limit |
| --- | --- | --- |
| SNI / ALPN | `tls.server_name` / `tls.alpn` | Preserve provider values and certificate verification |
| Xray certificate pins / names | Native verification options | Existing mapping; no conversion into `insecure` |
| Reality public key / short ID / fingerprint | Native Reality TLS options | Existing core compatibility patches; fingerprint support still matters |
| XHTTP padding range | `transport.x_padding_bytes` | Existing parser/core support; conflicting aliases rejected |
| VLESS Encryption | `encryption` | Existing native single-key `mlkem768x25519plus.native.0rtt/1rtt`; Vision combination rejected |
| FinalMask UDP Salamander | Hysteria2 `obfs.type=salamander` | One mask, password-only settings, at least four UTF-8 bytes; proxy authentication remains separate |
| FinalMask TCP header-custom | VLESS `tcp_header.clients/servers` | One mask; literal `str` items; plain TCP without TLS/Reality, other transport, or Vision |
| FinalMask QUIC tuning | Native Hysteria2 BBR/MTU/hopping options | Existing supported subset; other QUIC fields are rejected |
| QUIC uTLS fingerprint | Hysteria2 Chrome parrot for exact `chrome` | Existing normalization; other fingerprints are not silently substituted |
| Other FinalMask masks/chains | Import issue | Unsupported settings are not ignored or collapsed into a different mask |

## Shared mapping

`FinalMaskCompatibility` is used by URI and Xray JSON adapters through
`ProxyLinkParser.applyFinalMask`. It validates a copy before updating the node,
so a rejected mapping leaves no partially applied native settings behind.
Existing native obfuscation plus another FinalMask UDP layer is rejected even
when the passwords match: collapsing a two-layer mask would change the wire.

Trojan URI recognizes `fm` and rejects unsupported combinations instead of
silently claiming that the setting was applied. Legacy VMess JSON remains strict
about unsupported fields. This change does not convert all input formats or all
future Xray extensions automatically.

## Runtime extension

The small pinned-core TCP header patch implements literal sequence exchange:
write each client sequence, read the corresponding server sequence, then read
any remaining server sequences. Responses are compared exactly. Exchange happens
before VLESS Encryption and VLESS traffic, on both TCP and packet dialing paths.

The parser and core bound headers to 32 sequences per direction and 64 KiB total;
the parser additionally bounds item count. Exchange has a 15-second maximum,
honors a shorter dialing deadline, and closes the socket on cancellation.
Errors do not include literal header contents, and cancellation stays cancellation.
Unmasked outbounds do not enter this path.

## Verification

JVM fixtures exercise URI/Xray JSON conversion, password/TLS preservation,
unsupported masks/settings/chains, conflicting obfuscation, transactional mapping,
and no fingerprint bypass. Generated full configurations are checked by the
patched core in CI.

Official checksum-pinned Xray v26.9.9 is a **test fixture**, not a second runtime
in the APK. The wire tests cover:

- TCP header with plain VLESS, Encryption 0rtt and 1rtt, and fresh connections;
- complete HTTPS exchange with a trusted test certificate;
- wrong header rejection and cancelled header exchange;
- native Hysteria2 Salamander against Xray FinalMask, including wrong proxy
  authentication and wrong mask password rejection.

Loopback permission exists only in fixture configs. Android device and provider
tests are separate from successful import, config checks, and loopback tests.

## Remaining work

Audit unsupported combinations against native capabilities before implementing
new protocol code. Current limits include dynamic header items, arbitrary mask
chains, Salamander packet-size/gecko options, broader QUIC fingerprints, and
Encryption/Vision combinations. Keep offline probe/Stop lifecycle work separate
from protocol compatibility; this change does not claim to fix those behaviors.

References: pinned Xray `infra/conf/transport_finalmask.go` and
`transport/internet/finalmask/header/custom/tcp.go`; pinned sing-quic
`hysteria2/salamander.go`; local interoperability fixtures.
