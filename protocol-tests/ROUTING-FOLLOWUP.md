DNS rules and route-only sniffing

Client DNS rules support domain, suffix, keyword and SRS conditions, optional A/AAAA, direct/proxy fallback groups, rejection, ordering and enable state. They precede FakeIP and default DNS. Endpoint bootstrap remains direct to avoid resolution loops.

Modern sniff actions use the detected domain only for route matching. Advanced sniffer selection is collapsed. Explicit resolve uses native sing-box actions; Xray domain-strategy names are not copied. Fast fallback reduces IPv4/IPv6 Happy Eyeballs delay, without claiming simultaneous dialing of every IP.

Android strict_route prevents allowBypass while the VPN is running. It does not remove explicitly excluded apps/routes or replace Android's always-on VPN lockdown.

RF direct and Russian whitelist each reference their domain/IP SRS files in one connection rule. Legacy presets migrate while preserving unrelated settings and order. RF direct retains subscription catch-all behavior; whitelist retains proxy catch-all. Bundled initial copies allow startup offline. Remote rule sets retain native update timers using initial_path and the shared interval; disabled updates use local copies. Downloads are bounded and validated before replacing cache.

Explicit warm/cold ping modes are propagated through native command RPC and offline probes. Warm mode measures the second HTTP HEAD on the same transport/connection where supported; endpoints that close keep-alive reconnect. Cold mode performs one request. Duplicate test guards remain in place.
