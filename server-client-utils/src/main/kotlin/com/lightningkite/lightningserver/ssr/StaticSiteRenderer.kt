package com.lightningkite.lightningserver.ssr

import com.lightningkite.kiteui.ssr.SsrRouter
import com.lightningkite.services.Untested
import java.io.File

/**
 * Metadata for a route to be included in static site generation and sitemap output.
 */
public data class StaticRoute(
    val path: String,
    val priority: Double = 0.5,
    val changeFreq: ChangeFreq? = null,
)

/**
 * Sitemap change frequency values per the sitemap protocol.
 */
public enum class ChangeFreq(public val value: String) {
    Always("always"),
    Hourly("hourly"),
    Daily("daily"),
    Weekly("weekly"),
    Monthly("monthly"),
    Yearly("yearly"),
    Never("never"),
}

/**
 * Result of rendering a single route to disk.
 */
public data class RenderResult(
    val path: String,
    val outputFile: File,
    val bytes: Long,
)

/**
 * Renders KiteUI pages to static HTML files using [SsrRouter] and generates a sitemap.
 *
 * The rendered HTML includes script tags for [scriptUrls] so the JS app hydrates on load.
 * Point these at your deployed JS bundle (CDN, bucket, etc.).
 *
 * Usage:
 * ```kotlin
 * val renderer = StaticSiteRenderer(
 *     router = SsrRouter(AutoRoutes, myTheme, appWrapper = { n, d -> app(n, d) }),
 *     outputDir = File("build/site"),
 *     baseUrl = "https://example.com",
 *     scriptUrls = listOf("https://cdn.example.com/app.js"),
 *     routes = listOf(
 *         StaticRoute("/", priority = 1.0, changeFreq = ChangeFreq.Weekly),
 *         StaticRoute("/about", priority = 0.8),
 *     ),
 * )
 * renderer.renderAll()
 * ```
 */
@Untested
public class StaticSiteRenderer(
    public val router: SsrRouter,
    public val outputDir: File,
    public val baseUrl: String,
    public val routes: List<StaticRoute>,
    /** URLs of JS bundles to include for client-side hydration. Injected as script tags before `</body>`. */
    public val scriptUrls: List<String> = emptyList(),
    public val userAgent: String? = null,
    public val interceptor: ((path: String, html: String) -> String)? = null,
) {
    /**
     * Renders all [routes] to [outputDir] and writes `sitemap.xml`.
     */
    public suspend fun renderAll(): List<RenderResult> {
        val results = routes.map { route -> renderRoute(route) }
        writeSitemap()
        return results
    }

    /**
     * Re-renders specific paths by looking them up in the [routes] list.
     * Paths not found in [routes] are skipped.
     */
    public suspend fun invalidate(vararg paths: String): List<RenderResult> {
        val routesByPath = routes.associateBy { it.path }
        return paths.mapNotNull { path ->
            routesByPath[path]?.let { renderRoute(it) }
        }
    }

    /**
     * Re-renders specific routes that may or may not be in the original [routes] list.
     * Useful for dynamically discovered content.
     */
    public suspend fun invalidate(routes: List<StaticRoute>): List<RenderResult> {
        return routes.map { route -> renderRoute(route) }
    }

    /**
     * Generates and writes `sitemap.xml` to [outputDir].
     */
    public fun writeSitemap(): File {
        val file = outputDir.resolve("sitemap.xml")
        file.parentFile.mkdirs()
        file.writeText(generateSitemap())
        return file
    }

    /**
     * Generates sitemap XML content for all [routes].
     */
    public fun generateSitemap(): String = buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        appendLine("""<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">""")
        val base = baseUrl.trimEnd('/')
        for (route in routes) {
            appendLine("  <url>")
            appendLine("    <loc>${xmlEscape(base + route.path)}</loc>")
            appendLine("    <priority>${route.priority}</priority>")
            if (route.changeFreq != null) {
                appendLine("    <changefreq>${route.changeFreq.value}</changefreq>")
            }
            appendLine("  </url>")
        }
        appendLine("</urlset>")
    }

    private suspend fun renderRoute(route: StaticRoute): RenderResult {
        var html = router.renderOrFallbackWithPreload(route.path, userAgent)
        if (scriptUrls.isNotEmpty()) {
            val scriptTags = scriptUrls.joinToString("\n") { url ->
                """<script src="${htmlEscapeAttr(url)}"></script>"""
            }
            html = html.replace("</body>", "$scriptTags\n</body>")
        }
        if (interceptor != null) {
            html = interceptor.invoke(route.path, html)
        }
        val outputFile = outputFileFor(route.path)
        outputFile.parentFile.mkdirs()
        outputFile.writeText(html)
        return RenderResult(
            path = route.path,
            outputFile = outputFile,
            bytes = outputFile.length(),
        )
    }

    private fun outputFileFor(path: String): File {
        val trimmed = path.trim('/')
        return if (trimmed.isEmpty()) {
            outputDir.resolve("index.html")
        } else {
            outputDir.resolve("$trimmed/index.html")
        }
    }
}

private fun xmlEscape(value: String): String = value
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&apos;")

private fun htmlEscapeAttr(value: String): String = value
    .replace("&", "&amp;")
    .replace("\"", "&quot;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
