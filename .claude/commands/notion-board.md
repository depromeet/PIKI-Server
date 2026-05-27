`/pr` 의 마지막 단계로 자동 호출되어, 이번 PR을 Notion `프로젝트 일정 관리` 보드의 매칭 카드에 반영합니다 — 카드 본문 `개발 로그` 에 PR 링크 한 줄 append + `계획중` 이면 `진행중` 으로. 카드 생성·`완료` 이동·일정/담당자 변경은 하지 않습니다(사람 몫). 단독 수동 호출보다 `/pr` 흐름의 일부로 도는 것을 전제합니다.

## 전제

- **`$NOTION_TOKEN`** (Notion internal integration 토큰)이 필요하다. 없으면 이 단계 전체를 **조용히 스킵**하고 한 줄 안내만 남긴다 — PR 흐름을 실패시키지 않는다.
- 이 Bash 도구는 `~/.zshrc` 를 자동 로드하지 않으므로, 각 curl 블록 시작에 토큰을 로드한다:
  ```bash
  [ -z "$NOTION_TOKEN" ] && eval "$(grep '^export NOTION_TOKEN=' ~/.zshrc 2>/dev/null)"
  ```
- **모든 Notion 호출은 HTTP 200 을 확인한다.** read(카드 조회·본문 조회)가 비-200이면 그 단계에서 멈추고 보드 반영을 생략한다(best-effort). 비-200 응답을 본문인 양 파싱하면 401/403/429/5xx 를 "카드 없음"·"미기록"으로 오인해 잘못 진행한다.

## 상수

- 보드(DB) id: `5a0c800c-72cf-8307-8297-8124d888ca79`
- API 버전 헤더: `Notion-Version: 2022-06-28`
- 상태 property 이름: `상태`, 옵션: `계획중` / `진행중` / `보류` / `완료`

## 입력 (`/pr` 가 넘겨준다)

이번 PR 의 URL · 번호 · 브랜치명 · 연관 이슈 번호·제목 · `$ISSUE_LABELS` · PR 제목, 그리고 이번 대화 맥락.

## 절차

### 1. 게이트

- `$NOTION_TOKEN` 미설정 → 아무것도 하지 않고 조용히 스킵하고 한 줄만 보고한 뒤 종료한다 ("Notion 토큰 없어 보드 반영 생략"). 토큰 없이는 할 수 있는 게 없으므로 이 경우만 자동 스킵이다 (PR 결과에 영향 없음).
- 그 외에는 **라벨과 무관하게 항상 진행**한다. 보드 반영 여부를 라벨로 자동 판단해 조용히 버리지 않는다 — 3단계에서 사용자에게 묻고 사용자가 결정한다.

> `$ISSUE_LABELS` 는 스킵을 강제하는 게이트가 아니라 **참고 신호**일 뿐이다. `chore` / `test` / `infra` / `docs` / `refactor` 처럼 보통 보드 비대상인 내부 작업이면 3단계 확인에서 그 사실을 한 줄 덧붙여 알리되, 올릴지 말지는 사용자가 정한다. 매칭되는 카드가 없으면 자연히 보드에 안 올라간다.

### 2. 후보 카드 조회 (완료 아닌 카드)

전체를 받아 클라이언트에서 `상태 != 완료` 만 추린다 (`파트` 필드는 보드에서 일관되지 않으므로 필터·판단에 쓰지 않는다 — 참고용으로만 표시):

```bash
[ -z "$NOTION_TOKEN" ] && eval "$(grep '^export NOTION_TOKEN=' ~/.zshrc 2>/dev/null)"
DB=5a0c800c-72cf-8307-8297-8124d888ca79
code=$(curl -s -X POST "https://api.notion.com/v1/databases/$DB/query" \
  -H "Authorization: Bearer $NOTION_TOKEN" -H "Notion-Version: 2022-06-28" -H "Content-Type: application/json" \
  --data '{"page_size":100}' -o /tmp/nb_cards.json -w "%{http_code}")
[ "$code" = "200" ] || { echo "Notion query 실패 (HTTP $code) — 보드 반영 생략"; exit 0; }
python3 - <<'PY'
import json
d=json.load(open('/tmp/nb_cards.json'))
for p in d.get('results',[]):
    pr=p['properties']
    title=''.join(t['plain_text'] for t in pr['프로젝트명']['title'])
    st=pr['상태']['status']; st=st['name'] if st else '-'
    if st=='완료': continue
    part=pr['파트']['select']; part=part['name'] if part else '-'
    print(f"{st:<5} {part:<7} {p['id']}  {title}")
PY
```

### 3. 매칭 + 확인 (유일한 판단 지점)

- 이번 PR 단서(브랜치명·연관 이슈 번호·제목·PR 제목·대화 맥락)로 후보 중 가장 맞는 카드를 고른다. 백엔드 작업이면 보통 `[BE] ...` · `User` · 위시/토너먼트 등 기능 카드.
- 사용자에게 제안하고 확인받는다: **"이 PR을 '<카드명>' 에 기록할게, 맞아?"** (애매하면 상위 2~3개 제시). `$ISSUE_LABELS` 가 `chore` / `test` / `infra` / `docs` / `refactor` 면 **"이건 <라벨> 라 보통 보드 비대상인데, 그래도 기록할까?"** 처럼 그 신호를 확인 문구에 덧붙여 사용자가 판단하게 한다.
- 적절한 카드가 없으면 → **"보드에 맞는 카드가 없어. 직접 만든 뒤 다시 `/pr` 돌릴래?"** 하고 멈춘다. **자동 생성하지 않는다.**

