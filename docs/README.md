# Lightning Server + KiteUI docs

This library connects a [Lightning Server](https://github.com/lightningkite/lightning-server) backend to a
[KiteUI](https://github.com/lightningkite/kiteui) app.  The server describes its API; this library turns that
description into a typed client, a live client-side cache, ready-made login screens, forms, and an admin panel.

## Learning path

Read in order the first time; each chapter assumes the ones before it.

1. [Getting started](getting-started.md): Gradle setup and the server + shared + app project layout.
2. [The generated API client](api-client.md): `generateSdk`, `Api`/`LiveApi`/`CachedApi`, fetchers, sessions.
3. [ModelCache and live models](model-cache.md): reactive lists and items that stay current.
4. [Auth screens](auth.md): `AuthComponent`, `ReAuthComponent`, `SecurityComponent`.
5. [Forms](forms.md): `form()`, `view()`, `renderTable()` from a serializer, and custom renderers.
6. [Admin panel](admin.md): the hosted CRUD console every server gets from `MetaEndpoints`.
7. [Static site rendering](static-site-rendering.md): pre-render pages to HTML with `StaticSiteRenderer`.
8. [Testing](testing.md): mocks for `ModelCache`, virtual time, and full-stack tests.
9. [Demo apps tour](demo-apps.md): a working server and app that use every feature above.

## Which module do I need?

| You are writing | Depend on | Gives you |
|---|---|---|
| A KiteUI app (Android, iOS, JS, JVM) | `client` | Fetchers, `ModelCache`, auth components, forms engine |
| The server's SDK generator or static site | `server-client-utils` (JVM) | `CachingSdk`, `StaticSiteRenderer` |
| Nothing: you want the admin panel | nothing | Mount `MetaEndpoints`, open `/meta/admin/` |

`admin` and the `demo-*` modules are not published.

## Elsewhere

- Lightning Server's own guide: models, endpoints, permissions, sessions (`docs-guide/guide` in that repo).
- KiteUI's docs: views, reactivity, navigation, theming.
- [Agent notes](../.claude/CLAUDE.md): build commands and internals, for working on this repo.
