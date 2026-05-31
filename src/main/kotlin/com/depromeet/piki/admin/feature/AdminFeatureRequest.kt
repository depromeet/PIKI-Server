package com.depromeet.piki.admin.feature

import com.depromeet.piki.common.domain.LongBaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table

/**
 * admin 백오피스 기능 요청 한 건. 팀원이 한 줄로 남기는 인박스 항목.
 *
 * 생성 검증(blank 금지·길이)은 [create] 팩토리에 모으고 주 생성자를 private 로 막는다 —
 * kotlin("plugin.jpa") 의 no-arg 생성자가 invokeInitializers 로 init 블록을 실제 실행하는데,
 * Hibernate 하이드레이션 시점엔 title 이 아직 안 채워져 init 의 require 가 깨지기 때문이다.
 * 그래서 불변식을 init 이 아니라 팩토리에 둔다(Item 이 상태-의존 불변식을 from/markReady 에 둔 것과 같은 결).
 */
@Entity
@Table(name = "admin_feature_requests")
class AdminFeatureRequest private constructor(
    title: String,
    createdBy: String,
) : LongBaseEntity() {
    @Column(name = "title", nullable = false, length = TITLE_MAX_LENGTH)
    var title: String = title
        protected set

    @Column(name = "created_by", nullable = false, length = 100)
    val createdBy: String = createdBy

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    var status: AdminFeatureRequestStatus = AdminFeatureRequestStatus.NEW
        protected set

    // 검토 완료 표시를 오간다. NEW↔DONE 토글 — 잘못 눌러도 한 번 더 누르면 원복된다.
    // 도메인이 자기 상태 전이를 책임진다(서비스가 if 로 분기하지 않는다).
    fun toggleStatus() {
        status =
            when (status) {
                AdminFeatureRequestStatus.NEW -> AdminFeatureRequestStatus.DONE
                AdminFeatureRequestStatus.DONE -> AdminFeatureRequestStatus.NEW
            }
    }

    companion object {
        const val TITLE_MAX_LENGTH = 200

        // 유일한 생성 경로. 불변식(blank 금지·길이·작성자)을 여기서 검사한다.
        // 검증 실패는 입력 경계(컨트롤러 폼·서비스)가 먼저 거르므로 정상 흐름에선 닿지 않는다 —
        // 닿으면 폼을 우회한 호출이고, admin 도구라 require 로 막고 컨트롤러가 IllegalArgumentException 을 잡아 flash 로 보여준다.
        fun create(
            title: String,
            createdBy: String,
        ): AdminFeatureRequest {
            val trimmed = title.trim()
            require(trimmed.isNotBlank()) { "기능 요청 제목은 비어 있을 수 없습니다." }
            require(trimmed.length <= TITLE_MAX_LENGTH) { "기능 요청 제목은 ${TITLE_MAX_LENGTH}자를 초과할 수 없습니다." }
            require(createdBy.isNotBlank()) { "작성자(admin) 가 비어 있습니다." }
            return AdminFeatureRequest(trimmed, createdBy)
        }
    }
}
