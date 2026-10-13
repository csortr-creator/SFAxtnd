//go:build with_quic

package vless

import (
	"context"
	"crypto/tls"
	"crypto/x509"
	"encoding/json"
	"encoding/pem"
	"fmt"
	"net"
	"net/http"
	"net/http/httptest"
	"os"
	"os/exec"
	"path/filepath"
	"testing"
	"time"

	"github.com/sagernet/sing-box/option"
	HY "github.com/sagernet/sing-box/protocol/hysteria2"
	"github.com/sagernet/sing/common/logger"
	M "github.com/sagernet/sing/common/metadata"
)

func TestSFASalamanderXray(t *testing.T) {
	binary := os.Getenv("SFA_REALITY_XRAY_BINARY")
	if binary == "" {
		t.Skip("current Xray fixture binary is not configured")
	}
	origin := httptest.NewTLSServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { w.WriteHeader(204) }))
	defer origin.Close()
	certificate := pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE", Bytes: origin.TLS.Certificates[0].Certificate[0]})
	key, err := x509.MarshalPKCS8PrivateKey(origin.TLS.Certificates[0].PrivateKey)
	if err != nil {
		t.Fatal(err)
	}
	dir := t.TempDir()
	certFile, keyFile := filepath.Join(dir, "cert.pem"), filepath.Join(dir, "key.pem")
	if err := os.WriteFile(certFile, certificate, 0600); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(keyFile, pem.EncodeToMemory(&pem.Block{Type: "PRIVATE KEY", Bytes: key}), 0600); err != nil {
		t.Fatal(err)
	}
	reserve, err := net.ListenPacket("udp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	port := reserve.LocalAddr().(*net.UDPAddr).Port
	reserve.Close()
	config := map[string]any{
		"log": map[string]any{"loglevel": "none"},
		"inbounds": []any{map[string]any{
			"listen": "127.0.0.1", "port": port, "protocol": "hysteria",
			"settings": map[string]any{"version": 2, "users": []any{map[string]any{"auth": "proxy-password"}}},
			"streamSettings": map[string]any{
				"network": "hysteria", "security": "tls", "hysteriaSettings": map[string]any{"version": 2},
				"tlsSettings": map[string]any{"alpn": []string{"h3"}, "certificates": []any{map[string]any{"certificateFile": certFile, "keyFile": keyFile}}},
				"finalmask":   map[string]any{"udp": []any{map[string]any{"type": "salamander", "settings": map[string]any{"password": "mask-password"}}}},
			},
		}},
		"outbounds": []any{map[string]any{"protocol": "freedom", "settings": map[string]any{"finalRules": []any{map[string]any{"action": "allow", "ip": []string{"127.0.0.0/8"}}}, "ipsBlocked": []string{}}}},
	}
	data, err := json.Marshal(config)
	if err != nil {
		t.Fatal(err)
	}
	path := filepath.Join(dir, "server.json")
	if err := os.WriteFile(path, data, 0600); err != nil {
		t.Fatal(err)
	}
	cmd := exec.Command(binary, "run", "-c", path)
	if err := cmd.Start(); err != nil {
		t.Fatal(err)
	}
	done := make(chan error, 1)
	go func() { done <- cmd.Wait() }()
	t.Cleanup(func() {
		cmd.Process.Kill()
		select {
		case <-done:
		case <-time.After(3 * time.Second):
			t.Error("Xray did not exit")
		}
	})
	// Reserve readiness with a second bind, without sending invalid QUIC packets.
	ready := false
	for until := time.Now().Add(5 * time.Second); time.Now().Before(until); {
		select {
		case err := <-done:
			done <- err
			t.Fatalf("Xray exited: %v", err)
		default:
		}
		probe, err := net.ListenPacket("udp", fmt.Sprintf("127.0.0.1:%d", port))
		if err != nil {
			ready = true
			break
		}
		probe.Close()
		time.Sleep(10 * time.Millisecond)
	}
	if !ready {
		t.Fatal("Xray did not bind UDP")
	}
	pool := x509.NewCertPool()
	pool.AddCert(origin.Certificate())
	for _, tc := range []struct {
		name, password, mask            string
		valid, disableParrot, wrongName bool
	}{
		{"valid", "proxy-password", "mask-password", true, false, false},
		{"without-parrot", "proxy-password", "mask-password", true, true, false},
		{"wrong-certificate-name", "proxy-password", "mask-password", false, false, true},
		{"wrong-auth", "wrong-password", "mask-password", false, false, false},
		{"wrong-mask", "proxy-password", "wrong-mask-password", false, false, false},
	} {
		t.Run(tc.name, func(t *testing.T) {
			ctx, cancel := context.WithTimeout(context.Background(), 4*time.Second)
			defer cancel()
			serverName := "127.0.0.1"
			if tc.wrongName {
				serverName = "wrong.example.org"
			}
			out, err := HY.NewOutbound(ctx, nil, logger.NOP(), "hy2", option.Hysteria2OutboundOptions{
				ServerOptions: option.ServerOptions{Server: "127.0.0.1", ServerPort: uint16(port)}, Password: tc.password,
				DisableChromeParrot:         tc.disableParrot,
				Obfs:                        &option.Hysteria2Obfs{Type: "salamander", Password: tc.mask},
				OutboundTLSOptionsContainer: option.OutboundTLSOptionsContainer{TLS: &option.OutboundTLSOptions{Enabled: true, ServerName: serverName, Certificate: []string{string(certificate)}}},
			})
			if err != nil {
				t.Fatal(err)
			}
			defer out.(*HY.Outbound).Close()
			transport := &http.Transport{TLSClientConfig: &tls.Config{RootCAs: pool}, DialContext: func(ctx context.Context, network, address string) (net.Conn, error) {
				return out.DialContext(ctx, network, M.ParseSocksaddr(address))
			}}
			defer transport.CloseIdleConnections()
			response, err := (&http.Client{Transport: transport, Timeout: 3 * time.Second}).Get(origin.URL)
			if !tc.valid {
				if err == nil {
					response.Body.Close()
					t.Fatal("invalid authentication or mask accepted")
				}
				return
			}
			if err != nil {
				t.Fatal(err)
			}
			defer response.Body.Close()
			if response.StatusCode != 204 {
				t.Fatalf("HTTPS status %d", response.StatusCode)
			}
		})
	}
}
