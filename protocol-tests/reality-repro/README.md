# Reality ML-KEM investigation — WIP, not enabled

Baseline product: 965f2b0 / Actions #444 SUCCESS. These files are research only;
patch.sh and CI do not apply/run this candidate.

Pinned core v1.14.2: af6e64c3b69e6132ebaee0e1a3d24e93903f6709.
Use all existing SFA core patches first, then candidate.patch, then copy Go files
into common/tls. Runtime Go 1.25.5; with_quic,with_utls; -ldflags=-checklinkname=0.
XHTTP_XRAY_BINARY points to official Xray 26.3.27; SFA_REALITY_XRAY_BINARY to 26.9.9.
Public official zip digests were verified before local execution.

Unmodified core: valid Reality handshake PASS with 26.3.27, FAIL with 26.9.9.
Candidate retains configured X25519MLKEM768 curves/key share; separate X25519
ECDHE authentication key, identity verification and version bytes remain unchanged.
Handshake-only candidate: valid auth PASS on both; wrong key/short ID rejected;
modern matrix passed three repetitions under -race.

IMPORTANT: current expanded HTTPS/VLESS tests FAIL with a timeout after successful
handshake. Do not claim working traffic or ship this patch yet. Investigate
fixture vs production path. Xray modern freedom requires a fixture loopback allow;
fixture now uses finalRules allow 127.0.0.0/8 plus legacy ipsBlocked [].
Production app routing is unchanged. Client uses the production DialEarlyConn.
The fixture has temporary debugging (reflection, stack timer, debug Xray logs).
Remove those once the failure is explained. Origin listener is closed before replacement.
Current camouflage: httptest TLS1.3, no HTTP2, session tickets disabled, X25519
curve preference (a temporary diagnostic variation; earlier hybrid preference also timed out).
Restore hybrid preference for final coverage.

Vision reflection in sing-vmess raises checkptr under -race. Vision test file is
!race, intended to run separately WITHOUT -race; auth/plain matrix retains -race.
No product dependency/instrumentation changes were made.

References: https://github.com/SagerNet/sing-box/issues/4520
https://github.com/XTLS/REALITY/commit/8cdf7bf9c7f09cb9814bf08c3eb877f68b85fba8

Next: get deterministic full HTTPS success with old/new Xray and auth negatives;
then hook a reviewed minimal patch + pinned modern test binary into CI as a
separate small step. No second client core, no verification bypass, no server secrets.
