# The generated API client

The server writes the app's client.  Lightning Server's `FetcherSdk` generates a typed interface and its HTTP
implementation; this library's `CachingSdk` adds one [`ModelCache`](model-cache.md) per model.  You never write
request code by hand.

## Generating it

Give the server CLI an `sdk` command:

```kotlin
import com.lightningkite.lightningserver.typed.sdk.CachingSdk
import com.lightningkite.lightningserver.typed.sdk.FetcherSdk
import com.lightningkite.lightningserver.typed.sdk.SDK.writeUsingDefaultSettings
import com.lightningkite.services.kfile.KFile

fun sdk() {
    val out = KFile("apps/src/commonMain/kotlin/com/example/sdk")
    FetcherSdk("com.example.sdk").writeUsingDefaultSettings(Server, out)   // Api.kt, LiveApi.kt
    CachingSdk("com.example.sdk").writeUsingDefaultSettings(Server, out)   // CachedApi.kt
}
```

`writeUsingDefaultSettings` builds the server with default settings, so generation needs no `settings.json`, no
database, and no network.  (The demo's [`Main.kt`](../demo-server/src/main/kotlin/com/lightningkite/lskiteuistarter/Main.kt)
instead calls `write(KFile)` inside its loaded engine; either works.)  Run it with `./gradlew :server:generateSdk`
after every server change, and commit the output.

## What you get

For a server with `val todos = path.path("todos") module TodoEndpoints`:

```kotlin
interface Api {                                    // Api.kt
    fun withMetadata(block: suspend RequestMetadata.() -> Unit): Api
    val todo: ClientModelRestEndpoints<Todo, Uuid>
    // one suspend fun per custom endpoint, nested interfaces per `module`
}
class LiveApi(val fetcher: Fetcher) : Api          // LiveApi.kt: real HTTP calls

open class CachedApi(val uncached: Api) {          // CachedApi.kt
    open val todos = ModelCache(uncached.todo, Todo.serializer())
}
```

Each cache is named after the model's serial name, pluralized and camel-cased (`Todo` becomes `todos`,
`User.NestedTypeModel` becomes `userNestedTypeModels`).  Pass `namingScheme = CachingSdk.NamingScheme.SdkPath()` to
name them after the SDK path instead.  The properties are `open` so a subclass can replace one, e.g. to add a
`versionOf` or a log tag (see the demo's [`UserSession.kt`](../demo-apps/src/commonMain/kotlin/com/lightningkite/lskiteuistarter/sdk/UserSession.kt)).

## Connecting

A `Fetcher` turns calls into requests.  Pick one per server:

```kotlin
// Batches calls made in the same 100 ms into one POST to /meta/bulk, and multiplexes every
// live-update socket over one connection.  Use this unless the server lacks those endpoints.
val api = LiveApi(BulkFetcher(
    httpBulk = "https://api.example.com/meta/bulk",
    wsMultiplex = "wss://api.example.com?path=/multiplex",
))

// One request per call, one socket per subscription.
val plainApi = LiveApi(ConnectivityFetcher(http = "https://api.example.com", ws = "wss://api.example.com"))
```

On the web, a relative base (`"/api/meta/bulk"`, `"/api?path=/multiplex"`) plus a dev-server proxy keeps the
app and API on one origin.  The demo keeps its choices in an enum, persisted so a tester can switch servers:
[`apiOptions.kt`](../demo-apps/src/commonMain/kotlin/com/lightningkite/lskiteuistarter/sdk/apiOptions.kt).

## Sessions

Login ([auth.md](auth.md)) produces a long-lived **session token**.  Exchange it for short-lived access tokens
and attach them to every request with `withMetadata`; everything built from the resulting `Api` is signed in.

```kotlin
class UserSession(val api: Api, val userId: Uuid) : CachedApi(api)

val sessionToken = PersistentProperty<String?>("sessionToken", null)

val currentSession: Reactive<UserSession?> = rememberSuspending {
    val token = sessionToken() ?: return@rememberSuspending null
    val accessToken = api.userAuth.accessToken(token)          // fetched, then reused for 4 minutes
    val authApi = api.withMetadata { set("Authorization", accessToken()) }
    UserSession(authApi, authApi.userAuth.getSelf()._id)
}
```

Because `currentSession` is reactive, signing in or out (`sessionToken.value = ...`) rebuilds the session and with
it every cache, and every screen reading `currentSession()` follows.  Signing out:

```kotlin
onClick {
    try { currentSession()?.api?.userAuth?.terminateSession() }
    finally { sessionToken.value = null }
}
```

## Calling endpoints

Model collections are usually read through their cache ([model-cache.md](model-cache.md)).  Everything else is a
suspend function; call it from an `Action` so KiteUI shows progress and errors:

```kotlin
val ping = Action("Ping") {
    val answer = currentSessionNotNull().api.exampleEndpoint(41)
    context.toast("Server says $answer")
}
button { text("Ping"); action = ping }
```

A failed call throws `LsErrorException`, whose `error` is the server's `LSError`.  Install the handler once at the
app root so these show as readable dialogs instead of generic failures:

```kotlin
fun ViewWriter.app(navigator: PageNavigator) {
    context.exceptionHandlers.installLsError()
    // ...
}
```

## Files

Upload with the server's `UploadEarlyEndpoint`, then store the returned `ServerFile` in a model.  Show one with
`asImage()`; a file uploaded from this device displays from the local copy without a round trip.

```kotlin
val file: ServerFile? = fileReference.toServerFile(session.api.uploadEarlyEndpoint)
image { ::source { document().attachment?.asImage() } }
```

## Gotchas

- **Never hold a `CachedApi` across sessions.**  Its caches were built on one user's credentials; build a new one
  per session as above.
- **`withHeaderCalculator` is deprecated.**  Generated code still offers it; use `withMetadata`.
- **`accessToken()` returns the bare token**, not a header list (changed in the current release; see the
  CHANGELOG).
- **Endpoint names come from the server.**  Two endpoints with the same path and different methods become
  overloads (`exampleEndpoint()` and `exampleEndpoint(input)`); rename on the server if that reads badly.
