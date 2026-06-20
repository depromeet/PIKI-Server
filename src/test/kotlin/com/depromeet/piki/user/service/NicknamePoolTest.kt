package com.depromeet.piki.user.service

import com.depromeet.piki.user.domain.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// 게스트 닉네임 풀(형용사 × 동물) 정합성 검증 — 크기·글자수·중복. Spring·DB 없이 순수 데이터만 보므로 단위 테스트다.
// NICKNAME_POOL 은 같은 모듈 테스트가 검증하도록 internal 로 노출돼 있다(프로덕션 외부 노출 아님).
class NicknamePoolTest {
    @Test
    fun `풀은 형용사 64 × 동물 64 = 4096 조합이고 중복이 없다`() {
        val pool = UserService.NICKNAME_POOL
        assertEquals(4096, pool.size, "형용사 64 × 동물 64 = 4096 이어야 한다")
        assertEquals(pool.size, pool.toSet().size, "중복 조합이 없어야 한다 (형용사·동물 각 리스트 내 중복도 곱해져 드러난다)")
    }

    @Test
    fun `모든 조합이 닉네임 최대 길이 이하다`() {
        UserService.NICKNAME_POOL.forEach { nickname ->
            assertTrue(
                nickname.length <= User.NICKNAME_MAX_LENGTH,
                "'$nickname'(${nickname.length}자)이 ${User.NICKNAME_MAX_LENGTH}자 제한을 넘는다",
            )
        }
    }

    @Test
    fun `형용사는 5자 이하, 동물은 3자 이하다`() {
        // 풀 항목은 "형용사 동물" 형식 — 공백 하나로 가른다(형용사·동물 모두 공백을 포함하지 않는다).
        UserService.NICKNAME_POOL.forEach { nickname ->
            val parts = nickname.split(" ")
            assertEquals(2, parts.size, "'$nickname' 은 '형용사 동물' 형식이어야 한다")
            val (prefix, animal) = parts
            assertTrue(prefix.length <= 5, "형용사 '$prefix'(${prefix.length}자)이 5자를 넘는다")
            assertTrue(animal.length <= 3, "동물 '$animal'(${animal.length}자)이 3자를 넘는다")
        }
    }
}
