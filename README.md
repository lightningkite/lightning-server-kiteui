# Lightning Server / KiteUI Combo Library

This library bridges [Lightning Server](https://github.com/lightningkite/lightning-server) and [KiteUI](https://github.com/lightningkite/kiteui), providing pre-built form generation, live-querying utilities, type-safe API client helpers, and an auto-generated admin interface. It also ships server-side SSR utilities for static site generation.

## Modules

| Module | Artifact | Target |
|---|---|---|
| `client` | `com.lightningkite.lightningserver:client` | Multiplatform (JVM, JS, Android, iOS) |
| `server-client-utils` | `com.lightningkite.lightningserver:server-client-utils` | JVM (server-side only) |
| `admin` | Internal — consumed via `client` | Multiplatform |

## Gradle dependency

Pick the version matching your Lightning Server version (check the [CHANGELOG](CHANGELOG.md) or the published releases).

```kotlin
// build.gradle.kts (shared/client module — multiplatform or JVM)
dependencies {
    implementation("com.lightningkite.lightningserver:client:<version>")
}

// build.gradle.kts (server module — JVM only; includes StaticSiteRenderer)
dependencies {
    implementation("com.lightningkite.lightningserver:server-client-utils:<version>")
}
```

Artifacts are published to the LightningKite Maven repository:

```kotlin
repositories {
    maven("https://lightningkite-maven.s3.us-west-2.amazonaws.com")
}
```

## Docs

- [Static site rendering](docs/static-site-rendering.md) — generate pre-rendered HTML pages from your KiteUI app at build time
