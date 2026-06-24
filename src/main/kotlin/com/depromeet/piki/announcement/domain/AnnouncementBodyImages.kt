package com.depromeet.piki.announcement.domain

// 공지 본문(마크다운)에서 이미지 URL 을 추출·치환하는 순수 함수 모음(#561 이미지 rehost).
// rehost 는 "본문의 외부 이미지 URL 을 우리 S3 로 옮기고 본문 URL 을 우리 것으로 바꾸는" 작업이라,
// 그 두 연산(어떤 URL 이 있나 / 그 URL 을 무엇으로 바꾸나)을 마크다운 파싱 한 곳에 모은다.
object AnnouncementBodyImages {
    // 마크다운 이미지 문법 `![alt](url "title")` 의 url 캡처. url 은 공백·')' 를 포함하지 않고,
    // 선택적 title("...")은 무시한다. Toast UI 에디터의 getMarkdown() 출력 형식(인라인 이미지)을 대상으로 한다.
    private val IMAGE = Regex("""!\[[^\]]*]\(\s*([^)\s]+)(?:\s+"[^"]*")?\s*\)""")

    // 본문에 등장하는 모든 이미지 URL(중복 제거 전 원순서).
    fun urls(body: String): List<String> = IMAGE.findAll(body).map { it.groupValues[1] }.toList()

    // 각 이미지 URL 을 transform 결과로 치환한다. transform 이 null 을 주면 그 URL 은 그대로 둔다.
    // 매치의 url 그룹 위치만 정확히 바꿔, alt 텍스트가 우연히 url 과 같아도 오치환되지 않게 한다.
    fun rewrite(
        body: String,
        transform: (String) -> String?,
    ): String =
        IMAGE.replace(body) { match ->
            val url = match.groupValues[1]
            val replacement = transform(url) ?: return@replace match.value
            val urlRange = match.groups[1]!!.range
            val start = urlRange.first - match.range.first
            val end = urlRange.last - match.range.first + 1
            match.value.substring(0, start) + replacement + match.value.substring(end)
        }
}
