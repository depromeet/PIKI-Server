package com.depromeet.piki.notification.fcm.service

import com.depromeet.piki.notification.domain.Notification
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.MessagingErrorCode
import com.google.firebase.messaging.MulticastMessage
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.stereotype.Component
import com.google.firebase.messaging.Notification as FcmNotification

// FcmMessageSender 의 운영 구현 — FirebaseMessaging 멀티캐스트 발송(#245).
// FirebaseApp 빈이 있을 때만(@ConditionalOnBean) 뜬다 — 키 없는 로컬/테스트에선 FirebaseConfig 가
// FirebaseApp 을 안 만들어 이 빈도 안 뜨고, PushNotificationChannel 이 발송을 no-op 으로 건너뛴다.
@Component
@ConditionalOnBean(FirebaseApp::class)
class FirebaseMessageSender(
    firebaseApp: FirebaseApp,
) : FcmMessageSender {
    private val log = LoggerFactory.getLogger(javaClass)
    private val messaging = FirebaseMessaging.getInstance(firebaseApp)

    // tokens 를 멀티캐스트로 발송하고, FCM 이 무효라고 답한(앱 삭제·토큰 폐기) 죽은 토큰을 모아 반환한다.
    // 한 청크 발송이 통째로 실패해도(네트워크 등) 나머지 청크는 계속 시도한다 — 부분 실패를 전체 실패로 키우지 않는다.
    override fun send(
        tokens: List<String>,
        notification: Notification,
    ): List<String> {
        val stale = mutableListOf<String>()
        tokens.chunked(MULTICAST_LIMIT).forEach { chunk ->
            val response =
                runCatching { messaging.sendEachForMulticast(buildMessage(chunk, notification)) }
                    .getOrElse { e ->
                        // 토큰은 민감 정보라 로그에 남기지 않는다 — 크기만.
                        log.warn("FCM 멀티캐스트 발송 실패 chunkSize={}", chunk.size, e)
                        return@forEach
                    }
            response.responses.forEachIndexed { i, result ->
                if (result.isSuccessful) return@forEachIndexed
                if (isStaleToken(result.exception?.messagingErrorCode)) stale += chunk[i]
            }
        }
        return stale
    }

    // 표시용 title/body + 클라 라우팅용 data(type·refId)를 실은 멀티캐스트 메시지.
    // 백그라운드 수신 시 클라가 data 로 딥링크(type+refId)를 복원한다.
    private fun buildMessage(
        tokens: List<String>,
        notification: Notification,
    ): MulticastMessage =
        MulticastMessage
            .builder()
            .addAllTokens(tokens)
            .setNotification(
                FcmNotification
                    .builder()
                    .setTitle(notification.title)
                    .setBody(notification.body)
                    .build(),
            ).putData(DATA_KEY_TYPE, notification.type.name)
            .putData(DATA_KEY_REF_ID, notification.refId.toString())
            .applyPlatformConfig()
            .build()

    // 플랫폼별 푸시 옵션(iOS APNS / Android / WebPush)을 싣는 확장 지점 — FCM 한 발송이 세 플랫폼을
    // 다 라우팅하므로 sender 를 쪼개지 않고 여기 한 곳에서만 분기한다.
    //
    // 지금은 iOS 만이고 공통 notification(title/body)만으로 표시가 충분해 분기 없이 그대로 둔다.
    // Android/Web 합류 시 손댈 곳은 딱 (1) user_devices.platform 컬럼 additive 추가 →
    // (2) findTokens 가 플랫폼별 그룹으로 토큰을 반환 → (3) 이 함수가 platform 을 받아
    // setApnsConfig/setAndroidConfig/setWebpushConfig 로 분기. 인터페이스(FcmMessageSender)와
    // 호출부(PushNotificationChannel)는 그대로다.
    private fun MulticastMessage.Builder.applyPlatformConfig(): MulticastMessage.Builder = this

    companion object {
        // FCM MulticastMessage 한 번에 최대 500 토큰 (Firebase 제한).
        private const val MULTICAST_LIMIT = 500

        // 발송 실패 토큰 중 "정리 대상(죽은 토큰)" 판정 — FirebaseMessaging 응답 구조와 무관한 순수 정책이라
        // companion 으로 분리해 단위 테스트로 분기를 망라한다(FirebaseApp 없이 검증).
        // UNREGISTERED(앱 삭제·토큰 폐기)·INVALID_ARGUMENT(형식 무효)만 제거하고, 그 외(네트워크·서버 일시 오류·
        // 쿼터 등)와 코드 부재는 다음 발송에서 재시도되도록 보존한다.
        internal fun isStaleToken(code: MessagingErrorCode?): Boolean =
            when (code) {
                MessagingErrorCode.UNREGISTERED, MessagingErrorCode.INVALID_ARGUMENT -> true
                else -> false
            }

        // 백그라운드 수신 시 클라가 딥링크를 복원하려고 읽는 data 키 — FE 와 공유하는 contract.
        // 값은 NotificationSsePayload 의 필드명(type·refId)과 일치시켜, 클라가 SSE/FCM 어느 채널로 받든
        // 같은 키로 딥링크를 읽게 한다. (SSE 는 data class 프로퍼티명을 Jackson 이 직렬화하므로 그 쪽은
        //  같은 문자열을 상수로 빼지 못한다 — 이 상수의 값으로 일치를 맞춘다.)
        private const val DATA_KEY_TYPE = "type"
        private const val DATA_KEY_REF_ID = "refId"
    }
}
