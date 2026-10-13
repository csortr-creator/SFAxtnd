//go:build with_utls && !race

package tls

import (
	"os"
	"testing"
)

// sing-vmess Vision uses uintptr reflection that is incompatible with checkptr
// instrumentation. Run the actual HTTPS wire check in a separate ordinary test;
// the authentication/ordinary VLESS matrix remains race-instrumented.
func TestSFARealityXrayVision(t *testing.T) {
	sfaRunRealityXrayMatrix(t, "xtls-rprx-vision")
}

// Cross product catches importable combinations whose layers cannot actually cooperate.
func TestSFARealityEncryptionVision(t *testing.T) {
	binary := os.Getenv("SFA_REALITY_XRAY_BINARY")
	if binary == "" {
		t.Skip("current Xray fixture binary is not configured")
	}
	for _, fp := range []string{"chrome", "firefox", "safari"} {
		for _, mode := range []string{"0rtt", "1rtt"} {
			t.Run(fp+"/"+mode, func(t *testing.T) {
				sfaCheckRealityCombination(t, binary, "xtls-rprx-vision", fp, mode)
			})
		}
	}
}
