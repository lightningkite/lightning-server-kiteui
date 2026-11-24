package com.lightningkite.lightningserver.files

import com.lightningkite.kiteui.createFileReferenceFromBytes
import com.sun.net.httpserver.HttpServer
import com.lightningkite.services.files.ServerFile
import kotlinx.coroutines.runBlocking
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ToServerFileTest {
    @Test fun uploadsThenVerifies() = runBlocking {
        var method: String? = null
        var body: ByteArray? = null
        val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/put") { ex ->
            method = ex.requestMethod
            body = ex.requestBody.readBytes()
            ex.sendResponseHeaders(200, -1)
            ex.close()
        }
        server.start()
        try {
            var verified: UnverifiedUploadToken? = null
            val api = object : ClientFileUploadEndpoints {
                override suspend fun getFileUploadUrl() =
                    UploadTarget("http://localhost:${server.address.port}/put", UnverifiedUploadToken("tok"))
                override suspend fun verifyUpload(upload: UnverifiedUploadToken): ServerFile {
                    verified = upload
                    return ServerFile("files/a.txt")
                }
            }
            val local = createFileReferenceFromBytes("hello".toByteArray(), "text/plain", "a.txt")
            val result = local.toServerFile(api)
            assertEquals("PUT", method)
            assertEquals("hello", body!!.decodeToString())
            assertEquals("tok", verified!!.raw)
            assertEquals(ServerFile("files/a.txt"), result)
            assertSame(local, LocalFileRegistry.fileReference[result])
        } finally {
            server.stop(0)
        }
    }
}
