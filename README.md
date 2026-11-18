# Lightning Server + KiteUI

Glue between a [Lightning Server](https://github.com/lightningkite/lightning-server) backend and a
[KiteUI](https://github.com/lightningkite/kiteui) app.  Your server generates a typed client; this library makes it
live and gives it UI:

- **`ModelCache`**: reactive lists and items that load once, share data across screens, and update over websockets.
- **Auth screens**: a complete multi-proof login, re-authentication, and security settings, from your server's
  proof endpoints.
- **Forms**: editable forms, read-only views and tables generated from any `@Serializable` class.
- **Admin panel**: a CRUD console for every model, served by any server that mounts `MetaEndpoints`.
- **Static site rendering**: pre-render KiteUI pages to HTML with a sitemap.

```kotlin
// A live, paginated list of the signed-in user's open todos.
val todos = remember { currentSessionNotNull().todos.list(Query { it.done eq false }) }
col {
    expanding.recyclerView {
        children(items = todos) { todo -> text { ::content { todo().title } } }
    }
}
```

## Install

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories { maven("https://lightningkite-maven.s3.us-west-2.amazonaws.com"); google(); mavenCentral() }
    versionCatalogs { create("lk") { from("com.lightningkite:stack-catalog:1000.0.0") } }
}

// apps/build.gradle.kts (KMP: Android, iOS, JS, JVM)
commonMain.dependencies { api(lk.lightningserver.client) }

// server/build.gradle.kts (JVM): CachingSdk and StaticSiteRenderer
dependencies { implementation(lk.lightningserver.server.client.utils) }
```

Coordinates: `com.lightningkite.lightningserver:client` and `com.lightningkite.lightningserver:server-client-utils`.
Or skip the setup and copy [ls-kiteui-starter](https://github.com/lightningkite/ls-kiteui-starter).

## Docs

Start at [docs/](docs/README.md):
[getting started](docs/getting-started.md) ·
[API client](docs/api-client.md) ·
[ModelCache](docs/model-cache.md) ·
[auth](docs/auth.md) ·
[forms](docs/forms.md) ·
[admin](docs/admin.md) ·
[static sites](docs/static-site-rendering.md) ·
[testing](docs/testing.md) ·
[demo apps](docs/demo-apps.md)

## Repository

| Module | Published | Contents |
|---|---|---|
| `client` | yes | Fetchers, `ModelCache`, auth components, forms engine |
| `server-client-utils` | yes | `CachingSdk`, `StaticSiteRenderer` |
| `admin` | no (hosted) | The admin panel web app |
| `demo-shared`, `demo-server`, `demo-apps`, `demo-android` | no | A working app using all of the above |

Changes are in the [CHANGELOG](CHANGELOG.md).  Notes for working on this repo: [.claude/CLAUDE.md](.claude/CLAUDE.md).
