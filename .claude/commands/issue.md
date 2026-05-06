이슈를 생성하고, 브랜치를 만들고, 이슈에 매핑하고, 라벨/Project를 설정합니다.

## 원칙

**이슈는 작업의 시작점이라 사용자 입력이 source of truth.** 모델이 추측해 본문을 채우거나 분류를 단정하지 않는다. 자동화하는 부분은 모두 검수 단계에서 사용자 OK 를 받는다.

**Epic 과 일반 이슈는 본질이 다르다.** Epic 은 여러 작업의 묶음으로 자체 코드 작업이 없어 브랜치를 만들지 않는다. 일반 이슈(task/refactor/bug)는 코드 작업의 단위로 부모 Epic 에 묶일 수 있다. 이 구분만 사용자가 명시하고, 그 외 분류·prefix·상위 Epic 추천은 모델이 본문을 보고 자동 결정한 뒤 검수받는다.

**채팅 끊김을 최소화한다.** 자유 텍스트는 한 메시지로 일괄 받고, 옵션형 결정은 `AskUserQuestion` 으로 묶어 받는다. 사용자 인터랙션은 보통 3~4 라운드 안에 끝난다.

## 절차

### 1단계: Epic 여부

`AskUserQuestion` (single-select):

- `일반 이슈 (task/refactor/bug) (Recommended)`
- `Epic`

대화 컨텍스트가 명확하면 default 로 표시. 모호하면 추측 없이 그대로 보여준다.

선택에 따라 흐름이 갈린다.

---

## A. Epic 흐름

Epic 은 브랜치를 만들지 않고 상위 Epic 도 없다.

### A-1. 자유 입력 (1회)

사용자에게 다음과 같이 안내:

> "이슈 내용을 자유롭게 적어주세요. 제목 / 왜 / 무엇을 모두 포함해 한 번에 작성하시면 됩니다. 모델이 알아서 분해/정리합니다."

사용자 입력이 너무 짧아 분해가 어려우면 (예: "이슈 스킬 추가" 한 줄) 1~2 번 follow-up 질문 (예: "왜 필요한지 한 줄만 더 알려주세요"). 더 이상 묻지 않는다.

### A-2. 옵션형 결정 — `AskUserQuestion` 묶음 (3 questions)

```
Q1: 우선순위
    - High / Medium / Low (Recommended)
Q2: 시작일
    - 오늘 (Recommended) / 1주일 후 / 2주일 후 / 직접 입력
    + Other 로 MMDD 직접 입력 가능
Q3: 마감일
    - 1주일 후 (Recommended) / 2주일 후 / 4주일 후 / 직접 입력
    + Other 로 MMDD 직접 입력 가능
```

### A-3. 모델 자동 분해 + 중복 검사

자유 입력을 분석해 다음을 결정:

- **제목** (한 줄, 70자 이내, kebab-case 슬러그용 의미 보존)
- **왜** (배경, 필요성)
- **무엇을** (목표 상태, 예상 범위)

분해 룰 (다듬기 통합):
- 표현 자연스럽게, 단답을 풍부한 문장으로 풀기 OK
- 적절한 컨텍스트 추론 OK (예: "성능 개선" → "현재 X 처리가 느려 개선 필요")
- ❌ 구체적 수치/날짜/외부 시스템·라이브러리/책임자 새로 만들어내기 금지
- ❌ 사용자가 명시한 사실과 다른 내용 금지

분해된 제목으로 중복 이슈 사전 검사:

```bash
gh issue list --repo depromeet/18th-team3-server --search "{제목 키워드}" --state open --json number,title,labels --limit 5
```

결과가 1개 이상이면 검수 단계에서 사용자에게 함께 보여줌.

### A-4. 검수 — `AskUserQuestion`

분해된 본문 + 일정 + 중복 검사 결과를 한 화면에 보여주고:

```
[본문 미리보기]
## 왜
{분해된 내용}

## 무엇을
{분해된 내용}

## 우선순위 / 시작일 / 마감일
...

[중복 검사 결과]
유사 이슈: #X "..." (있을 때만)
```

`AskUserQuestion` (single-select):

- `OK 진행 (Recommended)`
- `본문 수정 필요` — Other 로 어떤 부분을 어떻게
- `일정/우선순위 수정` — Other 로 어떻게
- `중복 — 새 이슈 만들지 않음` (중복 검사 결과 있을 때만)

수정 요청 들어오면 반영하고 다시 검수. 만족할 때까지 반복.

### A-5. 이슈 생성

```bash
gh issue create \
  --title "{제목}" \
  --body "{본문}" \
  --label "epic"
```

### A-6. Project 추가

```bash
gh project item-add 99 --owner depromeet --url {이슈 URL}
```

### A-7. 결과 출력

- 이슈 URL
- "Epic 은 브랜치를 만들지 않습니다. 하위 작업은 `/issue` 로 일반 이슈를 만들고 그 단계에서 이 epic 이 자동 추천됩니다."

---

## B. 일반 이슈 흐름

### B-1. 자유 입력 (1회)

A-1 과 동일.

