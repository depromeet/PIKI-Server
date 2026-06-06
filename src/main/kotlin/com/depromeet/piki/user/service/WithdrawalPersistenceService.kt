package com.depromeet.piki.user.service

import com.depromeet.piki.notification.fcm.repository.UserDeviceRepository
import com.depromeet.piki.notification.repository.NotificationRepository
import com.depromeet.piki.user.domain.UserException
import com.depromeet.piki.user.repository.UserDetailRepository
import com.depromeet.piki.user.repository.UserRepository
import com.depromeet.piki.wishlist.repository.WishRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

// 탈퇴의 DB 변경(cascade)만 짧은 단일 트랜잭션으로 묶는 빈. Redis/SSE 같은 외부 의존성은 여기서 다루지 않고
// 오케스트레이터(WithdrawalService)가 트랜잭션 밖에서 처리한다. 별도 빈으로 분리해 Spring AOP proxy 를 거치게 함으로써
// self-invocation 으로 @Transactional 이 무력화되는 함정을 피한다.
@Service
class WithdrawalPersistenceService(
    private val userRepository: UserRepository,
    private val userDetailRepository: UserDetailRepository,
    private val userDeviceRepository: UserDeviceRepository,
    private val wishRepository: WishRepository,
    private val notificationRepository: NotificationRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // 회원 탈퇴 DB cascade. MEMBER 전용 — 게스트는 호출부가 사전에 거른다.
    // 멱등: 이미 tombstone(deletedAt 이 채워진 상태) 이면 아무 것도 하지 않고 즉시 반환(double-request 안전).
    //
    // 동시 탈퇴(double-request) 안전성: 아래 deletedAt 가드는 두 요청이 동시에 deletedAt=null 을 읽으면
    // 둘 다 통과할 수 있다(가드 자체는 race 를 완전히 막지 않는다 — fast-path 일 뿐). 그래도 안전한 이유는
    // cascade 의 각 단계가 전부 멱등이기 때문이다: withdraw()/softDelete() 는 deletedAt ?:= now 로 첫 값 유지,
    // user_details 파생 delete 는 0건 no-op, soft-delete 쿼리는 deletedAt IS NULL 가드로 2회차엔 0건.
    // 따라서 2회 실행돼도 같은 종단 상태가 되고 UNIQUE 충돌·예외가 나지 않는다. 완전 직렬화(락)는 불필요.
    @Transactional
    fun withdraw(userId: UUID) {
        val user = userRepository.findById(userId) ?: throw UserException.notFound(userId)
        user.deletedAt?.let {
            log.info("이미 탈퇴 처리된 유저 — cascade 생략(멱등) userId={}", userId)
            return
        }

        // 1. users 익명 tombstone 전이(softDelete + 닉네임/프로필 비식별화). 게스트면 도메인 check 위반 → 500.
        //    (정상 흐름은 호출부가 게스트를 403 으로 막아 여기 닿지 않는다.)
        user.withdraw()
        userRepository.save(user)

        // 2. user_details 하드삭제 — socialId 즉시 파기(PIPA "지체없이 파기"), UNIQUE 풀려 재가입 가능.
        userDetailRepository.deleteByUserId(userId)

        // 3. user_devices 하드삭제 — 기기 토큰 제거.
        userDeviceRepository.deleteAllByUserId(userId)

        // 4. wishes soft-delete — 개인 데이터 가리기(30일 후 스케줄러가 하드삭제).
        val now = LocalDateTime.now()
        val wishes = wishRepository.softDeleteAllByUserId(userId, now)

        // 5. notifications soft-delete — 수신자 알림 가리기(30일 후 하드삭제).
        val notifications = notificationRepository.softDeleteAllByUserId(userId, now)

        log.info("회원 탈퇴 cascade 완료 userId={} wishes={} notifications={}", userId, wishes, notifications)
    }

    // 30일 파기 — soft-delete 된 콘텐츠(wishes, notifications)를 영구 하드삭제. tombstone users 행 자체는 보존한다
    // (익명·PII 없음, 공유 토너먼트 참조 유지 — 삭제하면 tournament_items.userId 등이 dangling 된다).
    // 한 유저 단위로 묶어 짧은 트랜잭션 안에서 끝낸다(스케줄러가 chunk 를 돌며 유저별로 호출). 멱등(없으면 0건).
    // 하드삭제 직후 같은 트랜잭션에서 content_purged_at 을 찍어, 다음 스캔에서 이 유저가 제외되게 한다(매일 영구 재스캔 방지).
    @Transactional
    fun purgeContent(userId: UUID) {
        val wishes = wishRepository.hardDeleteAllByUserId(userId)
        val notifications = notificationRepository.hardDeleteAllByUserId(userId)
        val user = userRepository.findById(userId) ?: throw UserException.notFound(userId)
        user.markContentPurged()
        userRepository.save(user)
        log.info("탈퇴 콘텐츠 파기 완료 userId={} wishes={} notifications={}", userId, wishes, notifications)
    }
}
