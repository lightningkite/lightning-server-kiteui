# Static site rendering

`StaticSiteRenderer` (in `server-client-utils`) renders KiteUI pages to HTML files on the JVM, plus a
`sitemap.xml`.  Each file loads your JS bundle, which takes over the page: visitors and search engines see content
before the app boots.  It is marked `@Untested`, so opt in with `@OptIn(Untested::class)`.

## Setup

The app module needs a JVM target, so the server can call its `AutoRoutes`, theme and `app()`:

```kotlin
// apps/build.gradle.kts
kotlin {
    jvm()
    // ... other targets
}
```

Give any `expect` declarations in the app a `jvmMain` `actual`.  KiteUI's only JVM target is its SSR engine, so
that is what `jvm()` resolves to.

```kotlin
// server/build.gradle.kts
dependencies {
    implementation(project(":apps"))                                  // AutoRoutes, theme, app()
    implementation(lk.lightningserver.server.client.utils)            // brings com.lightningkite.kiteui:library-jvmssr
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing")  // Dispatchers.Main for SSR
}
```

## Minimal example

```kotlin
@OptIn(Untested::class)
fun staticSite() = runBlocking {
    val renderer = StaticSiteRenderer(
        router = SsrRouter(
            routes = AutoRoutes,
            theme = defaultTheme,
            appWrapper = { navigator -> app(navigator) },   // optional: render inside the app shell
        ),
        outputDir = File("build/static-site"),
        baseUrl = "https://yoursite.com",
        scriptUrls = listOf("https://cdn.yoursite.com/app.js"),
        routes = listOf(
            StaticRoute("/", priority = 1.0, changeFreq = ChangeFreq.Weekly),
            StaticRoute("/about", priority = 0.8),
            StaticRoute("/blog/intro", priority = 0.6),
        ),
    )
    for (result in renderer.renderAll()) println("${result.path} -> ${result.outputFile} (${result.bytes} bytes)")
}
```

```
build/static-site/
  index.html              <- /
  about/index.html        <- /about
  blog/intro/index.html   <- /blog/intro
  sitemap.xml
```

Add `::staticSite` to the server's `cli(available = ...)` list and run `./gradlew :server:run --args="staticSite"`.
The demo does exactly this in [`Main.kt`](../demo-server/src/main/kotlin/com/lightningkite/lskiteuistarter/Main.kt).

| Parameter | Meaning |
|---|---|
| `router` | `SsrRouter` over your `AutoRoutes` and theme, optionally with an `appWrapper` |
| `outputDir` | Where the HTML and `sitemap.xml` go |
| `baseUrl` | Prefix for the sitemap's `<loc>` entries |
| `routes` | Paths to render, with sitemap `priority` and `changeFreq` |
| `scriptUrls` | JS bundles, injected as `<script>` tags before `</body>` |
| `userAgent` | Passed to the renderer, for pages that vary by platform |
| `interceptor` | `(path, html) -> html`, applied last |

## Data and metadata

Data loaded with KiteUI's `ssrResource(key) { ... }` is awaited before the HTML is captured, then embedded in a
`__SSR_DATA__` block so the browser reuses it instead of fetching again.  It is a `Reactive<ReactiveState<T>>`:

```kotlin
override fun ElementWriter.CanAddTheme.render() {
    val product = ssrResource("product-$id") { api.product.detail(id) }
    col {
        h1 { ::content { product().handle(success = { it.name }, notReady = { "Loading..." }, exception = { "Not found" }) } }
    }
}
```

Page titles are picked up automatically.  For a description, OpenGraph and Twitter tags, implement
`SsrPreloadable` and call `context.applyMeta(PageMeta(...))` in `preload`.  Both live only in KiteUI's JVM target,
so a page in `commonMain` can't use them directly.

## Re-rendering

```kotlin
renderer.invalidate("/blog/intro", "/about")           // paths already in `routes`; others are skipped
renderer.invalidate(listOf(StaticRoute("/blog/new-post", priority = 0.7, changeFreq = ChangeFreq.Daily)))
renderer.writeSitemap()                                // invalidate() never touches the sitemap
```

## Post-processing

```kotlin
interceptor = { path, html ->
    if (path.startsWith("/blog/")) html.replace("</head>", """<script type="application/ld+json">{"@type":"Article"}</script></head>""")
    else html
}
```

## Deploying

1. Build and upload the JS bundle.
2. Run `staticSite` with `scriptUrls` pointing at the uploaded bundle.
3. Upload the HTML (or serve it from Lightning Server's file system).

The bundle and the HTML deploy independently; `scriptUrls` is the only link between them.

## Gotchas

- **`IllegalStateException: Module with the Main dispatcher is missing`** means the server lacks
  `kotlinx-coroutines-swing`.
- **API calls during rendering need a reachable server.**  Without one they fail with `Connection refused`;
  `SsrRouter` tolerates that and renders the page without the data.
- **Pages render one at a time**, so a long route list takes a while.
- **Files are large (1-3 MB)** because KiteUI inlines its CSS; serve them gzipped.
