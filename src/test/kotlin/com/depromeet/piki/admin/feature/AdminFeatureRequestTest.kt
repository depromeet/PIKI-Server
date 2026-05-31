package com.depromeet.piki.admin.feature

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AdminFeatureRequestTest {
    @Test
    fun `제목과 작성자로 생성하면 NEW 상태로 시작한다`() {
        val request = AdminFeatureRequest.create("item 목록 추출 실패 필터", "admin")

        assertEquals(AdminFeatureRequestStatus.NEW, request.status)
        assertEquals("item 목록 추출 실패 필터", request.title)
        assertEquals("admin", request.createdBy)
    }

    @Test
    fun `제목 앞뒤 공백은 제거되어 저장된다`() {
        val request = AdminFeatureRequest.create("   여백 있는 제목   ", "admin")

        assertEquals("여백 있는 제목", request.title)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "   ", "\t", "\n"])
    fun `빈 제목이나 공백만 있는 제목으로 생성하면 예외가 발생한다`(blank: String) {
        assertFailsWith<IllegalArgumentException> { AdminFeatureRequest.create(blank, "admin") }
    }

    @Test
    fun `200자 제목은 허용되고 201자는 예외가 발생한다`() {
        val maxTitle = "가".repeat(AdminFeatureRequest.TITLE_MAX_LENGTH)

        assertEquals(AdminFeatureRequest.TITLE_MAX_LENGTH, AdminFeatureRequest.create(maxTitle, "admin").title.length)
        assertFailsWith<IllegalArgumentException> {
            AdminFeatureRequest.create("가".repeat(AdminFeatureRequest.TITLE_MAX_LENGTH + 1), "admin")
        }
    }

    @Test
    fun `작성자가 비어 있으면 예외가 발생한다`() {
        assertFailsWith<IllegalArgumentException> { AdminFeatureRequest.create("제목", "") }
    }

    @Test
    fun `toggleStatus 는 NEW 와 DONE 을 오간다`() {
        val request = AdminFeatureRequest.create("제목", "admin")

        request.toggleStatus()
        assertEquals(AdminFeatureRequestStatus.DONE, request.status)

        request.toggleStatus()
        assertEquals(AdminFeatureRequestStatus.NEW, request.status)
    }
}
