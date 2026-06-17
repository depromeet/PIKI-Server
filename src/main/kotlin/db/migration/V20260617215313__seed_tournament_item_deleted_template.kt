package db.migration

import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context

// TOURNAMENT_ITEM_DELETED 템플릿 시드 — 토너먼트 아이템 삭제 알림(추가 알림 TOURNAMENT_ITEM_ADDED 의 거울).
// 본문의 리터럴 dollar-brace(${actorName})를 SQL 마이그레이션에 두면 Flyway 가 placeholder 로 오인해 파싱이
// 깨지므로, 시드(V20260615015148)와 동일하게 JDBC 로 직접 적재한다. 전 타입 템플릿이 있어야 부팅이 통과한다
// (DbNotificationTemplateProvider.load 의 누락 검증).
@Suppress("ClassName")
class V20260617215313__seed_tournament_item_deleted_template : BaseJavaMigration() {
    override fun migrate(context: Context) {
        context.connection
            .prepareStatement(
                "INSERT INTO notification_templates (type, title_template, body_template, updated_at) VALUES (?, ?, ?, NOW(6))",
            ).use { statement ->
                statement.setString(1, "TOURNAMENT_ITEM_DELETED")
                statement.setString(2, "\${actorName}님이 '\${itemName}'을(를) 삭제했어요")
                statement.setString(3, "")
                statement.executeUpdate()
            }
    }
}
