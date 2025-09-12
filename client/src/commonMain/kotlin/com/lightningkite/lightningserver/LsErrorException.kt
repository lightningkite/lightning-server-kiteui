package com.lightningkite.lightningserver

class LsErrorException(val error: LSError) : IllegalStateException("${error.http}: ${error.message}") {
    val status get() = error.http
}
