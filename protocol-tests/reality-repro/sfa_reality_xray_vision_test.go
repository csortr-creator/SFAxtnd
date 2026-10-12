//go:build with_utls && !race

package tls

import "testing"

// sing-vmess Vision uses uintptr reflection that is incompatible with checkptr
// instrumentation. Run the actual HTTPS wire check in a separate ordinary test;
// the authentication/ordinary VLESS matrix remains race-instrumented.
func TestSFARealityXrayVision(t *testing.T) {
	sfaRunRealityXrayMatrix(t, "xtls-rprx-vision")
}
