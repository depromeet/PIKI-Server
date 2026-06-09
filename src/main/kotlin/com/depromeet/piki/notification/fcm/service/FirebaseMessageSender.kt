package com.depromeet.piki.notification.fcm.service

import com.depromeet.piki.notification.domain.Notification
import com.depromeet.piki.notification.domain.NotificationKind
import com.depromeet.piki.notification.domain.NotificationRouting
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.AndroidConfig
import com.google.firebase.messaging.AndroidNotification
import com.google.firebase.messaging.ApnsConfig
import com.google.firebase.messaging.Aps
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.MessagingErrorCode
import com.google.firebase.messaging.MulticastMessage
import com.google.firebase.messaging.WebpushConfig
import com.google.firebase.messaging.WebpushFcmOptions
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
                val code = result.exception?.messagingErrorCode
                if (isStaleToken(code)) {
                    stale += chunk[i]
                } else {
                    // 토큰 무관 실패(요청 오류·일시 오류 등)는 토큰을 지우지 않고 코드만 남긴다(토큰은 민감).
                    log.warn("FCM 발송 실패(토큰 보존) code={}", code)
                }
            }
        }
        return stale
    }

    // 표시용 title/body + 클라 라우팅용 data(type·refId·라우팅 컨텍스트)를 실은 멀티캐스트 메시지.
    // 백그라운드 수신 시 클라가 data 로 딥링크를 복원한다. data 키 구성은 buildDataPayload 가 결정한다.
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
            ).apply { buildDataPayload(notification).forEach { (key, value) -> putData(key, value) } }
            .applyPlatformConfig()
            .build()

    // 플랫폼별 푸시 옵션을 한 메시지에 모두 싣는다 — FCM 이 각 토큰의 플랫폼에 맞는 config 만 적용하므로
    // (iOS 토큰엔 APNS, Android 토큰엔 Android, 웹 토큰엔 WebPush) 한 발송이 세 플랫폼을 다 커버한다.
    // 따라서 user_devices.platform 컬럼 없이도 안전하고, iOS 발송에도 무해하다(다른 config 는 무시됨).
    // 플랫폼마다 "다른 내용"을 보내야 할 때만 (1) platform 컬럼 (2) findTokens 플랫폼 그룹핑이 필요해진다.
    private fun MulticastMessage.Builder.applyPlatformConfig(): MulticastMessage.Builder =
        this
            // iOS(APNS) — 백그라운드 표시 시 소리. 포그라운드 표시는 클라가 SSE 로 처리해 억제한다.
            .setApnsConfig(
                ApnsConfig
                    .builder()
                    .setAps(Aps.builder().setSound("default").build())
                    .build(),
            )
            // Android — 8.0+ 는 채널이 있어야 알림이 표시된다. 이 channelId 는 Android 클라가 동일 id 로
            // NotificationChannel 을 생성해야 매칭된다(FE 와 공유하는 상수). HIGH 로 헤드업 표시.
            .setAndroidConfig(
                AndroidConfig
                    .builder()
                    .setPriority(AndroidConfig.Priority.HIGH)
                    .setNotification(
                        AndroidNotification
                            .builder()
                            .setChannelId(ANDROID_CHANNEL_ID)
                            .setDefaultSound(true)
                            .build(),
                    ).build(),
            )
            // Web — 알림 클릭 시 이동할 링크. 아이콘 등 웹 전용 옵션·VAPID·서비스워커는 웹 클라 붙일 때 채운다.
            .setWebpushConfig(
                WebpushConfig
                    .builder()
                    .setFcmOptions(WebpushFcmOptions.withLink(WEB_CLICK_LINK))
                    .build(),
            )

    companion object {
        // FCM MulticastMessage 한 번에 최대 500 토큰 (Firebase 제한).
        private const val MULTICAST_LIMIT = 500

        // Android 알림 채널 id — Android 클라가 같은 id 로 NotificationChannel 을 만들어야 8.0+ 에서 표시된다(FE 합의 상수).
        private const val ANDROID_CHANNEL_ID = "piki_default"

        // 웹 푸시 클릭 시 이동할 링크(https 필수) — 웹 프론트 붙을 때 실제 경로로 조정.
        private const val WEB_CLICK_LINK = "https://depromeet18team3.cloud"

        // 발송 실패 토큰 중 "정리 대상(죽은 토큰)" 판정 — FirebaseMessaging 응답 구조와 무관한 순수 정책이라
        // companion 으로 분리해 단위 테스트로 분기를 망라한다(FirebaseApp 없이 검증).
        // UNREGISTERED(앱 삭제·토큰 폐기)만 보수적으로 삭제한다. INVALID_ARGUMENT 는 토큰이 아닌 요청·메시지
        // 파라미터 문제에도 올 수 있어, 그걸 stale 로 보면 코드 버그가 정상 토큰을 대량 삭제할 위험이 있다 →
        // 삭제하지 않고 로그로 남긴다(send 의 else 분기). 네트워크·일시 오류·코드 부재도 보존해 재시도된다.
        internal fun isStaleToken(code: MessagingErrorCode?): Boolean = code == MessagingErrorCode.UNREGISTERED

        // 백그라운드 수신 시 클라가 딥링크를 복원하려고 읽는 data 키 — FE 와 공유하는 contract.
        // 값은 NotificationSsePayload 의 필드명(id·type·refId·kind·tournamentId·tournamentItemId)과 일치시켜,
        // 클라가 SSE/FCM 어느 채널로 받든 같은 키로 같은 알림을 다루게 한다. (SSE 는 data class 프로퍼티명을 Jackson 이
        //  직렬화하므로 그 쪽은 같은 문자열을 상수로 빼지 못한다 — 이 상수의 값으로 일치를 맞춘다.)
        // id 는 채널 무관 dedup(SSE·FCM 중복 수신 시 같은 알림으로 합침)과 푸시 탭 → 읽음 처리(POST /read {ids:[id]})의 키다(#246).
        private const val DATA_KEY_ID = "id"
        private const val DATA_KEY_TYPE = "type"
        private const val DATA_KEY_REF_ID = "refId"
        private const val DATA_KEY_KIND = "kind"
        private const val DATA_KEY_TOURNAMENT_ID = "tournamentId"
        private const val DATA_KEY_TOURNAMENT_ITEM_ID = "tournamentItemId"

        // FCM data payload(키→값) 구성 — id·type·refId 는 항상, 라우팅 컨텍스트(kind·tournamentId·tournamentItemId)는
        // 있을 때만 싣는다. FCM data 는 값이 null 일 수 없어 null 키는 아예 넣지 않는다(#408). FirebaseMessaging
        // 호출과 무관한 순수 매핑이라 companion 으로 분리해 단위 테스트로 분기를 망라한다(FirebaseApp 없이 검증).
        // notification 은 dispatcher 가 이미 저장한 영속 엔티티라 getId() 가 보장된다(SsePayload.from 과 동일 전제).
        internal fun buildDataPayload(notification: Notification): Map<String, String> =
            buildMap {
                put(DATA_KEY_ID, notification.getId().toString())
                put(DATA_KEY_TYPE, notification.type.name)
                put(DATA_KEY_REF_ID, notification.refId.toString())
                // SSE payload 와 같은 routing() sealed 를 분기해 두 채널이 한 소스를 쓴다. FCM data 는 값이 null 일 수 없어
                // 라우팅이 없으면 키 자체를 안 넣고, TOURNAMENT 만 추가 식별자를 싣는다(#408).
                when (val routing = notification.routing()) {
                    null -> Unit
                    NotificationRouting.Wish -> put(DATA_KEY_KIND, NotificationKind.WISH.name)
                    is NotificationRouting.Tournament -> {
                        put(DATA_KEY_KIND, NotificationKind.TOURNAMENT.name)
                        put(DATA_KEY_TOURNAMENT_ID, routing.tournamentId.toString())
                        put(DATA_KEY_TOURNAMENT_ITEM_ID, routing.tournamentItemId.toString())
                    }
                }
            }
    }
}
