package db.migration

import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context

// notification_templates 시드(#252) — Flyway Java 마이그레이션. 템플릿 본문의 리터럴 dollar-brace(${actorName} 등)를
// SQL 마이그레이션에 두면 Flyway 가 placeholder 로 오인해 파싱이 깨지므로, JDBC 로 직접 적재한다. CREATE
// (V20260615015147) 직후·JPA/provider 부팅 전에 실행되어 전 타입 시드가 보장된다.
@Suppress("ClassName")
class V20260615015148__seed_notification_templates : BaseJavaMigration() {
    override fun migrate(context: Context) {
        val rows =
            listOf(
                Triple("TOURNAMENT_JOINED", "\${actorName}님이 참가했어요", ""),
                Triple("TOURNAMENT_ITEM_ADDED", "\${actorName}님이 아이템을 추가했어요", ""),
                Triple("TOURNAMENT_STARTED", "\${actorName}님이 토너먼트를 시작했어요", ""),
                Triple("TOURNAMENT_PLAYED_FROM_LINK", "\${actorName}님이 회원님 토너먼트를 플레이했어요", ""),
                Triple("TOURNAMENT_COMPLETED", "\${actorName}님이 회원님 토너먼트를 완료했어요", ""),
                Triple("TOURNAMENT_RESULT_READY", "참여하신 \${actorName}님의 토너먼트 결과가 나왔어요", ""),
                Triple("ITEM_PARSING_COMPLETED", "상품 정보가 저장됐어요", ""),
                Triple("ITEM_PARSING_FAILED", "상품 정보를 가져오지 못했어요", ""),
                Triple("ANNOUNCEMENT", "\${title}", "\${body}"),
            )
        context.connection
            .prepareStatement(
                "INSERT INTO notification_templates (type, title_template, body_template, updated_at) VALUES (?, ?, ?, NOW(6))",
            ).use { statement ->
                rows.forEach { (type, title, body) ->
                    statement.setString(1, type)
                    statement.setString(2, title)
                    statement.setString(3, body)
                    statement.addBatch()
                }
                statement.executeBatch()
            }
    }
}
