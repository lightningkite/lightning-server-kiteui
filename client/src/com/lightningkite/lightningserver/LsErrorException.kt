package com.lightningkite.lightningserver

public class LsErrorException(public val error: LSError) : IllegalStateException("${error.http}: ${error.message}") {
    public val status: Int get() = error.http
}
