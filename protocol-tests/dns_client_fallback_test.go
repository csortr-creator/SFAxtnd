package dns

import (
 "context"
 "net/netip"
 "testing"
 "time"
 "github.com/sagernet/sing-box/option"
 "github.com/sagernet/sing/common/json/badoption"
 "github.com/stretchr/testify/require"
)

func TestSFASequentialDNSFallbackAfterTimeout(t *testing.T) {
 x := &fakeDNSTransport{tag: "x", delay: 50*time.Millisecond, exchangeErr: context.DeadlineExceeded}
 y := &fakeDNSTransport{tag: "y", delay: 10*time.Millisecond, address: netip.MustParseAddr("192.0.2.2")}
 router := raceTestRouter(t, x, y)
 primary := evaluateRule("x", "primary", false)
 primary.DefaultOptions.EvaluateOptions.Timeout = badoption.Duration(50*time.Millisecond)
 rules := raceTestRules(t, []option.DNSRule{primary, respondRule("primary", false, true), routeRule("y", false)})
 result := raceTestExchange(router, rules)
 require.NoError(t, result.err)
 require.Equal(t, netip.MustParseAddr("192.0.2.2"), responseAddress(t, result.response))
 require.Equal(t, int32(1), x.queryCount.Load())
 require.Equal(t, int32(1), y.queryCount.Load())
 require.GreaterOrEqual(t, y.firstQueried.Sub(x.firstQueried), 35*time.Millisecond)
}

func TestSFASequentialDNSFallbackDoesNotQueryBackupOnSuccess(t *testing.T) {
 x := &fakeDNSTransport{tag: "x", address: netip.MustParseAddr("192.0.2.1")}
 y := &fakeDNSTransport{tag: "y", address: netip.MustParseAddr("192.0.2.2")}
 router := raceTestRouter(t, x, y)
 rules := raceTestRules(t, []option.DNSRule{evaluateRule("x", "primary", false), respondRule("primary", false, true), routeRule("y", false)})
 result := raceTestExchange(router, rules)
 require.NoError(t, result.err)
 require.Equal(t, netip.MustParseAddr("192.0.2.1"), responseAddress(t, result.response))
 require.Equal(t, int32(0), y.queryCount.Load())
}
