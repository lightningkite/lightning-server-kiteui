# Testing

Three levels, cheapest first:

1. **Cache-backed logic against a mock server**: `ClientModelRestEndpointsMock` behind a real `ModelCache`.
2. **Server endpoints** with Lightning Server's `Server.test { }` and a RAM database (see its testing guide).
3. **The whole stack in one JVM**: the real server, the real app, no HTTP; see the starter's `integration-tests`.

## Mock endpoints

`ClientModelRestEndpointsMock<T, ID>` is an in-memory `ClientModelRestEndpoints` that enforces `ModelPermissions`
like the server does.  `ClientModelRestEndpointsPlusUpdatesWebSocketMock` adds a working update socket.  Put a
`ModelCache` in front of either and you are testing the code your screens actually run:

```kotlin
@Test fun newItemsAppearInOpenLists() = runVirtualTimeTest {
    val server = ClientModelRestEndpointsPlusUpdatesWebSocketMock<Todo, Uuid>(this)
    val first = Todo(title = "first")
    server.data[first._id] = first
    val cache = ModelCache(server, Todo.serializer(), scope = backgroundScope)

    val list = cache.list(Query(sort { it.title.ascending() }) { Condition.Always })
    var shown = listOf<Todo>()
    reactive { shown = list() }          // a listener is what starts the cache working
    delay(1.seconds)
    assertEquals(listOf(first), shown)

    val second = cache.add(Todo(title = "second"))
    delay(1.seconds)
    assertEquals(listOf(first, second), shown)
}
```

| Mock knob | Simulates |
|---|---|
| `data` | The server's table; edit it directly for out-of-band changes. |
| `connectivityFailure = true` | Server unreachable: requests throw and the socket closes. |
| `socketConnected = false` (socket mock) | Socket down while REST still answers. |
| `modelPermissions` | Server-side read conditions and masks. |
| `delayAmount` | Latency per request (default 100 ms). |

## Virtual time

`ModelCache` reads the time from a `ClockContextElement` in its coroutine context, falling back to the system clock.
Under `runTest`, give it a clock that follows the test scheduler, or freshness and polling decisions use wall time
while `delay` skips ahead:

```kotlin
fun runVirtualTimeTest(body: suspend TestScope.() -> Unit) {
    lateinit var scope: TestScope
    val virtualClock = object : Clock {
        val start = Clock.System.now()
        override fun now(): Instant = start + scope.currentTime.milliseconds
    }
    runTest(ClockContextElement(virtualClock)) { scope = this; body() }
}
```

Needs `kotlinx-coroutines-test`.  Pass `scope = backgroundScope` to the cache so its polling loops end with the test.

## Screens without a server

`MockModelCollection<T, ID>` implements `ModelCacheLike` with plain signals: `populate(item)` adds a row,
and every `item()`/`list()` reads it.  Use it for code typed against `ModelCacheLike`.  The generated `CachedApi`
holds concrete `ModelCache`s, whose properties are `open`, so a test session can override one with a cache over a
mock instead.

## Full stack

[ls-kiteui-starter](https://github.com/lightningkite/ls-kiteui-starter)'s `integration-tests` module runs the real
server (`Server.test` with a RAM database) and the real app pages (KiteUI's `uiTest` on the JVM) together.  Its
`TestRunnerFetcher` is a `Fetcher` that dispatches requests straight into the server engine, so
`LiveApi(TestRunnerFetcher(runner))` is a fully working client with no network.  Copy that module to start.

## This repo's tests

```bash
./gradlew :client:jvmTest                                # ModelCache, CoverageStore, forms, networking
./gradlew :client:jvmTest --tests "*.ModelCacheTest"
./gradlew :admin:jsTest                                  # admin screens (headless browser)
./gradlew allTests                                       # every target
```

## Gotchas

- **Nothing loads until something listens.**  Reading `list.state` without a `reactive { }` (or `await()`) shows
  what's cached and starts no fetch.
- **The socket mock acknowledges subscriptions; a dead socket does not.**  A cache waits up to 5 seconds for a
  subscription before fetching, so a fake fetcher whose socket never connects slows every first read by 5 seconds.
- **Global state outlives tests.**  `PersistentProperty` values and `AppScope` jobs (session token, current session)
  carry over between tests in one process; reset them in setup.
