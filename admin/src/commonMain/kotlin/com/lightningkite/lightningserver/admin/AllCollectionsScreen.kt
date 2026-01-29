package com.lightningkite.lightningserver.admin

//
// IMPROVEMENTS:
// 1. Add empty state UI: When `models.entries.toList()` is empty, show a friendly message
//    instead of rendering an empty recyclerView (e.g., "No collections available").
//
// 2. Error handling: The `adminServer()` call could fail. Consider wrapping in a try-catch
//    to show error UI if server schema fetch fails or is invalid.
//
// 3. Sorting: Collections are displayed in map iteration order which may be unpredictable.
//    Consider sorting by displayName for better UX (e.g., `sortedBy { it.invoke().value.serializer.displayName }`).
//
// 4. Search/Filter: For admin panels with many collections (50+), consider adding a search
//    input to filter the list by name. This improves discoverability.
//
// 5. Collection metadata: Consider showing additional info per collection like item count,
//    last modified date, or permission level. This helps admins quickly identify collections.
//
// 6. Loading state: The `remember` block at line 19 is synchronous, but if `adminServer()`
//    is slow to initialize, there's no loading indicator. Consider using `rememberSuspending`
//    with a loading state.
//
// 7. Accessibility: The links have no visible text context besides the collection name.
//    Consider adding icons or descriptions for better accessibility.

import com.lightningkite.kiteui.Routable
import com.lightningkite.kiteui.forms.displayName
import com.lightningkite.kiteui.navigation.Page

import com.lightningkite.kiteui.views.ViewWriter
import com.lightningkite.kiteui.views.direct.col
import com.lightningkite.kiteui.views.direct.link
import com.lightningkite.kiteui.views.direct.recyclerView
import com.lightningkite.kiteui.views.direct.text
import com.lightningkite.kiteui.views.expanding
import com.lightningkite.kiteui.views.l2.children
import com.lightningkite.reactive.core.remember

/**
 * Landing page that displays all available collections in the admin panel.
 *
 * This page serves as the main navigation hub for accessing different data collections
 * managed by the admin interface. It:
 * - Lists all collections from the server schema
 * - Provides links to each collection's management screen (CollectionAdminPage)
 * - Uses a recyclerView for efficient rendering of large collection lists
 *
 * Each collection is identified by its key in the server's models map and displays
 * its human-readable name derived from the serializer's displayName.
 *
 * Route: `/collections`
 */
@Routable("collections")
class AllCollectionsPage() : Page {
    /**
     * Renders a vertical scrolling list of all available collections.
     *
     * The list is cached via `remember` to avoid re-fetching on every render.
     * Each collection entry is rendered as a clickable link that navigates to
     * the CollectionAdminPage for detailed CRUD operations.
     */
    override fun ViewWriter.render() {
        // by Claude - Fetch collection list once and cache it
        // Note: If adminServer() fails, this will throw. Consider error handling.
        val models = remember { adminServer().models.entries.toList() }
        col {
            expanding.recyclerView {
                // Render each collection as a link using its key as the stable ID
                children(models, id = {it.key}) {
                    link {
                        // Display the human-readable collection name
                        text { ::content { it.invoke().value.serializer.displayName } }
                        // Navigate to the collection's admin page
                        ::to { it().key.let { { CollectionAdminPage(it) } } }
                    }
                }
            }
        }
    }
}