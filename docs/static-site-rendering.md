# Static Site Rendering

`StaticSiteRenderer` generates static HTML files from your KiteUI app at build time. The rendered pages include your JS bundle for client-side hydration, so users see content immediately while the app boots up. Search engines see fully rendered HTML.

## Setup

### Dependencies

Your server module needs two dependencies beyond the standard Lightning Server ones:

```kotlin
// build.gradle.kts (server module)
dependencies {
    implementation(project(":your-app-module"))       // For AutoRoutes, theme, app()
    implementation(project(":server-client-utils"))    // For StaticSiteRenderer
    implementation(libs.comLightningkiteKiteuiLibraryJvmSsr)  // KiteUI SSR engine
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing")  // Dispatchers.Main for SSR
}
```

Your app module needs a JVM target so the server can access `AutoRoutes` and your theme:

```kotlin
// build.gradle.kts (app module)
kotlin {
    jvm()  // Add this if not already present
    // ... other targets
}
```

You'll also need JVM `actual` implementations for any `expect` functions in your app module.

### Version catalog

Add the SSR library to your version catalog if not already present:

```toml
# gradle/libs.versions.toml
[libraries]
comLightningkiteKiteuiLibraryJvmSsr = { module = "com.lightningkite.kiteui:library-jvmssr", version.ref = "kiteui" }
```

## Basic usage

```kotlin
import com.lightningkite.kiteui.ssr.SsrRouter
import com.lightningkite.lightningserver.ssr.*
import kotlinx.coroutines.runBlocking
import java.io.File

fun staticSite() = runBlocking {
    val router = SsrRouter(
        routes = AutoRoutes,
        theme = defaultTheme,
        appWrapper = { navigator, dialog -> app(navigator, dialog) },
    )
    val renderer = StaticSiteRenderer(
        router = router,
        outputDir = File("build/static-site"),
        baseUrl = "https://yoursite.com",
        scriptUrls = listOf("https://cdn.yoursite.com/app.js"),
        routes = listOf(
            StaticRoute("/", priority = 1.0, changeFreq = ChangeFreq.Weekly),
            StaticRoute("/about", priority = 0.8),
            StaticRoute("/pricing", priority = 0.8, changeFreq = ChangeFreq.Monthly),
            StaticRoute("/blog/intro", priority = 0.6),
        ),
    )

    val results = renderer.renderAll()
    for (result in results) {
        println("  ${result.path} -> ${result.outputFile} (${result.bytes} bytes)")
    }
}
```

This produces:

```
build/static-site/
  index.html              <- /
  about/index.html        <- /about
  pricing/index.html      <- /pricing
  blog/intro/index.html   <- /blog/intro
  sitemap.xml             <- generated from all routes
```

## Parameters

| Parameter | Description |
|-----------|-------------|
| `router` | `SsrRouter` wrapping your app's `AutoRoutes`, theme, and optional `appWrapper` |
| `outputDir` | Directory to write rendered HTML files and sitemap |
| `baseUrl` | Full base URL for sitemap `<loc>` entries (e.g. `"https://yoursite.com"`) |
| `routes` | List of `StaticRoute` paths to render, with sitemap metadata |
| `scriptUrls` | JS bundle URLs for hydration. Injected as `<script>` tags before `</body>` |
| `userAgent` | Optional User-Agent string passed to SSR for platform-specific rendering |
| `interceptor` | Optional `(path, html) -> html` transform applied after rendering |

## Hydration

The `scriptUrls` parameter injects `<script>` tags into every rendered page, pointing at your deployed JS bundle. When a user loads the page:

1. Browser renders the SSR'd HTML immediately (no blank page)
2. JS bundle loads and the KiteUI app boots
3. App takes over the DOM and becomes fully interactive

Point `scriptUrls` at wherever your JS bundle is deployed -- CDN, S3 bucket, same-origin path, etc. The JS bundle comes from your standard `jsBrowserProductionVite` or `jsBrowserProductionWebpack` build; it's deployed separately from the static HTML.

