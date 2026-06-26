package com.depromeet.piki.item.service

import org.slf4j.LoggerFactory
import org.springframework.dao.TransientDataAccessException
import org.springframework.stereotype.Component
import java.sql.SQLTransientException

/**
 * 파싱 결과의 상태 전이(markReady/markFailed) write 를 일시 DB 오류에 한해 인라인 재시도한다.
 *
 * 추출(fetch + Gemini)은 이미 성공한 뒤라, 전이 write 가 일시 DB 오류(데드락·lock timeout·일시 커넥션)로 실패했다고
 * 추출까지 버리면 낭비다. PROCESSING 으로 두고 recover 가 잡으면 추출 전체를 재실행하므로(외부 의존성 재호출), 그 대신
 * 추출 재실행 없이 전이 write 만 짧게 N회 다시 시도한다. 상한까지 실패하면 예외를 그대로 전파해 호출부가 FAILED 로 종결한다.
 *
 * 재시도 대상은 "일시"뿐 — 영구 오류(도메인 검증 위반·sweeper 레이스로 이미 전이됨 등)는 재시도해도 같으니 즉시 전파한다.
 * [com.depromeet.piki.product.service.gemini.GeminiRetry] 와 같은 결(분류를 한 곳에 모음)이되, DB 예외는 category 가 없어
 * Spring DataAccessException 타입으로 가른다. dirty-checking UPDATE 는 일시 장애가 commit(flush) 시점에 터져
 * `TransactionSystemException` 으로 감싸지기도 하므로, cause 사슬에서 [TransientDataAccessException](레포 호출 시점에
 * Spring 이 번역한 것) 또는 [SQLTransientException](commit 시점 raw — MySQL deadlock 40001 · lock wait timeout 1205 의
 * `SQLTransactionRollbackException` 포함) 을 찾는다.
 *
 * block 은 `@Transactional` 전이 메서드를 프록시로 호출해 매 시도가 새 트랜잭션이 되게 해야 한다(self-invocation 금지).
 */
@Component
class TransitionRetry {
    private val log = LoggerFactory.getLogger(javaClass)

    fun <T> execute(block: () -> T): T {
        var attempt = 1
        while (true) {
            try {
                return block()
            } catch (e: Exception) {
                if (!isTransient(e) || attempt >= MAX_ATTEMPTS) throw e
                log.warn("상태 전이 일시 실패 {}/{} — {}ms 후 재시도: {}", attempt, MAX_ATTEMPTS - 1, BACKOFF_MS, e.message)
                Thread.sleep(BACKOFF_MS)
                attempt++
            }
        }
    }

    // cause 사슬에 일시 DB 예외가 있으면 일시로 본다(직접 던져졌든 TransactionSystemException 등에 감싸졌든).
    // generateSequence 가 cause 가 없을 때 사슬을 끝내고, take 로 병리적 순환 cause 에도 멈춘다.
    private fun isTransient(e: Throwable): Boolean =
        generateSequence(e) { it.cause }
            .take(MAX_CAUSE_DEPTH)
            .any { it is TransientDataAccessException || it is SQLTransientException }

    companion object {
        // 짧은 인라인 재시도 — 단건 ≤60s·절대 3분 초과 금지 제약 안 (3회 × 50ms = 최악 약 100ms 지연).
        private const val MAX_ATTEMPTS = 3
        private const val BACKOFF_MS = 50L
        private const val MAX_CAUSE_DEPTH = 10
    }
}
