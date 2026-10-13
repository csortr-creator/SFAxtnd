package vless

import (
	"bytes"
	"context"
	"crypto/ecdh"
	"crypto/rand"
	"crypto/tls"
	"crypto/x509"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/http/httptest"
	"os"
	"os/exec"
	"path/filepath"
	"testing"
	"time"

	"github.com/sagernet/sing-box/option"
	"github.com/sagernet/sing-box/transport/vlessenc"
	V "github.com/sagernet/sing-vmess/vless"
	"github.com/sagernet/sing/common/logger"
	M "github.com/sagernet/sing/common/metadata"
	N "github.com/sagernet/sing/common/network"
)

func TestSFATCPHeaderXray(t *testing.T) {
	binary := os.Getenv("SFA_REALITY_XRAY_BINARY")
	if binary == "" {
		t.Skip("current Xray fixture binary is not configured")
	}
	origin := httptest.NewTLSServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusNoContent)
	}))
	defer origin.Close()
	clients := []string{"HELLO\r\n", "SECOND\r\n"}
	servers := []string{"READY\r\n", "ACCEPTED\r\n", "END\r\n"}
	key, err := ecdh.X25519().GenerateKey(rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	for _, mode := range []string{"none", "0rtt", "1rtt"} {
		decryption := "none"
		encryption := "none"
		if mode != "none" {
			decryption = "mlkem768x25519plus.native.600s." + base64.RawURLEncoding.EncodeToString(key.Bytes())
			encryption = "mlkem768x25519plus.native." + mode + "." + base64.RawURLEncoding.EncodeToString(key.PublicKey().Bytes())
		}
		address := startTCPHeaderXray(t, binary, clients, servers, decryption)
		pool := x509.NewCertPool()
		pool.AddCert(origin.Certificate())
		for _, negative := range []bool{false, true} {
			t.Run(fmt.Sprintf("%s/wrong-response=%v", mode, negative), func(t *testing.T) {
				header := &option.VLESSTCPHeaderOptions{Clients: clients, Servers: append([]string(nil), servers...)}
				if negative {
					header.Servers[0] = "WRONG\r\n"
				}
				client, err := V.NewClient("00000000-0000-0000-0000-000000000001", "", logger.NOP())
				if err != nil {
					t.Fatal(err)
				}
				outbound := &Outbound{logger: logger.NOP(), dialer: N.SystemDialer, serverAddr: M.ParseSocksaddr(address), client: client, tcpHeader: header}
				outbound.encryption, err = vlessenc.NewClient(encryption)
				if err != nil {
					t.Fatal(err)
				}
				transport := &http.Transport{DisableKeepAlives: true, TLSClientConfig: &tls.Config{RootCAs: pool}, DialContext: func(ctx context.Context, network, address string) (net.Conn, error) {
					return (*vlessDialer)(outbound).DialContext(ctx, network, M.ParseSocksaddr(address))
				}}
				defer transport.CloseIdleConnections()
				httpClient := &http.Client{Transport: transport, Timeout: 5 * time.Second}
				for attempt := 0; attempt < 2; attempt++ {
					response, err := httpClient.Get(origin.URL)
					if negative {
						if err == nil {
							response.Body.Close()
							t.Fatal("accepted mismatched TCP header")
						}
						return
					}
					if err != nil {
						t.Fatal(err)
					}
					defer response.Body.Close()
					if response.StatusCode != http.StatusNoContent {
						t.Fatalf("HTTPS status %d", response.StatusCode)
					}
				}
			})
		}
	}
}

func TestSFATCPHeaderCancellation(t *testing.T) {
	client, server := net.Pipe()
	defer client.Close()
	defer server.Close()
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	done := make(chan error, 1)
	go func() {
		done <- exchangeTCPHeader(ctx, client, &option.VLESSTCPHeaderOptions{Clients: []string{"HELLO"}, Servers: []string{"READY"}})
	}()
	request := make([]byte, 5)
	if _, err := io.ReadFull(server, request); err != nil {
		t.Fatal(err)
	}
	cancel()
	select {
	case err := <-done:
		if !errors.Is(err, context.Canceled) {
			t.Fatal("cancelled handshake did not preserve cancellation")
		}
	case <-time.After(time.Second):
		t.Fatal("cancelled handshake stayed blocked")
	}
}

func startTCPHeaderXray(t *testing.T, binary string, clients, servers []string, decryption string) string {
	t.Helper()
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	port := listener.Addr().(*net.TCPAddr).Port
	listener.Close()
	sequence := func(values []string) []any {
		result := []any{}
		for _, value := range values {
			result = append(result, []any{map[string]any{"type": "str", "packet": value}})
		}
		return result
	}
	config := map[string]any{
		"log": map[string]any{"loglevel": "error"},
		"inbounds": []any{map[string]any{
			"listen": "127.0.0.1", "port": port, "protocol": "vless",
			"settings": map[string]any{"clients": []any{map[string]any{"id": "00000000-0000-0000-0000-000000000001"}}, "decryption": decryption},
			"streamSettings": map[string]any{"network": "tcp", "security": "none", "finalmask": map[string]any{"tcp": []any{map[string]any{
				"type": "header-custom", "settings": map[string]any{"clients": sequence(clients), "servers": sequence(servers)},
			}}}},
		}},
		"outbounds": []any{map[string]any{"protocol": "freedom", "settings": map[string]any{"finalRules": []any{map[string]any{"action": "allow", "ip": []string{"127.0.0.0/8"}}}, "ipsBlocked": []string{}}}},
	}
	data, err := json.Marshal(config)
	if err != nil {
		t.Fatal(err)
	}
	path := filepath.Join(t.TempDir(), "server.json")
	if err := os.WriteFile(path, data, 0600); err != nil {
		t.Fatal(err)
	}
	cmd := exec.Command(binary, "run", "-c", path)
	var logs bytes.Buffer
	cmd.Stdout = &logs
	cmd.Stderr = &logs
	if err := cmd.Start(); err != nil {
		t.Fatal(err)
	}
	done := make(chan error, 1)
	go func() { done <- cmd.Wait() }()
	t.Cleanup(func() {
		cmd.Process.Kill()
		select {
		case <-done:
			if t.Failed() {
				t.Log(logs.String())
			}
		case <-time.After(3 * time.Second):
			t.Error("Xray did not exit")
		}
	})
	address := fmt.Sprintf("127.0.0.1:%d", port)
	for deadline := time.Now().Add(5 * time.Second); time.Now().Before(deadline); {
		select {
		case err := <-done:
			done <- err
			t.Fatalf("Xray exited: %v", err)
		default:
		}
		c, err := net.DialTimeout("tcp", address, 100*time.Millisecond)
		if err == nil {
			c.Close()
			return address
		}
		time.Sleep(10 * time.Millisecond)
	}
	t.Fatal("Xray did not listen")
	return ""
}