### B-2. 옵션형 결정 — `AskUserQuestion` 묶음 (3 questions)

A-2 와 동일. 시작일/마감일은 분류가 아직 결정되지 않았으므로 `직접 입력` Other 에서 빈 칸도 허용 (refactor/bug 면 일정 선택, task 면 검수 단계에서 빠진 경우 안내).

### B-3. 모델 자동 분해 + 자동 결정 + 중복 검사

**자유 입력 분해** — A-3 과 동일 룰.

**분류 자동 결정** (multi 가능): `task` / `refactor` / `bug`
- "버그", "안 됨", "오류 수정" 시그널 → bug
- "구조 개선", "리팩터링", "정리", "추출" 시그널 → refactor
- "추가", "도입", "새" 시그널 → task
- 둘 이상 시그널 명확하면 multi
- 모호하면 `task` 로 fallback

**브랜치 prefix** (주 분류 기준):
- `bug` → `fix`
- `refactor` → `refactor`
- `task` → `chore` (default) 또는 `feat` (외부 사용자/클라이언트가 호출/이용할 새 기능 시그널이 본문에 명확히 있을 때만)
- 모호하면 `chore`

**주 분류 결정** (multi 일 때): "주된 작업의 본질" 기준. 모호하면 검수 단계에서 묻기.

**상위 Epic 추천**:
```bash
gh issue list --repo depromeet/18th-team3-server --label epic --state open --json number,title --limit 20
```
활성 epic 목록과 본문(제목+왜+무엇을)의 의미 비교로 가장 관련 있는 1개 추천. 없으면 추천 안 함.

**task 의 일정 보완**: 자동 분류가 task 면 시작일/마감일이 비어있는지 확인. 비어있으면 검수 단계에서 안내.

**중복 이슈 사전 검사**: A-3 과 동일하게 분해된 제목으로 검색.

### B-4. 검수 — `AskUserQuestion`

본문 + 일정 + 자동 결정 + 중복 검사 결과를 한 화면에 보여주고:

```
[본문 미리보기]
## 상위 Epic
#{추천 번호}            ← 없으면 통째 생략

## 왜 / 무엇을 / 우선순위 / 시작일 / 마감일
...

[자동 결정]
- 분류: {라벨들} (주 분류: {prefix 결정용})
- 브랜치 prefix: {chore|feat|refactor|fix}
- 상위 Epic: #{번호} 또는 없음

[중복 검사 결과]
유사 이슈: #X "..." (있을 때만)
```

`AskUserQuestion` (single-select):

- `OK 진행 (Recommended)`
- `본문 수정 필요` — Other 로 어떤 부분을 어떻게
- `분류/prefix 수정` — Other 로 어떻게
- `일정/우선순위 수정` — Other 로 어떻게

(중복 검사 결과 있을 때 옵션 1개 추가: `중복 — 새 이슈 만들지 않음`. 옵션 4개 한도라 다른 옵션 1개 묶기.)

수정 반영 후 만족할 때까지 반복.

### B-5. 이슈 생성

```bash
gh issue create \
  --title "{제목}" \
  --body "{본문}" \
  --label "{선택된 분류 라벨들 콤마 구분}"
```

### B-6. 브랜치 생성 + 매핑

브랜치명 슬러그는 분해된 제목에서 영문 kebab-case 로 생성 (한국어면 의미 보존하며 영문 의역).

```bash
gh issue develop {이슈번호} \
  --repo depromeet/18th-team3-server \
  --base dev \
  --name "{prefix}/{이슈번호}-{slug}" \
  --checkout
```

생성 후 자동 checkout 이 안 되면 명시적으로 `git checkout {prefix}/{이슈번호}-{slug}` 실행.

### B-7. Project 추가

```bash
gh project item-add 99 --owner depromeet --url {이슈 URL}
```

### B-8. 결과 출력

- 이슈 URL
- 브랜치명 + 현재 체크아웃된 브랜치 확인
- 다음 단계 안내 (작업 시작 → 커밋 → PR)

---

## 주의 사항

- **자유 입력이 너무 짧으면 follow-up.** 다만 1~2 번 안에 끝낸다. 무한 follow-up 금지.
- **모델 분해 결과는 검수 필수.** 사용자가 거부하면 즉시 부분 수정 또는 원본 그대로.
- 분류 자동 결정은 시그널이 명확할 때만. 모호하면 `task` + `chore` fallback (보수적).
- 라벨이 레포에 없어 `gh issue create` 가 실패하면 에러 그대로 보고.
- `gh issue develop` 은 `--name` (브랜치 이름 옵션) + `--checkout` 둘 다 명시 (인터랙티브 회피). `--branch-name` 은 존재하지 않는 옵션이니 주의.
- `gh project item-add` 권한 부족 시 사용자에게 `gh auth refresh -s project` 안내.
- 본문에 `#{epic 번호}` 가 들어가면 GitHub 가 자동 cross-reference 링크 — 별도 sub-issue API 불필요.
- 중복 이슈 검사 false positive 가능성 인지. 사용자가 "다른 이슈" 라 답하면 그대로 진행.

$ARGUMENTS
