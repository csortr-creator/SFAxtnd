# MD3 screen layout redesign

The Servers, Subscriptions, Routes and Settings tabs share Material 3 surface, typography and action sizing. Inspiration was reviewed from WG Tunnel and Mullvad's Android Compose UI, and subscription workflows from Hiddify and Throne for Android. No third-party UI code or assets were copied.

## Behavior

- Servers defaults to a single-column list, with a connection summary, subscription picker, search, protocol filter and an optional grid. Search/filter state resets when the profile changes. Existing selection, latency sorting, single/group URL tests and expanded groups remain available.
- The connection button calls MainActivity's existing VPN permission/start handler, and the existing stop path; the redundant floating button/status overlay is hidden on the local Servers tab.
- Subscriptions are individual cards with update and overflow actions on each row. All previous export formats remain available. Export captures the requested profile ID before launching the document picker so an unselected row exports its own profile.
- The Routes overview shows routing presets and summaries. Rules, DNS, sources and connection parameters are separate navigation destinations with back transitions. DNS presets live on the DNS page. Settings reload on resume, including after editing a rule.
- Settings uses the same shared section/navigation-row components.
- Error dialogs show a short explanation; technical details and copy are available in a second dialog.

## Verification

Kotlin source was parsed/formatted; existing 26 parser/config JVM tests passed. The Android build must pass before distributing the resulting APK. Device checks: switch subscriptions while stopped, search and filter, switch list/grid with large text, connect/disconnect, export an unselected profile, navigate to every routing section, edit a rule and return, and open technical error details.
