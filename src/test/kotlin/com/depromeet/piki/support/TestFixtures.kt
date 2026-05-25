package com.depromeet.piki.support

import java.nio.ByteBuffer
import java.util.UUID

fun uuidToBytes(uuid: UUID): ByteArray =
    ByteBuffer
        .allocate(16)
        .apply {
            putLong(uuid.mostSignificantBits)
            putLong(uuid.leastSignificantBits)
        }.array()
