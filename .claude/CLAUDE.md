# CLAUDE.md

Notes for working on this repo.  User-facing docs are in [docs/](../docs/README.md); read the chapter for the area
you are changing, and update it when behavior changes.

## Modules

| Module | Targets | What |
|---|---|---|
| `client` | Android, JVM, JS, iOS (macOS only) | Fetchers, `ModelCache`, auth components, forms engine.  Published. |
| `server-client-utils` | JVM | `CachingSdk` (generates `CachedApi.kt`), `StaticSiteRenderer`.  Published. |
| `admin` | JS | The admin panel, hosted by Lightning Kite and served by `MetaEndpoints`.  Not published. |
| `demo-shared`, `demo-server`, `demo-apps`, `demo-android` | | A working app exercising everything; see `docs/demo-apps.md`. |

Versions live in each published module's `gradle.properties` and are edited only by the `lk` release tool
(metawork).  Never run `publishToMavenLocal` by hand.

## Commands

```bash
./gradlew :client:jvmTest                          # fastest feedback for ModelCache/forms/networking
./gradlew :client:jvmTest --tests "*.CoverageStoreTest"
./gradlew :admin:jsTest
./gradlew allTests
./gradlew :admin:jsViteDev                         # admin dev server
./gradlew :demo-server:run --args="serve"          # demo API on :8090
./gradlew :demo-apps:jsViteDev                     # demo web app on :8091
./gradlew :demo-server:generateSdk                 # after changing demo models/endpoints
```

iOS targets are declared only on macOS; override with `-PlsKiteui.ios=true|false`.  Use `./local/` or `tmp/` for
scratch files.

## Where things are

- `client/.../lightningserver/db/ModelCache.kt`: the cache; `CoverageStore.kt` is what it knows and why.
  `ModelCacheLike.kt` documents `maximumAge`/`pullFrequency`.
- `client/.../lightningserver/db/*Mock*.kt`: test doubles, also used by the app-level tests.
- `client/.../lightningserver/networking/`: `BulkFetcher`, `ConnectivityFetcher`, socket adapters.
- `client/.../lightningserver/auth/authInterfaces.kt`: `AuthEndpoints`, `accessToken`.
- `client/.../kiteui/auth/`: `AuthComponent`, `ReAuthComponent`, `SecurityComponent`, one `ProofComponent` per proof.
- `client/.../kiteui/forms/`: `FormModule` (renderer registry), `FormModule.defaults.kt` (built-ins), one file per renderer.
- `admin/.../admin/schema.kt`: `ExternalLightningServer`, turning `/meta/kschema` into caches and auth endpoints.
- `admin/.../admin/CollectionAdminScreen.kt`, `DetailAdminScreen.kt`, `ItemActions.kt`: the main screens.

## How it fits together

- **ModelCache** keeps every row once in a `CoverageStore`, with timestamped claims about which queries it knows
  completely.  A read is answered locally only when a claim backs it.  An ID lookup is a one-row query.  Claims mostly
  come from one inference: a query returning fewer rows than its limit has seen everything matching.  Pagination
  extends a claim with a cursor (`orderBy.after(boundary)`), which is why `list()` forbids `skip` and makes the
  sort total.
- **Sockets**: a reader with `pullFrequency < 30s` subscribes through `SharedCollectionUpdatesSocket` when the
  endpoints implement `ClientModelRestUpdatesWebSocket`; while not covered it polls at the 5 s floor.
- **Forms**: a `Renderer` is chosen per `RenderContext` (serializer + field annotations) by `Selector` match, then
  highest `priority`; ties go to the earliest registered.
- **Admin**: everything is derived at runtime from the server's `LightningServerKSchema`; there is no per-project
  admin code.

## Conventions

- `explicitApi()` is on in `client` and `server-client-utils`: public declarations need explicit visibility.
- KSP (`sa.database.processor`) generates typed fields into `build/generated/ksp/`; `ksp.incremental=false` works
  around a KSP stack overflow (see `gradle.properties`).
- Prefer `Condition { }` / `Modification { }`; the lowercase builders are deprecated.
