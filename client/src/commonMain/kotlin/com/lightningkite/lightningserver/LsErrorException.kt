package com.lightningkite.lightningserver

class LsErrorException(val status: Short, val error: LSError) : IllegalStateException("$status: ${error.message}")
