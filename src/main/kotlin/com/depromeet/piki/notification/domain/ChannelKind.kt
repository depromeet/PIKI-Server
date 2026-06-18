package com.depromeet.piki.notification.domain

// 알림 전달 채널의 종류. 타입별 채널 선택(NotificationChannelPolicy)과 채널 구현(NotificationChannel.kind)이 공유하는 식별자다.
// SSE 와 PUSH 는 같은 일을 상황 따라 나누는 게 아니라 다른 일을 한다 — SSE 는 인앱 실시간 채널(앱 열려있을 때
// UI·데이터 라이브 갱신, 폴링 대체), PUSH(FCM)는 앱이 닫힘/백그라운드일 때 OS 트레이로 다시 부르는 채널이다.
enum class ChannelKind {
    SSE,
    PUSH,
}
