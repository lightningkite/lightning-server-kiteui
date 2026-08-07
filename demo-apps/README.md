# Demo: `demo-shared` / `demo-server` / `demo-apps`

A working Lightning Server + KiteUI app used to exercise this library's own features - the forms
engine, `ModelCache`, file uploads, authentication - against a real server, not mocks. If you're
looking for how to use a particular piece of `client` or `server-client-utils`, there is probably
a page here doing it against live data.

- `demo-shared` - KMP models shared by client and server.
- `demo-server` - the JVM Lightning Server backend.
- `demo-apps` - the KiteUI app (Android / JVM / JS / iOS).

## Routes and what each demonstrates

| Route | Demonstrates |
|---|---|
| `/` | Landing page (unauthenticated). |
| `/login` | `AuthComponent` - email/TOTP/password/backup-code login flows. |
| `/dashboard` | Post-login home: FCM test notification, logout. |
| `/users` | `recyclerView` + `children()` pagination over a `ModelCache` query. |
| `/users/{id}` | Editing through the raw `ModelCache` write API: `ModelCacheItemReadable.modify()`/`.delete()`, field binding via `com.lightningkite.serialization.lensPath` + `.asString()`, and diffing a save with `lightningdb.modification(old, new)`. Also the page a `@References(User::class)` foreign key opens. |
| `/race` | A live race leaderboard built as a correctness harness for `ModelCache` - an oracle, storm/soak runners, socket-cut and offline toggles, and one-click scenario assertions. See the doc comments in `RaceConsolePage.kt` for what each scenario checks. |
| `/forms` | Index of the forms-engine demo below. |
| `/forms/app-releases`, `/forms/app-releases/{id}` | `com.lightningkite.kiteui.forms`: a plain data class (`AppRelease`) with a `String`, a `Boolean`, a `LocalDate`, a nullable enum, and a nullable `List<object>` field, edited with `form()` and listed with `renderTable()`. |
| `/forms/sealed`, `/forms/sealed/{id}` | The sealed-polymorphic renderer's reason to exist: `SealedPolymorhphicModel` wraps `SealedClassItem` (a plain Kotlin `sealed class`) as both a nullable single field and a list. |
| `/forms/documents`, `/forms/documents/{id}` | `Document`, the richest fixture: `Set<String>`, `Map<String, String>`, a `@References(User::class)` foreign key (`ForeignKeyRenderer`), a nullable `ServerFile` and a `List<ServerFile>` uploaded through `UploadEarlyEndpoint` (`ServerFileRenderer`), and a plain `Instant`. The edit page also calls `ServerFile.asImage()` directly to preview the attachment, the way an ordinary screen would. |
| `/health` | Renders `/meta/health` - every configured subsystem and which implementation answered, straight from `MetaEndpoints`. |

## Running it

### Server

```bash
./gradlew :demo-server:run --args="--settings settings.json serve"
```

Or use the `Serve` run configuration in `.run/` (IntelliJ). Listens on **port 8090** per the root
`settings.json` (`general.publicUrl` / `ktorRunConfig.port`).

### Web (Vite dev server)

```bash
./gradlew :demo-apps:jsViteDev
```

Serves on **port 8091** (`demo-apps/vite/vite.config.mjs`) and proxies `/api` to the backend on
8090. Open http://localhost:8091.

### Android

```bash
./gradlew :demo-apps:installDebug
```

### iOS

Open `example-app-ios` in Xcode, or build the `iosArm64`/`iosSimulatorArm64` targets directly with
Gradle/KMP tooling.

### Port convention

Backend **8090**, web frontend **8091**, for interactive dev (`./gradlew :demo-server:run` /
`:demo-apps:jsViteDev` as above). Root `settings.json` and `ApiOption.Local` in
`demo-apps/.../sdk/apiOptions.kt` use these as fixed defaults; `demo-apps/vite/vite.config.mjs`
uses them as defaults too, but reads `FRONTEND_PORT`/`BACKEND_PORT` env vars first.

The `testing/` harness below runs on **8020/8021** instead - deliberately different, so a
scripted test run can boot without colliding with, or silently talking to, a dev server you
already have open on 8090/8091.

### `testing/` harness

`testing/setup.sh` (and `stop.sh`, `api.sh`, `rebuild.sh`) start/stop the server and Vite dev
server in the background for scripted testing. Ports come from `testing/config.env`
(`BACKEND_PORT`/`FRONTEND_PORT`, default 8020/8021); `setup.sh` exports them for Vite to read, and
`testing/settings.json` - the settings file the harness passes to the server - hardcodes the same
values in `ktorRunConfig.port` etc., since Lightning Server settings don't support env var
interpolation. If you change the ports in `config.env`, update `testing/settings.json` (and
`settings.suggested.json`) to match.

## Regenerating the SDK

After changing any server model or endpoint:

```bash
./gradlew :demo-server:generateSdk
```

This rewrites `demo-apps/src/commonMain/kotlin/com/lightningkite/lskiteuistarter/sdk/{Api,LiveApi,CachedApi}.kt`.
Review the diff - `CachedApi.kt` gets one `ModelCache` property per model automatically.
