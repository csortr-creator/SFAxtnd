package dialer

import (
	"context"
	"time"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/option"
	"github.com/sagernet/sing-dns"
	N "github.com/sagernet/sing/common/network"
)

type Options struct {
	Context                 context.Context
	Router                  adapter.Router
	Options                 option.OutboundDialerOptions
	RemoteIsDomain          bool
	DirectResolver          bool
	ResolverOnDetour        bool
	NewDialer               bool
	DisableEmptyDirectCheck bool
	DirectOutbound          bool
	DefaultOutbound         bool
}

func New(options Options) N.Dialer {
	var dialer N.Dialer
	if options.Options.Detour == "" {
		dialer = NewDefault(options.Router, options.Options.DialerOptions)
	} else {
		dialer = NewDetour(options.Router, options.Options.Detour)
	}

	domainStrategy := dns.DomainStrategy(options.Options.DomainStrategy)
	if domainStrategy != dns.DomainStrategyAsIS || options.Options.Detour == "" {
		dialer = NewResolveDialer(options.Router, dialer, domainStrategy, time.Duration(options.Options.FallbackDelay))
	}
	return dialer
}
