# Noticias elDiario.es

Independent Android RSS reader for elDiario.es.

## Features
- Live RSS: https://www.eldiario.es/rss/
- RecyclerView list, search, pull-to-refresh, sharing and article view
- Offline JSON and image cache
- Passive network observation and explicit connectivity diagnostics
- ConnectivityAndInternetAccess synchronized from the canonical gist revision
  `3b0497e976765653a7467e3bd7d6bff28b96bd7c` (the compiled copy is under
  `app/src/main/java/com/example/muyinteresante/util`)
- Android API 16+ and Java 8

### Remote request policy

The app keeps the passive network observer for UI state and uses `isConnected()`/the
current `NetworkCapabilities` as a cheap gate. When the gate passes, RSS and article
requests go directly to their real endpoint, preserving redirects, HTTP status handling
and exceptions. The RSS request does not impose an artificial connect/read timeout, so
slow but usable mobile connections can complete; the user can still cancel it. A general DNS/HTTP diagnosis is run only after an ambiguous
connectivity failure; a valid HTTP response is never followed by a redundant probe.

Offline starts, refreshes, pagination and retries use the cached news and offline UI
without starting a remote task.

Validation: `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`.

Orientation changes are handled without recreating `MainActivity`, so the loaded
news list and the current `RecyclerView` scroll position are preserved and no RSS
request is started again.

The main header uses a taller, higher-contrast blue area: title/actions occupy the upper row and
network status sits below them inside the same header, preventing overlap on
narrow screens and devices with status-bar insets.

When no usable network is detected, remote loading is skipped before showing any
progress indicator and the app goes directly to cached/offline content.
The network pill starts neutral, follows the passive network snapshot (including
offline/disconnected state), and offline fallback does not stack duplicate Toasts.
VPN-only interfaces such as an active AdGuard tunnel do not count as a usable
network unless a physical Wi-Fi, mobile or Ethernet transport is also present.

This is an independent reader and is not an official elDiario.es application.
