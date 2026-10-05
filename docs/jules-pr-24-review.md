# Review of Jules PR #24

Reviewed head `94bf59fe5ebd0c31a2f108e5b49ab14ad3a5278a` against dev `ace9a5a8dbba554dd14adabf73ec1da7153b7e61`.

Recommendation: do not merge this revision.

- GitHub reports `mergeable=false`, `mergeable_state=dirty`.
- HTTPClient.kt calls `parseHysteria2(trimmed)` but contains no definition; PR does not introduce one elsewhere. This causes an unresolved reference and duplicates the production ProxyLinkParser already integrated on dev.
- MainActivity.kt removes `tailscaleStatusViewModel` and other tools view-model declarations while its subscribe/unsubscribe lambdas still reference them. This causes unresolved references.
- The parsing catch now suppresses invalid-node errors and imports a partial subscription, rather than telling the user their subscription lost servers. Existing strict parsing on dev keeps the previous profile intact on failure.
- Neither screenshot error is fixed by this PR: it does not normalize XHTTP extra.mode or implement full-certificate fingerprint verification in libbox.

The UI cleanup should be rebased and submitted separately, with a real Android build rather than a simulated build. No merge, close, or review comment was sent.
