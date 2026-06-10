package com.depromeet.piki.admin.feature

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.Repository

/**
 * 기능 요청 저장소. 삭제는 노출하지 않는다(인박스는 상태 토글로 닫지 물리 삭제하지 않는다).
 *
 * 넓은 JpaRepository 대신 save·조회만 가진 좁은 Repository 로 둔다(AdminAuditLogRepository 와 같은 결) —
 * delete* 가 열리면 인박스 항목이 통째로 사라질 수 있다. 상태 변경은 managed 엔티티의 dirty checking 으로 반영된다.
 */
interface AdminFeatureRequestRepository : Repository<AdminFeatureRequest, Long> {
    fun save(entity: AdminFeatureRequest): AdminFeatureRequest

    fun findById(id: Long): AdminFeatureRequest?

    // 삭제되지 않은 요청을 createdAt 내림차순으로 limit 개. 동률(같은 createdAt)에서 결과가 요청마다
    // 달라지지 않도록 id 를 보조 정렬 키로 둔다(ItemJpaRepository.findRecent 와 같은 규약). limit 은 Pageable 로 주입.
    @Query("select r from AdminFeatureRequest r where r.deletedAt is null order by r.createdAt desc, r.id desc")
    fun findRecent(pageable: Pageable): List<AdminFeatureRequest>
}
