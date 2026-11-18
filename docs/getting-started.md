# Getting started

A project built on this library has three modules: models shared by both sides, a JVM server, and a KiteUI app.
The fastest start is to copy [ls-kiteui-starter](https://github.com/lightningkite/ls-kiteui-starter), which is
this layout already wired up.  This page explains what it contains, so you can build or repair one yourself.

```
my-project/
├── shared/   KMP: @Queryable models, used by server and app
├── server/   JVM: Lightning Server endpoints; also generates the app's SDK
└── apps/     KMP: KiteUI app (Android, iOS, JS, JVM); the generated SDK lives here
```

## Gradle

Artifacts are on Lightning Kite's Maven repository.  Import the stack catalog, which names every Lightning Kite
library at versions that work together:

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        maven("https://lightningkite-maven.s3.us-west-2.amazonaws.com")
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositories {
        maven("https://lightningkite-maven.s3.us-west-2.amazonaws.com")
        google()
        mavenCentral()
    }
    versionCatalogs {
        create("lk") { from("com.lightningkite:stack-catalog:1000.0.0") }
    }
}
```

| Catalog entry | Coordinates | Used by |
|---|---|---|
| `lk.lightningserver.client` | `com.lightningkite.lightningserver:client` | `apps` |
| `lk.lightningserver.server.client.utils` | `com.lightningkite.lightningserver:server-client-utils` | `server` |
| `lk.stack.bom` | `com.lightningkite:stack-bom` | `shared` (lifts transitive versions) |

If you only want this repo's two artifacts, `com.lightningkite.stack:lightning-server-kiteui-catalog` names them
as `client` and `server-client-utils`.

### `shared`

```kotlin
kotlin {
    sourceSets.commonMain.dependencies {
        api(project.dependencies.platform(lk.stack.bom))
        api(lk.lightningserver.core.shared)
        api(lk.lightningserver.typed.shared)
        api(lk.lightningserver.sessions.shared)
        api(lk.lightningserver.files.shared)
    }
}
dependencies {
    // Generates the typed fields (User.email, ...) that queries and forms use.
    configurations.filter { it.name.startsWith("ksp") && it.name != "ksp" }
        .forEach { add(it.name, lk.services.database.processor) }
}
```

### `apps`

```kotlin
plugins {
    alias(lk.plugins.kiteui)
    // plus kotlin-multiplatform, kotlin-serialization, the Android KMP library plugin, ...
}
kotlin {
    jvm()                 // only if the server renders pages (see static-site-rendering.md)
    js { browser(); binaries.executable() }
    // android { ... }, iosArm64(), iosSimulatorArm64()
    sourceSets.commonMain.dependencies {
        api(lk.kiteui.library.asProvider())
        api(lk.lightningserver.client)
        api(project(":shared"))
    }
}
```

`client` brings Lightning Server's shared modules, Service Abstractions' database types, and KiteUI with it.

### `server`

```kotlin
dependencies {
    implementation(project(":shared"))
    implementation(lk.lightningserver.core.asProvider())
    implementation(lk.lightningserver.typed.asProvider())
    implementation(lk.lightningserver.sessions.asProvider())
    implementation(lk.lightningserver.server.client.utils)   // CachingSdk
    ksp(lk.services.database.processor)
}

tasks.register("generateSdk", JavaExec::class) {
    classpath(sourceSets.main.get().runtimeClasspath)
    mainClass.set("com.example.MainKt")
    args("sdk")
    workingDir(rootDir)
}
```

## What the server must mount

The client assumes a few endpoints exist.  This is the minimum `Server` object the rest of these docs build on:

```kotlin
object Server : ServerBuilder() {
    val database = setting("database", Database.Settings())
    val cache = setting("cache", Cache.Settings())

    // One `module` per model: `include` merges endpoints into the parent's SDK interface,
    // and an interface can carry only one model.
    val todos = path.path("todos") module TodoEndpoints

    // BulkFetcher batches calls through /meta/bulk; the admin panel reads /meta/kschema.
    val meta = path.path("meta") module MetaEndpoints("com.example", database, cache)

    // BulkFetcher opens one socket at `?path=/multiplex` and multiplexes every live query over it.
    val multiplex = path.path("multiplex") bind MultiplexWebSocketHandler()
    val socketRoot = path bind QueryParamWebSocketHandler()
}

object TodoEndpoints : ServerBuilder() {
    val info = Server.database.modelInfo(
        auth = UserAuth.require(),
        tableName = "Todo",
        permissions = { ModelPermissions(read = Condition<Todo> { it.owner eq auth.id }, /* ... */) },
    )
    // `+ ModelRestUpdatesWebSocket` makes the model live: ModelCache subscribes instead of polling.
    val rest = path include ModelRestEndpoints(info) + ModelRestUpdatesWebSocket(info)
}
```

`ModelRestEndpointsAndUpdatesWebSocket.Companion.plus` must be imported for that `+`.  Authentication
(`UserAuth`) is covered in [auth.md](auth.md); the demo's
[`Server.kt`](../demo-server/src/main/kotlin/com/lightningkite/lskiteuistarter/Server.kt) is a complete example.

## The development loop

1. Change a model in `shared` or an endpoint in `server`.
2. `./gradlew :server:generateSdk` rewrites `Api.kt`, `LiveApi.kt` and `CachedApi.kt` in `apps`
   ([api-client.md](api-client.md)).
3. Use the new calls and caches from the app.
4. Run the server and the web app; with Vite, proxy `/api` to the server so the app and API share an origin
   and need no CORS (see [`vite.config.mjs`](../demo-apps/vite/vite.config.mjs)).

## Gotchas

- **Commit the generated SDK.**  The app compiles from it; nothing regenerates it during a build.
- **iOS targets need a KiteUI published from a Mac.**  This repo and the starter declare iOS only on macOS
  (override with `-PlsKiteui.ios=true|false`).
- **AGP 9 forbids `com.android.application` in a KMP module**, so the installable app is a separate thin module
  (`demo-android` here, `apps-android` in the starter) that depends on `apps`.
- **Android:** the app module's `namespace` must equal the KiteUI plugin's `packageName`; generated resources use
  an unqualified `R`.
