package com.depromeet.piki.common.exception

abstract class BaseException(
    override val message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
