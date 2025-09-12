package com.lightningkite.lightningserver.admin

import com.lightningkite.services.database.HasId

typealias UnknownModel = HasId<UnknownId>
typealias UnknownId = Comparable<Comparable<*>>