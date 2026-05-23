브랜치에서 작업한 내용을 STAR 구조 PR로 정리하여 GitHub에 올립니다. 이미 PR이 있으면 본문을 덮어쓰지 않고 `## Updates` 섹션에 추가 변경 내역을 append 합니다. assignee(`@me`) · 라벨(연관 이슈에서 복사) · Project(99) 도 자동 설정합니다.

## PR 본문 작성 원칙

**대화 컨텍스트가 핵심이다.** diff 요약이 아니라, 이번 세션에서 나눈 고민·트레이드오프·결정 이유가 PR의 가치다.

## 절차

### 0단계: 모드 결정 + base branch 자동 감지

**현재 브랜치의 PR 존재 여부 확인** — update 모드 vs create 모드 결정:

```bash
gh pr view --json url,number,body,baseRefName 2>/dev/null
```

- 결과 있음 → **update 모드** (`### 3-B`)
- 결과 없음 → **create 모드** (`### 3-A`)

**`$BASE` 결정**:

- **create 모드** — 우선순위로 자동 감지:
  ```bash
  # 우선순위: origin/dev → 레포 default branch → main
  if git rev-parse --verify origin/dev >/dev/null 2>&1; then
    BASE=dev
  else
    BASE=$(gh repo view --json defaultBranchRef --jq '.defaultBranchRef.name' 2>/dev/null || echo main)
  fi
  ```
- **update 모드** — 기존 PR 의 base 사용:
  ```bash
  BASE=$(gh pr view --json baseRefName --jq '.baseRefName')
  ```
- `$ARGUMENTS` 에 사용자가 base 명시한 경우 (`/pr main` 같은) 그 값을 우선 (create 모드 한정 — update 모드에서 base 변경하지 않는다).

### 1단계: 정보 수집

아래 명령을 병렬로 실행하여 변경 내역을 파악한다 (`$BASE` 는 0단계에서 결정):

- `git log $BASE..HEAD --oneline` — 커밋 목록
- `git diff $BASE...HEAD --stat` — 변경 파일 요약
- `git diff $BASE...HEAD` — 실제 변경 내용
- `git status` — 현재 상태 (커밋되지 않은 변경이 있는지)

커밋되지 않은 변경이 있으면 먼저 커밋할지 사용자에게 확인한다.

**브랜치 이름에서 이슈 번호 자동 추출**:
정규식 `^[a-z]+/(\d+)-` 로 추출 (예: `chore/75-pr-skill-upgrade` → `#75`).

- 매칭 → `## 연관 이슈\n- close #{번호}` 본문에 자동 채움
- 매칭 안 됨 → 섹션은 유지하되 항목을 빈 줄로 둔다 (`- ` 만). 이슈 미연결 상태가 본문에서 명시적으로 드러나야 작성자가 의도적으로 비웠음을 인지할 수 있다.

**연관 이슈에서 PR 라벨 자동 수집** (이슈 번호 매칭 시):
PR 은 연관 이슈와 같은 분류를 갖는 것이 자연스러우므로, 이슈의 라벨을 그대로 PR 라벨로 쓴다.

```bash
ISSUE_LABELS=$(gh issue view {번호} --json labels --jq '[.labels[].name] | join(",")' 2>/dev/null)
```

- 라벨이 있으면 `### 3-A` / `### 3-B` 에서 `--label "$ISSUE_LABELS"` 로 PR 에 부여.
- 이슈 번호 매칭이 안 됐거나 이슈에 라벨이 없으면 라벨 없이 진행.

### 2단계: STAR 본문 작성 — create 모드 한정

(update 모드는 `### 3-B` 의 자체 가이드를 따른다 — 기존 본문은 그대로 두고 Updates 섹션에 짧은 STAR 항목만 추가)

이번 대화에서 나눈 내용을 중심으로, 아래 템플릿을 채운다:

```
## Situation
- 이 작업이 필요했던 배경/문제 상황
- 대화에서 논의된 동기나 맥락

## Task
- 해결하려 한 핵심 과제
- 대화에서 중요하게 다뤘던 고민이나 결정 포인트

## Action
- 실제로 한 일 (코드 변경 기반)
- 대화에서 논의한 트레이드오프나 선택의 이유

## Result
- 변경의 결과/효과
- 주의할 점이나 후속 작업이 있으면 언급

---
## 연관 이슈

- close #{자동 추출된 번호}     ← 추출 실패 시 `- ` 빈 항목으로
```

