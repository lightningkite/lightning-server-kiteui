# Demo apps tour

`demo-shared`, `demo-server`, `demo-apps` and `demo-android` are a small but real app built on this library.  They
exist to exercise its features against a live server, so for almost any question of "how do I use X", some page here
does it.  None of them is published.

| Module | What it is |
|---|---|
| `demo-shared` | `@Queryable` models used by both sides ([`models.kt`](../demo-shared/src/commonMain/kotlin/com/lightningkite/lskiteuistarter/models.kt)) |
| `demo-server` | The Lightning Server backend: endpoints, `UserAuth`, and the `serve`, `sdk`, `staticSite` commands |
| `demo-apps` | The KiteUI app (Android library, JVM, JS, iOS) and its generated SDK in `sdk/` |
| `demo-android` | The installable Android shell around `demo-apps` (AGP 9 won't put an app in a KMP module) |

## Run it

```bash
./gradlew :demo-server:run --args="serve"      # API on http://localhost:8090 (reads ./settings.json)
./gradlew :demo-apps:jsViteDev                 # web app on http://localhost:8091, proxying /api to 8090
./gradlew :demo-android:installDebug           # Android, on a connected device or emulator
```

IntelliJ users can use the `Serve` run configuration in `.run/`.  The iOS targets compile
(`./gradlew :demo-apps:compileKotlinIosSimulatorArm64`), but there is no Xcode project for the demo.

Ports: the backend is 8090 and the web app 8091 (`ApiOption.Local` in
[`apiOptions.kt`](../demo-apps/src/commonMain/kotlin/com/lightningkite/lskiteuistarter/sdk/apiOptions.kt) and
[`vite.config.mjs`](../demo-apps/vite/vite.config.mjs), which also honors `BACKEND_PORT`/`FRONTEND_PORT`).
The scripted harness in `testing/` (`setup.sh`, `stop.sh`, `api.sh`, `rebuild.sh`) uses 8020/8021 from
`testing/config.env` instead, so it never collides with a dev server.  Lightning Server settings can't read
environment variables, so if you change those ports also change them in `testing/settings.json`.

After changing a model or endpoint, regenerate the client:

```bash
./gradlew :demo-server:generateSdk     # rewrites demo-apps/.../sdk/{Api,LiveApi,CachedApi}.kt
```

## Pages

| Route | Shows | Read |
|---|---|---|
| `/` | Landing page, signed out | `LandingScreen.kt` |
| `/login` | `AuthComponent` with email, TOTP, password and backup-code proofs; a hidden server picker | `LoginScreen.kt` |
| `/dashboard` | Signed-in home: FCM test notification, Security, logout | `HomeScreen.kt` |
| `/security` | `SecurityComponent` with an elevated session | `SecurityPage.kt` |
| `/users` | `recyclerView` + `children()` paging a `ModelCache` list | `UserListPage.kt` |
| `/users/{id}` | Editing through the cache: draft signal, `lens(User.name)`, `modification(old, new)`, `modify()`, `delete()`.  Also where a `@References(User::class)` field links to | `UserDetailPage.kt` |
| `/forms` | Index of the forms pages below | `forms/FormsIndexPage.kt` |
| `/forms/app-releases`, `/{id}` | `form()` and `renderTable()` on a plain data class: `String`, `Boolean`, `LocalDate`, nullable enum, nullable list of objects | `forms/AppReleasePages.kt` |
| `/forms/sealed`, `/{id}` | The sealed-class renderers, as a nullable field and as a list | `forms/SealedModelPages.kt` |
| `/forms/documents`, `/{id}` | `Set`, `Map`, a foreign key, `ServerFile` uploads, `ServerFile.asImage()` | `forms/DocumentPages.kt` |
| `/race` | A live leaderboard built as a `ModelCache` correctness harness: an oracle reading the server directly, storm and soak runners, socket-cut and offline toggles, one-click scenario checks | `RaceConsolePage.kt`, `pressure/` |
| `/health` | `/meta/health`: every configured service and which implementation answered | `HealthPage.kt` |

Pages are in [`demo-apps/src/commonMain/kotlin/com/lightningkite/lskiteuistarter/`](../demo-apps/src/commonMain/kotlin/com/lightningkite/lskiteuistarter/).

## Worth copying

- [`sdk/currentSession.kt`](../demo-apps/src/commonMain/kotlin/com/lightningkite/lskiteuistarter/sdk/currentSession.kt):
  the session token, the reactive session, and turning "session terminated" errors into a sign-out.
- [`sdk/UserSession.kt`](../demo-apps/src/commonMain/kotlin/com/lightningkite/lskiteuistarter/sdk/UserSession.kt):
  overriding generated caches to add a log tag and `versionOf`.
- [`forms/FormsSupport.kt`](../demo-apps/src/commonMain/kotlin/com/lightningkite/lskiteuistarter/forms/FormsSupport.kt):
  a `FormModule` with file uploads and foreign keys wired to the session.
- [`Server.kt`](../demo-server/src/main/kotlin/com/lightningkite/lskiteuistarter/Server.kt) and
  [`UserAuth.kt`](../demo-server/src/main/kotlin/com/lightningkite/lskiteuistarter/UserAuth.kt): a complete server
  with proofs, uploads, multiplexed sockets and `MetaEndpoints`.
- [`data/RaceEndpoints.kt`](../demo-server/src/main/kotlin/com/lightningkite/lskiteuistarter/data/RaceEndpoints.kt):
  a model with an update socket (`ModelRestEndpoints(info) + ModelRestUpdatesWebSocket(info)`) and a version counter.

## Not for production

The demo signs anyone up who can receive an email, seeds a hard-coded root user, prints a root session token at
startup when `general.debug` is on, and gives every signed-in user full access to several tables.  Those are test
conveniences; don't carry them into a real server.
