# Admin panel

Every Lightning Server that mounts `MetaEndpoints` gets a CRUD console for all its REST models, with no code in
your project.  The console is the `admin` module of this repo, built once and hosted by Lightning Kite; your server
serves it under its own domain and points it at itself.

## Turning it on

```kotlin
object Server : ServerBuilder() {
    val meta = path.path("meta") module MetaEndpoints("com.example", database, cache)
    // ...
}
```

Then open `https://<your server>/meta/admin/` and sign in as any user your auth allows.

What happens: the server fetches the hosted page (`https://ls5admin.cs.lightningkite.com`), injects
`{"url": "<general.publicUrl>"}` and a `<base href>`, and returns it.  The admin reads the server's schema from
`/meta/kschema` and builds every screen from it.  `/meta/admin-beta/` serves the beta deployment the same way.

## What you get

- **Navigation** for every collection the signed-in user can read (`read != Condition.Never`), grouped by `@Group`.
- **Collection screens:** text search, a condition builder for filters, multi-field sort, chosen columns, CSV
  import and export, bulk delete.  Filter, sort and columns live in the URL, so a view can be bookmarked or shared.
- **Live data:** a toggle subscribes the page to the model's update socket, when the server mounted one.
- **Detail and new-item screens** built with the [forms engine](forms.md), so the same annotations apply:
  `@AdminHidden`, `@AdminViewOnly`, `@Description`, `@References`, `@AdminTableColumns`, `@AdminTitleFields`, ...
- **Item actions:** custom endpoints that take one item's ID (as the route parameter or the whole body) appear as
  buttons on that item's page, each behind a confirmation dialog.
- **Stats** per collection, and an **Endpoints** explorer (off by default; switch it on from the Home page).
- **File uploads,** when the server mounts `UploadEarlyEndpoint`.

## Permissions

The admin is only a client: it sees and does exactly what the server's `ModelPermissions` allow the signed-in
user, field masks and update restrictions included.  Hiding a field with `@AdminHidden` is presentation, not
security.

## Gotchas

- **`general.publicUrl` must be the server's real public URL**, or the admin loads its schema and scripts from the
  wrong place.
- **The schema path is fixed:** mount `MetaEndpoints` at `meta`.
- **The server needs outbound HTTPS** to the hosted deployment each time the page loads.
- **Destructive bulk actions are locked** until "Enable destructive actions" is turned on from the Home page; the
  unlock lasts 30 minutes.

## Working on the admin itself

`admin` is a JS-only KiteUI app.  `./gradlew :admin:jsViteDev` serves it locally; set the server URL from the
account menu (it defaults to `http://localhost:8080`).  `./gradlew :admin:jsBrowserProductionVite` builds the
bundle, and `deployWebls5` / `deployWebbeta` push it with Terraform (Lightning Kite AWS credentials required).