The rendered HTML also includes an embedded `__SSR_DATA__` block with serialized resource data, enabling `ssrResource()` calls to skip network fetches on first load.

## SEO metadata

KiteUI's SSR system automatically picks up page titles. For richer metadata, implement `SsrPreloadable` on your page:

```kotlin
@Routable("/products/{id}")
class ProductPage(val id: String) : Page, SsrPreloadable {
    var product: Product? = null
        private set

    override suspend fun preload(context: SsrContext) {
        product = api.fetchProduct(id)

        context.applyMeta(PageMeta(
            title = product?.name ?: "Product",
            description = product?.description,
            canonicalUrl = "https://yoursite.com/products/$id",
            openGraph = OpenGraph(
                type = OpenGraph.Type.PRODUCT,
                image = product?.imageUrl,
                siteName = "Your Store",
            ),
            twitter = TwitterCard.summary(site = "@yourhandle"),
        ))
    }

    override fun ViewWriter.render() = col {
        // product is populated during SSR, null on client until hydration
        // ...
    }
}
```

This generates proper `<title>`, `<meta name="description">`, OpenGraph, and Twitter Card tags in the rendered HTML.

For pages using the newer `ssrResource()` pattern instead of `SsrPreloadable`, resource data is automatically embedded in `__SSR_DATA__` and available to the client without a network round-trip.

## Re-rendering specific pages

After content changes, re-render specific paths without rebuilding everything:

```kotlin
// By path (looks up from the original routes list)
renderer.invalidate("/blog/intro", "/pricing")

// With custom route metadata (for paths not in the original list)
renderer.invalidate(listOf(
    StaticRoute("/blog/new-post", priority = 0.7, changeFreq = ChangeFreq.Daily),
))
```

`invalidate` does **not** regenerate `sitemap.xml`. Call `renderer.writeSitemap()` separately if needed.

## Post-processing with interceptor

The `interceptor` receives `(path, html)` after rendering and script injection. Use it for per-page customization:

```kotlin
val renderer = StaticSiteRenderer(
    // ...
    interceptor = { path, html ->
        var result = html
        // Add analytics to all pages
        result = result.replace("</head>", """<script src="/analytics.js"></script></head>""")
        // Add structured data to blog pages
        if (path.startsWith("/blog/")) {
            result = result.replace("</head>", """<script type="application/ld+json">{"@type":"Article"}</script></head>""")
        }
        result
    },
)
```

## Wiring into your server CLI

A typical pattern is to add a `staticSite` command alongside `serve` and `sdk`:

```kotlin
fun main(vararg args: String) = cli(
    arguments = args,
    setup = ::setup,
    available = listOf(
        ::serve,
        ::sdk,
        ::staticSite,
    ),
    useInteractive = true,
)
```

Then run:

```bash
./gradlew :your-server:run --args="staticSite"
```

## Deployment

The typical flow:

1. Build JS bundle: `./gradlew :your-app:jsBrowserProductionVite`
2. Deploy JS bundle to CDN/bucket
3. Generate static HTML: `./gradlew :your-server:run --args="staticSite"`
4. Deploy static HTML to CDN/bucket (or serve via Lightning Server's `PublicFileSystem`)

The static HTML and JS bundle are deployed independently. The `scriptUrls` in `StaticSiteRenderer` is what connects them -- it tells the HTML where to find the JS.

## Notes

- SSR rendering is sequential. Each page is rendered one at a time.
- Pages that make network calls during SSR (e.g. SDK calls to your API) will see `Connection refused` errors if the server isn't running. These are non-fatal; `SsrRouter` handles them gracefully and rendering still completes.
- The `kotlinx-coroutines-swing` dependency provides `Dispatchers.Main` which KiteUI needs during SSR initialization. Without it you'll get `IllegalStateException: Module with the Main dispatcher is missing`.
- File sizes can be large (1-3MB) because KiteUI inlines CSS. This is normal; gzip compression brings them down significantly.
