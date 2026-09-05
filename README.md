# Noticias elDiario.es

Independent Android RSS reader for elDiario.es.

## Features
- Live RSS: https://www.eldiario.es/rss/
- RecyclerView list, search, pull-to-refresh, sharing and article view
- Offline JSON and image cache
- Passive network observation and explicit connectivity diagnostics
- Complete ConnectivityAndInternetAccess gist vendored under third_party/connectivity
- Android API 16+ and Java 8

### Remote request policy

The app keeps the passive network observer for UI state and uses `isConnected()`/the
current `NetworkCapabilities` as a cheap gate. When the gate passes, RSS and article
requests go directly to their real endpoint, preserving redirects, HTTP status handling,
timeouts and exceptions. A general DNS/HTTP diagnosis is run only after an ambiguous
connectivity failure; a valid HTTP response is never followed by a redundant probe.

Offline starts, refreshes, pagination and retries use the cached news and offline UI
without starting a remote task.

Validation: `./gradlew :app:testDebugUnitTest :app:lintDebug`.

This is an independent reader and is not an official elDiario.es application.
