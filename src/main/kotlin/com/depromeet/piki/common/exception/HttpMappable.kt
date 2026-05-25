package com.depromeet.piki.common.exception

import org.springframework.http.HttpStatus

interface HttpMappable {
    val httpStatus: HttpStatus
    val category: ErrorCategory
}
