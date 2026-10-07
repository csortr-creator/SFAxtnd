# Xray capability matrix (A+)

Base: SFAxtnd `dev`, sing-box **1.14.2** + local patches. Scope: **outbound / share-link** only.

| Field / feature | Parser | Mapped config | Core | Level | Notes |
|-----------------|--------|---------------|------|-------|-------|
| VLESS base | yes | yes | yes | FULL | |
| Reality spiderX | yes (A+) | spider_x | patch | PARTIAL | fallback path |
| Unknown optional query | tolerate | not mapped | — | PARTIAL | import warnings |
| Xray routing/DNS/inbounds | stripped | — | app | IGNORED | by design |
