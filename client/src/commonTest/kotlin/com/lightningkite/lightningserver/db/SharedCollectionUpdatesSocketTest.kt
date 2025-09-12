package com.lightningkite.lightningserver.db

import kotlin.uuid.Uuid
import com.lightningkite.kiteui.ConsoleRoot
import com.lightningkite.kiteui.Platform
import com.lightningkite.kiteui.current
import com.lightningkite.kiteui.forms.prepareModelsClient
import com.lightningkite.services.database.condition
import com.lightningkite.services.database.gt
import com.lightningkite.services.database.lt
import com.lightningkite.prepareModelsClientTest
import com.lightningkite.prepareModelsShared
import com.lightningkite.reactive.context.onRemove
import kotlinx.coroutines.delay
import kotlin.test.Test
import kotlin.test.assertContains

class SharedCollectionUpdatesSocketTest {
    val testLog = if(Platform.current == Platform.Desktop) ConsoleRoot else null

    init {
        prepareModelsShared()
        prepareModelsClient()
        prepareModelsClientTest()
    }

    @Test
    fun test() = runTest2 {
        val mock = ClientModelRestEndpointsPlusUpdatesWebsocketMock<LargeTestModel, Uuid>(this)
        val remember = SharedCollectionUpdatesSocket(
            scope = this,
            socket = mock.updates(),
            onChange = { println("Got changes $it") },
            log = testLog,
        )
        println("OK")
        run {
            val req = remember.require(condition { it.int gt 4 })
            onRemove(req.beginUse())
            println("Req created")
            delay(1000)
            assertContains(remember.listeningStatus.value.requirements, req)
        }
        run {
            val req = remember.require(condition { it.int gt 9 })
            onRemove(req.beginUse())
            println("Req created")
            delay(1000)
            assertContains(remember.listeningStatus.value.requirements, req)
        }
        run {
            val req = remember.require(condition { it.int lt 2 })
            onRemove(req.beginUse())
            println("Req created")
            delay(1000)
            assertContains(remember.listeningStatus.value.requirements, req)
        }
    }
}