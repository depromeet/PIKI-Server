package com.depromeet.piki.notification.service

import org.apache.commons.text.StringSubstitutor
import org.springframework.stereotype.Component

// ${변수} 플레이스홀더를 실제 값으로 치환한다. 변수 맵에 없는 플레이스홀더는 원문 그대로 남는다.
@Component
class NotificationTemplateRenderer {
    fun render(
        template: String,
        variables: Map<String, String>,
    ): String =
        StringSubstitutor(variables)
            // 변수 값(예: 사용자 닉네임 actorName)에 포함된 ${...} 를 재귀 치환하지 않는다.
            // 닉네임 같은 사용자 입력이 변수로 들어오므로, 값 안의 플레이스홀더가 의도치 않게 치환되거나
            // 자기참조로 무한루프(IllegalStateException) 나는 것을 막는다.
            .apply { setDisableSubstitutionInValues(true) }
            .replace(template)
}