이후 단계는 확정된 카드의 `<PAGE_ID>` 로 진행.

### 4. dedup (이미 기록됐는지) + 본문 구조 확인

```bash
[ -z "$NOTION_TOKEN" ] && eval "$(grep '^export NOTION_TOKEN=' ~/.zshrc 2>/dev/null)"
code=$(curl -s "https://api.notion.com/v1/blocks/<PAGE_ID>/children?page_size=100" \
  -H "Authorization: Bearer $NOTION_TOKEN" -H "Notion-Version: 2022-06-28" -o /tmp/nb_body.json -w "%{http_code}")
[ "$code" = "200" ] || { echo "카드 본문 조회 실패 (HTTP $code) — 보드 반영 생략"; exit 0; }
python3 - <<'PY'
import json
d=json.load(open('/tmp/nb_body.json'))
heading=False; pr_seen=False
for b in d.get('results',[]):
    t=b['type']; node=b.get(t,{})
    txt=''.join(x.get('plain_text','') for x in (node.get('rich_text',[]) if isinstance(node,dict) else []))
    if t=='heading_3' and txt.strip()=='개발 로그': heading=True
    if 'PR #<번호>' in txt: pr_seen=True   # <번호> 를 실제 PR 번호로
print('heading_exists=',heading,'pr_already_logged=',pr_seen)
PY
```

- `pr_already_logged=True` → append 생략하고 6단계(상태)로.
- `heading_exists` 값으로 5단계에서 heading 포함 여부 결정.

### 5. append (개발 로그)

`/tmp/nb_append.json` 을 만든다 — `개발 로그` heading 이 **없을 때만** heading_3 을 먼저 포함, 이어서 PR bullet (전체를 PR 링크로):

```json
{"children":[
  {"object":"block","type":"heading_3","heading_3":{"rich_text":[{"type":"text","text":{"content":"개발 로그"}}]}},
  {"object":"block","type":"bulleted_list_item","bulleted_list_item":{"rich_text":[
    {"type":"text","text":{"content":"PR #169 토너먼트 아이템 삭제 API (2026-05-24)","link":{"url":"<PR_URL>"}}}
  ]}}
]}
```

- bullet 텍스트 = `PR #<번호> <사람이 읽는 PR 제목> (<KST 오늘 날짜>)`. 날짜는 `date +%Y-%m-%d`.
- heading 이 이미 있으면 위 children 에서 heading_3 객체를 빼고 bullet 만.

```bash
[ -z "$NOTION_TOKEN" ] && eval "$(grep '^export NOTION_TOKEN=' ~/.zshrc 2>/dev/null)"
code=$(curl -s -X PATCH "https://api.notion.com/v1/blocks/<PAGE_ID>/children" \
  -H "Authorization: Bearer $NOTION_TOKEN" -H "Notion-Version: 2022-06-28" -H "Content-Type: application/json" \
  --data @/tmp/nb_append.json -o /dev/null -w "%{http_code}")
echo "append HTTP $code"; [ "$code" = "200" ] || echo "append 실패 — 사용자에게 보고"
```

### 6. 상태 이동 (계획중 → 진행중만)

2/4 에서 읽은 카드 `상태` 기준:

- `계획중` → `진행중` 으로:
  ```bash
  [ -z "$NOTION_TOKEN" ] && eval "$(grep '^export NOTION_TOKEN=' ~/.zshrc 2>/dev/null)"
  code=$(curl -s -X PATCH "https://api.notion.com/v1/pages/<PAGE_ID>" \
    -H "Authorization: Bearer $NOTION_TOKEN" -H "Notion-Version: 2022-06-28" -H "Content-Type: application/json" \
    --data '{"properties":{"상태":{"status":{"name":"진행중"}}}}' -o /dev/null -w "%{http_code}")
  echo "status HTTP $code"; [ "$code" = "200" ] || echo "상태 변경 실패 — 사용자에게 보고"
  ```
- `진행중` / `보류` / `완료` → 건드리지 않는다. **`완료` 이동은 자동화하지 않는다** — 기능 완료는 사람의 편집적 판단이다.

### 7. 보고

한 줄로: 어느 카드에 무엇을 했는지. 예) `'[BE] Tournament' 에 PR #169 기록, 상태 계획중→진행중.` dedup/스킵이면 그 사실을 보고.

## 안 하는 것 (스코프 경계)

- 카드 자동 생성, `완료` 상태 이동, 일정 필드(`시작일`/`마감일`/`진행률`)·`담당자` 변경 — 전부 사람 몫.
- `파트` 필드 신뢰 (보드에서 일관되지 않음). 매칭은 제목·맥락으로만.
- Claude 안 쓰는 팀원의 PR 은 잡지 못한다 (알려진 한계). 필요해지면 GitHub Action 으로 보강.
