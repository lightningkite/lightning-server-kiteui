package com.lightningkite.mppexampleapp

import com.lightningkite.kiteui.navigation.Screen
import com.lightningkite.kiteui.views.*
import com.lightningkite.kiteui.views.direct.*
import com.lightningkite.lightningdb.HasId
import com.lightningkite.lightningserver.db.ModelCache


abstract class CollectionAdminScreen<T: HasId<ID>, ID: Comparable<ID>>(val cache: ModelCache<T, ID>): Screen {
    override fun ViewWriter.render() {
        col {
            recyclerView {

            }
        }
    }
}