# ModelCache and live models

`ModelCache<T, ID>` is the client-side copy of one server collection.  Ask it for an item or a query and you get a
`Reactive` that loads, stays current, and is shared with every other screen reading overlapping data.  Writes go
through it too, so the screens update without waiting for a refetch.

`CachedApi` already holds one per model ([api-client.md](api-client.md)), so you rarely construct one.

## Minimal example

```kotlin
@Routable("/todos")
class TodoListPage : Page {
    override fun ElementWriter.CanAddTheme.render() {
        val todos = remember {
            currentSessionNotNull().todos.list(
                Query(orderBy = sort { it.createdAt.descending() }) { it.done eq false }
            )
        }
        col {
            h2("To do")
            expanding.recyclerView {
                children(items = todos) { todo ->      // pages automatically as you scroll
                    card.text { ::content { todo().title } }
                }
            }
        }
    }
}
```

`todos` is `Reactive<ModelCacheLimitReadable<Todo>>`: the outer layer follows the session, the inner one is the
live list.  The `children` overload for that double-wrapped shape comes from
`com.lightningkite.lightningserver.db.children`, and raises the list's limit as the user nears the end.

## Reading

```kotlin
val todo: ModelCacheItemReadable<Todo> = session.todos.item(id)          // Reactive<Todo?>
val open: ModelCacheLimitReadable<Todo> = session.todos.list(Query(limit = 50) { it.done eq false })
```

Both take two freshness knobs, and they answer different questions:

| Parameter | Default | Meaning |
|---|---|---|
| `maximumAge` | `Duration.INFINITE` | How old cached data may be to show it immediately when a screen opens.  Asked once, on arrival. |
| `pullFrequency` | `0.seconds` (never) | How often to refetch while someone is observing. |

When the server mounted `ModelRestUpdatesWebSocket` for the model and `pullFrequency` is under 30 seconds, the cache
subscribes to the update socket instead of polling, and covered data stays fresh with no requests at all.  While
the socket is down it polls every 5 seconds, the floor for all polling, until the subscription is live again.

```kotlin
// Opening a dashboard: show nothing older than a minute, then keep it current.
session.orders.list(Query(), maximumAge = 1.minutes, pullFrequency = 10.seconds)
```

An item reads as `null` when it doesn't exist (or was deleted); `lastUpdatedAt` says when the data was retrieved.

### Pagination

`list()` fetches `query.limit` rows (100 by default).  Ask for more with `limit(n)`, which suspends until they
arrive and fetches only the missing rows with a cursor after the last one known:

```kotlin
val more = Action("Load more") { open.limit(open.limit + 50) }
```

## Writing

| Call | Does |
|---|---|
| `cache.add(item)` / `addAll(items)` | Insert; the server's copy (with generated fields) enters the cache. |
| `cache.upsert(item)` | Insert or replace by `_id`. |
| `cache.item(id).modify(Modification { it.done assign true })` | Partial update. |
| `cache.item(id).set(value)` | Sends only the fields that differ; inserts if missing, deletes if `null`. |
| `cache.item(id).delete()` | Delete. |
| `cache.bulkModify(MassModification(condition, modification))` | Server-side bulk update; clears the whole cache. |
| `cache.item(id).invalidate()` / `cache.totallyInvalidate()` | Forget and refetch. |

The cache records the server's response, so every cached list and item the change affects updates at once,
without a refetch.

### Editing a record

Edit a local copy and send the difference.  Binding a form straight to the cached value would fight every
background refresh.

```kotlin
val item = remember { session().users.item(userId) }
val draft = Signal(User(_id = userId, email = "pending@example.com".toEmailAddress()))
var seeded = false
reactive {
    val loaded = item()() ?: return@reactive
    if (!seeded) { seeded = true; draft.value = loaded }
}

val save = Action("Save") {
    val old = item()() ?: throw PlainTextException("User no longer exists", "Not found")
    val diff = modification(old, draft()) ?: return@Action    // com.lightningkite.lightningdb.modification
    item().modify(diff)
}

field("Name") { textInput { content bind draft.lens(User.name) } }   // com.lightningkite.optics.lens
```

`modification(old, new)` returns `null` when nothing changed.  `draft.validated().lens(User.email).asString()`
binds an `EmailAddress` (or `PhoneNumber`) field to a text input, keeping invalid text in the field instead of
losing it.  The demo's [`UserDetailPage.kt`](../demo-apps/src/commonMain/kotlin/com/lightningkite/lskiteuistarter/UserDetailPage.kt)
is this pattern in full; [forms.md](forms.md) generates the fields for you.

## How it decides what to fetch

All items live once, in a `CoverageStore`, alongside records of which queries it holds *completely* and since
when.  A read is answered locally only when such a record backs it; otherwise the cache fetches.  An item lookup is
just a one-row query, so opening a record from a list costs nothing.  The record mostly comes from one inference: a
query that returned fewer rows than its limit has seen everything matching its condition.

Concurrent item lookups are batched into one multi-ID query.  Models that carry a version counter can pass
`versionOf = { it.version }` so conflicting accounts of a row are settled by version rather than by clock.

## Gotchas

- **No `skip`.**  `list()` rejects `skip != 0` and `limit <= 0`; page with `limit(n)`.
- **The sort you send is extended.**  The cache appends `_id` (and whatever else makes the order total) so the
  server and client agree on ties.  Expect it in server logs.
- **`bulkModify` and an overloaded socket clear everything**, because nothing says which rows changed.
- **`get(id)`, `query(q)`, `watch(...)` and the `limit = n` setter are deprecated.**  `get` and `query` silently
  poll every minute; use `item`/`list` and state your `pullFrequency`.
- **Data cached before the socket connected isn't trusted as live**, since changes during the gap were missed; it is
  refetched once the subscription is acknowledged.
- **`localInsert` and `localSignalUpdate` skip the server.**  They are for optimistic UI and tests; the next fetch
  or socket update overwrites them.
- **Read permissions with a `readMask` matter.**  The cache downloads the model's permissions on start so it knows
  when the server silently narrowed a sort; until that arrives it reuses less, never wrongly.