**작성 지침:**
- 각 섹션은 bullet point(`-`)로 작성
- 대화에서 나온 고민, 왜 이 방식을 선택했는지, 어떤 대안을 검토했는지를 우선 반영
- diff에서만 보이는 기계적 변경 나열은 최소화
- Task 섹션은 Slack PR 봇이 읽으므로, 핵심 작업을 간결하게 요약
- 한국어로 작성, 기술 용어는 영어 허용
- **1단계에서 수집한 `git log` 의 모든 커밋이 STAR(특히 Action)에 빠짐없이 반영됐는지 최종 점검한다.** 해시 명기는 update 모드 전용이지만, "누락된 커밋이 없는지" 점검은 create 모드도 같은 레벨로 거친다 — 기억·추측이 아니라 로그와 대조한다.

**Action 섹션 그룹화 가이드:**
- bullet 이 6개 이상이거나 결이 다른 갈래(설계 / UX / 안전망 / 드라이푸딩 / 검토 후 채택 안 한 안 등)가 섞이면 sub-heading(`### 설계` 등)으로 그룹화한다.
- 그룹은 작업 성격에 따라 자유롭게 — 매번 같은 그룹명을 쓸 필요 없음. 이번 PR 이 다룬 결을 가장 잘 드러내는 이름으로.
- bullet 5개 이하의 짧은 PR 은 그룹화 없이 평범한 bullet 리스트로 둔다.

**채울 수 없는 섹션 처리:**
- 대화 컨텍스트나 diff에서 근거를 찾을 수 없는 섹션은 **억지로 채우지 않는다**
- 해당 섹션에 `- TODO: 작성자가 직접 보완해주세요` 를 남긴다
- 부분적으로만 파악 가능한 경우, 파악된 내용만 적고 나머지에 `- TODO: ...` 를 추가한다

### 3-A. Create 모드 — PR 신규 생성

1. 원격에 푸시되지 않았으면 `git push -u origin {브랜치명}`
2. 사용자에게 PR 제목과 본문 초안을 보여주고 확인받는다
3. 확인 후 PR 생성 — assignee / 라벨을 함께 부여한다:
   ```bash
   gh pr create --base $BASE \
     --title "{제목}" \
     --body-file /tmp/pr_body.md \
     --assignee @me \
     ${ISSUE_LABELS:+--label "$ISSUE_LABELS"}
   ```
   - `--assignee @me` — PR 작성자가 작업자라는 가정 (`issue` 스킬과 동일).
   - `--label` — 1단계에서 수집한 `$ISSUE_LABELS` 가 있을 때만 붙인다.
   - 라벨이 레포에 없어 실패하면 라벨 없이 재시도하고 사용자에게 보고한다.
