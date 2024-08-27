package com.lightningkite.lightningserver.schema

import com.lightningkite.kiteui.TypedWebSocket
import com.lightningkite.lightningdb.*
import com.lightningkite.lightningserver.db.ClientModelRestEndpoints
import com.lightningkite.lightningserver.db.ClientModelRestEndpointsStandardImpl
import com.lightningkite.serialization.SerializationRegistry

fun SerializationRegistry.register(schema: LightningServerKSchema) {
    schema.structures.values.forEach { register(it) }
    schema.enums.values.forEach { register(it) }
}

fun LightningServerKSchema.uploadEarlyEndpoint() = endpoints.find {
    it.output.serialName == "com.lightningkite.lightningserver.files.UploadInformation"
}
fun LightningServerKSchema.bulkEndpoint() = endpoints.find {
    it.path.contains("bulk") &&
    it.input.serialName == "com.lightningkite.lightningserver.typed.BulkRequest" &&
    it.output.serialName == "com.lightningkite.lightningserver.typed.BulkResponse"
}
//fun LightningServerKSchema.clientModelRestEndpoints(): Map<String, ClientModelRestEndpoints<*, *>> {
//    val bulk = bulkEndpoint()
//    this.models.mapValues { entry ->
//        val matching = this.structures[entry.value.type.serialName]!!
//        val hasWs = endpoints.find { it.path == entry.value.url && it.method == "WEBSOCKET" && it.input.serialName == "com.lightningkite.lightningdb.Query" && it.output.serialName == "com.lightningkite.lightningdb.ListChange" }
//        val hasUpdatesWs = endpoints.find { it.path == entry.value.url && it.method == "WEBSOCKET" && it.input.serialName == "com.lightningkite.lightningdb.Condition" && it.output.serialName == "com.lightningkite.lightningdb.CollectionUpdates" }
//        when {
//            else -> ClientModelRestEndpointsStandardImpl(
//                fetchImplementation = ,
//                wsImplementation = ,
//                serializer = matching,
//                idSerializer = ,
//            )
//        }
//    }
//}
private interface ignoreClientModelRestEndpointsPlusWs<T : HasId<ID>, ID : Comparable<ID>> : ClientModelRestEndpoints<T, ID> {
    fun watch(): TypedWebSocket<Query<T>, ListChange<T>>
}

private interface ignoreClientModelRestEndpointsPlusUpdatesWebsocket<T : HasId<ID>, ID : Comparable<ID>> : ClientModelRestEndpoints<T, ID> {
    fun updates(): TypedWebSocket<Condition<T>, CollectionUpdates<T, ID>>
}