package com.depromeet.piki.notification.fcm

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.ByteArrayInputStream
import java.util.Base64

/**
 * FCM 발송용 FirebaseApp 초기화 (#242).
 *
 * 서비스 계정 키 JSON 을 base64 인코딩한 `FIREBASE_SERVICE_ACCOUNT`(= `firebase.service-account`)
 * 환경변수에서 읽어 FirebaseApp 을 초기화한다. dev/prod 가 같은 Firebase 프로젝트를 공유하므로 이 키는
 * repo 공유 secret.
 *
 * 키가 없거나 빈 환경(로컬·테스트·secret 미설정)에서는 @ConditionalOnExpression(hasText) 로 이 설정이
 * 등록되지 않아 부팅이 깨지지 않는다 — 그 경우 FirebaseApp 빈이 없고, FCM 발송 채널(#245)이
 * @ConditionalOnBean 으로 비활성된다. (@ConditionalOnProperty 는 빈 문자열도 "존재"로 보고 통과시켜
 * 빈 secret 주입 시 GoogleCredentials 파싱까지 가 부팅이 깨지므로, hasText 식으로 빈값을 가드한다.)
 * (멀티라인 JSON 을 docker run -e 로 안전 전달하려고 base64 한 줄로 주입한다.)
 */
@Configuration
@ConditionalOnExpression("T(org.springframework.util.StringUtils).hasText('\${firebase.service-account:}')")
class FirebaseConfig {
    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun firebaseApp(
        @Value("\${firebase.service-account}") serviceAccountBase64: String,
    ): FirebaseApp {
        // getMimeDecoder 는 개행·공백을 허용한다 — base64 가 줄바꿈 포함으로 주입돼도 견딘다(getDecoder 는 실패).
        val json = Base64.getMimeDecoder().decode(serviceAccountBase64.trim())
        val credentials = GoogleCredentials.fromStream(ByteArrayInputStream(json))
        val options = FirebaseOptions.builder().setCredentials(credentials).build()
        // 컨텍스트 재기동(테스트 컨텍스트 캐시 등)에서 중복 초기화를 피한다.
        return if (FirebaseApp.getApps().isEmpty()) {
            FirebaseApp.initializeApp(options).also { log.info("FirebaseApp 초기화 완료") }
        } else {
            FirebaseApp.getInstance()
        }
    }
}