4. **Project 추가 + Status In review + Start date** — 생성된 PR 을 Project 99 에 등록하고 Status 를 `In review`, Start date 를 첫 commit author date 로 세팅한다. `item-add` 만 하면 기본값 `Backlog` 이 되므로, 반환된 item id 로 후속 mutation 들을 이어서 호출한다:
   ```bash
   ITEM_ID=$(gh project item-add 99 --owner depromeet --url {PR URL} --format json --jq '.id')

   # Status → In review
   gh project item-edit \
     --project-id PVT_kwDOARZVGM4BVVRV \
     --id "$ITEM_ID" \
     --field-id PVTSSF_lADOARZVGM4BVVRVzhQxyAA \
     --single-select-option-id df73e18b

   # Start date → 첫 commit author date (작업이 실제로 시작된 시점의 fact)
   START_DATE=$(git log --reverse "$BASE..HEAD" --format=%aI | head -1 | cut -d'T' -f1)
   gh api graphql -F itemId="$ITEM_ID" -F date="$START_DATE" -f query='
     mutation($itemId: ID!, $date: Date!) {
       updateProjectV2ItemFieldValue(input: {
         projectId: "PVT_kwDOARZVGM4BVVRV"
         itemId: $itemId
         fieldId: "PVTF_lADOARZVGM4BVVRVzhQxyGA"
         value: { date: $date }
       }) { projectV2Item { id } }
     }'
   ```
   - PR 을 올린다는 것은 곧 리뷰 대기 상태이므로 `In review` 가 자연스럽다. create 모드는 방금 만든 PR 이라 Status 가 항상 기본값(`Backlog`)이므로 조건 없이 세팅한다 (update 모드 10단계는 기존 Status 를 확인 후 분기).
   - Start date 는 "이슈 생성일" 이 아니라 **첫 commit author date** 를 쓴다 — 이슈만 만들어 두고 작업 안 들어가는 백로그 케이스의 노이즈를 피하기 위해. 첫 commit 은 history rewrite 가 없는 한 바뀌지 않는 fact 라 create 모드에선 무조건 set.
   - ID 의미: project=99 노드 ID, Status 필드/`In review` 옵션 ID, Start date 필드 ID(`PVTF_..GA`, DATE 타입). 보드에서 필드/옵션이 바뀌면 이 ID 들도 갱신 필요.
   - Target date 와 Status `Done` 은 PR 머지 시점에 별도 CI workflow (`.github/workflows/project-sync-on-pr-close.yml`) 가 자동 세팅. PR 스킬은 머지 이전 단계만 책임짐.
   - 권한 부족 시 사용자에게 `gh auth refresh -h github.com -s project,read:project` 안내 (일회성 디바이스 인증).
5. PR URL 과 부여된 assignee / 라벨 / Project(Status: In review, Start date) 결과를 사용자에게 전달

### 3-B. Update 모드 — 기존 PR 본문 갱신

1. 기존 본문 가져오기:
   ```bash
   gh pr view --json body --jq '.body' > /tmp/pr_body.md
   ```
2. **이번 추가 변경 내역을 `git log` 로 정확히 식별한다 — 기억·추측에 의존하지 않는다.**
   ```bash
   git log $BASE..HEAD --oneline   # PR 의 전체 커밋
   ```
   - PR 전체 커밋 중 기존 본문·`## Updates` 에 **이미 반영된 커밋(해시로 대조)** 을 제외해, 이번에 새로 추가된 커밋만 가려낸다.
   - merge 커밋이 있으면 `--no-merges` 로 우리 커밋만, merge 사실 자체는 별도 항목으로 다룬다.
   - 가려낸 커밋 목록과 실제 변경(`git diff`)을 대조해 누락이 없는지 확인한다.
3. 가려낸 새 커밋들에 대해 짧은 STAR 형식 항목을 작성한다.
   - **각 항목 끝에 해당 커밋의 short hash(7자리)를 `` (`a1b2c3d`) `` 형태로 명기**한다. 어떤 변경이 어떤 커밋인지 리뷰어가 추적할 수 있고, 다음 갱신 때 "무엇이 이미 반영됐는지" 대조 기준이 된다.
   - 한 항목이 여러 커밋을 묶으면 해시를 모두 적는다 (예: `` (`d3e53ab`, `cbef613`) ``).
   - 모든 새 커밋이 어느 항목엔가 반영됐는지 최종 점검한다 — 빠진 커밋이 없어야 한다.
4. 기존 본문 끝에 `## Updates` 섹션이 없으면 새로 추가, 있으면 그 안에 새 항목 append.
   - 항목은 날짜 또는 추가 변경의 의도를 sub-heading 으로 (`### CodeRabbit 리뷰 대응`, `### dev 머지 충돌 해결` 등)
5. **기존 본문은 절대 덮어쓰지 않는다.** 갱신본 = 기존 본문 + Updates 항목 추가만.
6. **제목 변경 필요 검토**: 추가 변경으로 작업 의도/스코프가 바뀌었거나 기존 제목에 오타·부정확한 표현이 있으면 새 제목 제안. 그 외엔 제목 유지.
7. 사용자에게 갱신본(필요시 새 제목 포함, 변경 이유 짚어서)을 보여주고 확인받는다.
8. 확인 후 `gh pr edit --body-file /tmp/pr_body.md` 로 갱신. 제목 변경이 있으면 `--title "새 제목"` 추가.
9. **CodeRabbit 리뷰 대응** — 이번 변경이 CodeRabbit 리뷰 대응이라면 commit + push 로 끝내지 않는다. CodeRabbit 리뷰(인라인 thread + review body nitpick) 조회·평가·reply·resolve 는 **`/coderabbit` 스킬**로 처리한다. 그 스킬이 author 매칭(GraphQL `reviewThreads` 는 `coderabbitai`, REST `reviews` 는 `coderabbitai[bot]` 이라 `coderabbitai` 로 시작하는지로 판별), nitpick 조회, accept/reject reply·resolve 정책을 담는다. (사람 리뷰 thread 는 작성자가 직접 답하므로 `/coderabbit` 도 건드리지 않는다.)

