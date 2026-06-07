package com.depromeet.piki.common.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.core.JsonGenerator
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.ValueSerializer
import tools.jackson.databind.module.SimpleModule
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Configuration
class JacksonConfig {
    @Bean
    fun localDateTimeModule(): SimpleModule =
        SimpleModule().apply {
            // LocalDateTime 은 타임존 정보가 없는 타입이라 그대로 직렬화하면 Z(UTC) suffix 가 붙지 않는다.
            // 서버는 serverTimezone=UTC 기준으로 모든 시각을 UTC 로 다루므로,
            // OffsetDateTime(UTC) 로 변환 후 ISO-8601 with offset 형태로 직렬화한다.
            addSerializer(LocalDateTime::class.java, LocalDateTimeUtcSerializer)
        }

    private object LocalDateTimeUtcSerializer : ValueSerializer<LocalDateTime>() {
        private val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

        override fun serialize(value: LocalDateTime, gen: JsonGenerator, ctxt: SerializationContext) {
            gen.writeString(value.atOffset(ZoneOffset.UTC).format(formatter))
        }
    }
}
