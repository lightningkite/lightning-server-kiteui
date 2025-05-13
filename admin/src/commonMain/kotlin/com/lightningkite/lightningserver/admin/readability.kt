package com.lightningkite.lightningserver.admin

import com.lightningkite.lightningdb.HasId

typealias UnknownModel = HasId<UnknownId>
typealias UnknownId = Comparable<Comparable<*>>