10. **메타데이터 보정** — 이전 버전 스킬로 만든 PR 은 assignee / 라벨 / Project / Start date 가 비어 있을 수 있다. update 모드에서도 멱등하게 보정한다 (이미 설정돼 있으면 no-op). `item-add` 는 이미 등록된 PR 이면 기존 item id 를 그대로 반환한다.
    Status 는 **현재 값을 먼저 조회해, 리뷰 이전 단계(`Backlog` / `Ready` / `In progress`)일 때만** `In review` 로 올린다 — 이미 `Done` 등으로 옮긴 PR 을 되돌리지 않기 위함이다. Start date 도 멱등 — 이미 set 되어 있으면 건드리지 않는다 (사람이 수동으로 다른 의미로 박았을 수 있어 보존). Status / Start date 조회는 item 노드를 직접 부르는 GraphQL 이 안정적이다 (`gh project item-list` 는 단일 선택 필드 값을 신뢰성 있게 주지 않는다):
    ```bash
    gh pr edit --add-assignee @me ${ISSUE_LABELS:+--add-label "$ISSUE_LABELS"}
    ITEM_ID=$(gh project item-add 99 --owner depromeet --url {PR URL} --format json --jq '.id')

    # Status + Start date 현재 값 동시 조회
    read CURRENT_STATUS CURRENT_START < <(gh api graphql -F id="$ITEM_ID" -f query='
      query($id: ID!) {
        node(id: $id) {
          ... on ProjectV2Item {
            status: fieldValueByName(name: "Status") {
              ... on ProjectV2ItemFieldSingleSelectValue { name }
            }
            start: fieldValueByName(name: "Start date") {
              ... on ProjectV2ItemFieldDateValue { date }
            }
          }
        }
      }' --jq '[(.data.node.status.name // "null"), (.data.node.start.date // "null")] | @tsv')

    # Status: 리뷰 이전 단계일 때만 In review 로 올림
    if [[ "$CURRENT_STATUS" == "Backlog" || "$CURRENT_STATUS" == "Ready" || "$CURRENT_STATUS" == "In progress" ]]; then
      gh project item-edit \
        --project-id PVT_kwDOARZVGM4BVVRV \
        --id "$ITEM_ID" \
        --field-id PVTSSF_lADOARZVGM4BVVRVzhQxyAA \
        --single-select-option-id df73e18b
    fi

    # Start date: 비어있을 때만 첫 commit author date 로 세팅
    if [[ "$CURRENT_START" == "null" ]]; then
      START_DATE=$(git log --reverse "$BASE..HEAD" --format=%aI | head -1 | cut -d'T' -f1)
      gh api graphql -F itemId="$ITEM_ID" -F date="$START_DATE" -f query='
        mutation($itemId: ID!, $date: Date!) {
          updateProjectV2ItemFieldValue(input: {
            projectId: "PVT_kwDOARZVGM4BVVRV"
            itemId: $itemId
            fieldId: "PVTF_lADOARZVGM4BVVRVzhQxyGA"
            value: { date: $date }
          }) { projectV2Item { id } }
        }'
    fi
    ```
11. PR URL 재출력.

### PR 제목 규칙
- 70자 이내
- 타입 prefix를 붙이지 않는다 (커밋과 다름)
- 예: `예외 처리 시스템 공통화`, `PR 생성 스킬 추가`
- update 모드에서는 기본적으로 제목 유지. 작업 의도/스코프 변화나 정정이 필요한 경우만 변경하고, 사용자에게 변경 이유를 짚어 확인받는다.

$ARGUMENTS
