# XHTTP core backport

`sing-box-1.14.2-xhttp.patch` applies to SagerNet/sing-box `v1.14.2`
(`af6e64c3b69e6132ebaee0e1a3d24e93903f6709`). It carries the XHTTP
transport and its tests from upstream commit
[`79eca64a383e32316b2fcbfd105d1741e3526220`](https://github.com/SagerNet/sing-box/commit/79eca64a383e32316b2fcbfd105d1741e3526220).
The upstream code remains covered by sing-box's GPL-3.0-or-later license.

This is a development transport backport, not a claim that the stable
v1.14.2 release supports XHTTP. The patch preserves the stable core's
dependencies and schema implementation, adds the XHTTP schema variant,
adds a Hysteria v1/v2 local connection regression test, and uses Xray's
`sessionPlacement`/`sessionKey` names in the compatibility fixtures. Update or
remove this patch when upgrading to a core with native XHTTP support.

CI applies the patch before building libbox and tests HTTP/1.1, HTTP/2,
HTTP/3, REALITY, the three upload modes, separate download endpoints,
and compatibility with the pinned Xray v26.3.27 binary. An application
build fails if the patch cannot be applied or a transport test fails.

Share-link tests run separately with `./gradlew -p protocol-tests test`.
They use the production parser without requiring Android or libbox.
The importer handles `hysteria://`, `hysteria2://`, `hy2://`, and
`type=xhttp`/`splithttp` on VLESS, VMess and Trojan links. It also accepts
these links on local profile import surfaces (clipboard, QR and files).

Unsupported share-link options fail explicitly instead of creating
an altered connection. In particular, Hysteria's `pinSHA256` pins a
certificate, whereas the native TLS public-key pin field pins its SPKI;
the importer does not substitute one for the other. Hysteria v1 uses
100 Mbps in each direction when its link omits required bandwidth.

Local tests and CI cannot establish reachability of a user's remote
server. Retest the reported subscription in the resulting APK; request
a redacted connection log if that particular endpoint still fails.

`sing-box-1.14.2-import-compatibility.patch` follows the transport patch. It adds
bounded parallel packet-up POST requests with cancellation and ordered sequence
numbers, and preserves Xray `pcs`/`vcn` semantics in both Go TLS and uTLS.
An exact leaf certificate pin accepts that certificate; a pinned CA still
requires chain and hostname verification. Verification names are independent
of SNI. These fields do not replace the existing `certificate_sha256` behavior.
Handshake tests cover matching and incorrect pins, CA chains, independent names
and untrusted certificates. Upload tests cover concurrency limits, packet
splitting, cancellation and worker failure. CI runs them with the race detector.

`sing-box-1.14.2-import-compatibility.patch` follows the transport patch. It adds
bounded parallel packet-up POST requests with cancellation and ordered sequence
numbers, and preserves Xray `pcs`/`vcn` semantics in both Go TLS and uTLS.
An exact leaf certificate pin accepts that certificate; a pinned CA still
requires chain and hostname verification. Verification names are independent
of SNI. These fields do not replace the existing `certificate_sha256` behavior.
Handshake tests cover matching and incorrect pins, CA chains, independent names
and untrusted certificates. Upload tests cover concurrency limits, packet
splitting, cancellation and worker failure. CI runs them with the race detector.

`sing-box-1.14.2-import-compatibility.patch` follows the transport patch. It adds
bounded parallel packet-up POST requests with cancellation and ordered sequence
numbers, and preserves Xray `pcs`/`vcn` semantics in both Go TLS and uTLS.
An exact leaf certificate pin accepts that certificate; a pinned CA still
requires chain and hostname verification. Verification names are independent
of SNI. These fields do not replace the existing `certificate_sha256` behavior.
Handshake tests cover matching and incorrect pins, CA chains, independent names
and untrusted certificates. Upload tests cover concurrency limits, packet
splitting, cancellation and worker failure. CI runs them with the race detector.

`sing-box-1.14.2-import-compatibility.patch` follows the transport patch. It adds
bounded parallel packet-up POST requests with cancellation and ordered sequence
numbers, and preserves Xray `pcs`/`vcn` semantics in both Go TLS and uTLS.
An exact leaf certificate pin accepts that certificate; a pinned CA still
requires chain and hostname verification. Verification names are independent
of SNI. These fields do not replace the existing `certificate_sha256` behavior.
Handshake tests cover matching and incorrect pins, CA chains, independent names
and untrusted certificates. Upload tests cover concurrency limits, packet
splitting, cancellation and worker failure. CI runs them with the race detector.
