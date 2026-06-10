package com.depromeet.piki.support

import org.springframework.test.util.ReflectionTestUtils
import java.nio.ByteBuffer
import java.util.UUID

fun uuidToBytes(uuid: UUID): ByteArray =
    ByteBuffer
        .allocate(16)
        .apply {
            putLong(uuid.mostSignificantBits)
            putLong(uuid.leastSignificantBits)
        }.array()

// 미영속 엔티티에 id 를 부여한다 — getId() 를 호출하는 순수 매핑(SsePayload.from·buildDataPayload 등)을
// 영속 경로(Spring·DB) 없이 단위로 검증하기 위함. LongBaseEntity 의 private id 필드를 직접 채운다.
fun <T : Any> T.withId(id: Long): T = apply { ReflectionTestUtils.setField(this, "id", id) }
