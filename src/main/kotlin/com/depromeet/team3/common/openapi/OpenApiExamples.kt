package com.depromeet.team3.common.openapi

import com.fasterxml.jackson.databind.ObjectMapper
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.examples.Example
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.MediaType
import org.springframework.http.HttpStatus
import org.springframework.web.method.HandlerMethod
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.javaMethod
import org.springframework.http.MediaType as SpringMediaType

fun HandlerMethod.binds(ref: KFunction<*>): Boolean {
    val target = ref.javaMethod ?: return false
    return method.declaringClass == target.declaringClass &&
        method.name == target.name &&
        method.parameterTypes.contentEquals(target.parameterTypes)
}

fun Operation.examples(objectMapper: ObjectMapper, block: OperationExamples.() -> Unit) {
    OperationExamples(this, objectMapper).block()
}

class OperationExamples(private val operation: Operation, private val objectMapper: ObjectMapper) {
    fun add(status: HttpStatus, name: String, payload: Any) {
        val response = operation.responses?.get(status.value().toString()) ?: return
        val content = response.content ?: Content().also { response.content = it }
        val mediaType = content[SpringMediaType.APPLICATION_JSON_VALUE]
            ?: MediaType().also { content[SpringMediaType.APPLICATION_JSON_VALUE] = it }
        val example = Example().value(objectMapper.convertValue(payload, Any::class.java))
        mediaType.examples = (mediaType.examples ?: linkedMapOf()).apply { put(name, example) }
    }
}
