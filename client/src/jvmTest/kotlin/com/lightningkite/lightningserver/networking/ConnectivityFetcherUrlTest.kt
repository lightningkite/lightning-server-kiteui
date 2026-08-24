package com.lightningkite.lightningserver.networking

import com.lightningkite.kiteui.suppressConnectivityIssues
import com.lightningkite.lightningserver.HttpMethod
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.builtins.serializer
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

/**
 * Regression test for [ConnectivityFetcher] joining its base URL to an endpoint path.
 *
 * Generated SDKs pass endpoint paths *relative* to the server root with no leading slash
 * (`fetcher("users/query", ...)`, and `fetcher("", ...)` for a root endpoint), while the admin
 * panel passes the schema's absolute paths (`/users/query`).  Both bases - `publicUrl` and
 * `wsUrl` - end without a slash, so the fetcher has to supply the separator.  Concatenating
 * directly produced `http://localhost:8080users/query`, which never reaches the server.
 *
 * These run against real local sockets rather than asserting on a composed string, so they
 * check what the server actually receives.
 */
class ConnectivityFetcherUrlTest {

    // The JVM webSocket implementation hops to Dispatchers.Main, which a plain JVM test process
    // does not provide.  Pure test-harness plumbing; see MultiplexedSocketColdStartTest.
    private val mainThread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    @BeforeTest
    fun setMainDispatcher() { Dispatchers.setMain(mainThread) }

    @AfterTest
    fun resetMainDispatcher() { Dispatchers.resetMain(); mainThread.close() }

    /** Serves `123` to any request and records the path it was asked for. */
    private fun withHttpServer(action: (fetcher: ConnectivityFetcher, pathsSeen: List<String>) -> Unit) {
        val paths = ArrayList<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            paths.add(exchange.requestURI.path)
            val body = "123".toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            action(
                ConnectivityFetcher(
                    http = "http://127.0.0.1:${server.address.port}",
                    ws = "ws://127.0.0.1:${server.address.port}",
                ),
                paths,
            )
        } finally {
            server.stop(0)
        }
    }

    /**
     * A mangled URL is indistinguishable from an unreachable server, so the shared
     * `Connectivity.fetchGate` retries it with backoff forever - which is how the original bug
     * presented: the app hung rather than reporting anything.  Suppressing that makes a regression
     * throw here instead of stalling, and keeps one failing test from latching the process-wide gate
     * shut and dragging down every test after it.  The timeout is a backstop for the same reason.
     */
    private fun ConnectivityFetcher.getInt(url: String): Int = runBlocking {
        withTimeout(15.seconds) {
            suppressConnectivityIssues {
                invoke(url, HttpMethod.GET, Unit.serializer(), Unit, Int.serializer())
            }
        }
    }

    @Test
    fun relativePathGetsSeparator(): Unit = withHttpServer { fetcher, paths ->
        assertEquals(123, fetcher.getInt("users/query"))
        assertEquals(listOf("/users/query"), paths)
    }

    @Test
    fun absolutePathIsNotDoubledUp(): Unit = withHttpServer { fetcher, paths ->
        fetcher.getInt("/users/query")
        assertEquals(listOf("/users/query"), paths)
    }

    @Test
    fun rootEndpointHitsRoot(): Unit = withHttpServer { fetcher, paths ->
        fetcher.getInt("")
        assertEquals(listOf("/"), paths)
    }

    /**
     * WebSockets go to the endpoint's own path.  The `?path=` query form some deployments use is
     * applied by infrastructure (the AWS CloudFront function) or spelled out by the caller in its
     * base URL - the fetcher must not bake it in, or a path-routed server never sees the route.
     *
     * Reads the raw HTTP upgrade request line off a plain socket; the handshake is never completed
     * because only the requested path is under test.
     */
    @Test
    fun webSocketConnectsToEndpointPath() {
        val requestLines = ArrayBlockingQueue<String>(1)
        val server = ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))
        val accepter = Thread {
            try {
                server.accept().use { socket ->
                    BufferedReader(InputStreamReader(socket.getInputStream())).readLine()
                        ?.let { requestLines.offer(it) }
                }
            } catch (e: Exception) {
                // The socket is closed out from under us during teardown; nothing to report.
            }
        }
        accepter.isDaemon = true
        accepter.start()

        val fetcher = ConnectivityFetcher(
            http = "http://127.0.0.1:${server.localPort}",
            ws = "ws://127.0.0.1:${server.localPort}",
        )
        val socket = fetcher.webSocket("users", String.serializer(), String.serializer())
        try {
            socket.connect()
            val line = requestLines.poll(10, TimeUnit.SECONDS)
            assertEquals("GET /users HTTP/1.1", line)
        } finally {
            socket.close(1000, "test over")
            server.close()
        }
    }
}
