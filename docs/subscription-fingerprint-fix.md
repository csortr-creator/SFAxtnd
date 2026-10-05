# Subscription import regressions

- Hysteria pinSHA256 is now imported as native certificate_sha256. Hex, colon-separated hex and base64 fingerprints are accepted only when decoded to 32 bytes. The core checks SHA-256 of the exact leaf DER certificate. It does not substitute a public-key pin or discard verification. The source insecure flag remains unchanged.
- Empty, whitespace and null XHTTP extra.mode values normalize to auto; explicit valid modes remain preserved, including independent download settings.
- TLS tests exercise matching and incorrect pins against a real local TLS server, for Go TLS and uTLS, and confirm that insecure=false still requires trusted certificates. Hysteria wire tests also exercise pinned certificates for v1 and v2.
- Jules PR #24 was inspected but not merged; see jules-pr-24-review.md.
