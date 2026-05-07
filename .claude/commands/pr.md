브랜치에서 작업한 내용을 STAR 구조 PR로 정리하여 GitHub에 올립니다. 이미 PR이 있으면 본문을 덮어쓰지 않고 `## Updates` 섹션에 추가 변경 내역을 append 합니다.

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
3. 확인 후 `gh pr create --base $BASE` 로 PR 생성
4. PR URL을 사용자에게 전달

### 3-B. Update 모드 — 기존 PR 본문 갱신

1. 기존 본문 가져오기:
   ```bash
   gh pr view --json body --jq '.body' > /tmp/pr_body.md
   ```
2. **이번 추가 변경 내역**에 대해 짧은 STAR 형식 항목 작성 (이번 세션에서 새로 한 일 위주).
3. 기존 본문 끝에 `## Updates` 섹션이 없으면 새로 추가, 있으면 그 안에 새 항목 append.
   - 항목은 날짜 또는 추가 변경의 의도를 sub-heading 으로 (`### CodeRabbit 리뷰 대응 (3건)`, `### dev 머지 충돌 해결` 등)
4. **기존 본문은 절대 덮어쓰지 않는다.** 갱신본 = 기존 본문 + Updates 항목 추가만.
5. **제목 변경 필요 검토**: 추가 변경으로 작업 의도/스코프가 바뀌었거나 기존 제목에 오타·부정확한 표현이 있으면 새 제목 제안. 그 외엔 제목 유지.
6. 사용자에게 갱신본(필요시 새 제목 포함, 변경 이유 짚어서)을 보여주고 확인받는다.
7. 확인 후 `gh pr edit --body-file /tmp/pr_body.md` 로 갱신. 제목 변경이 있으면 `--title "새 제목"` 추가.
8. PR URL 재출력.

### PR 제목 규칙
- 70자 이내
- 타입 prefix를 붙이지 않는다 (커밋과 다름)
- 예: `예외 처리 시스템 공통화`, `PR 생성 스킬 추가`
- update 모드에서는 기본적으로 제목 유지. 작업 의도/스코프 변화나 정정이 필요한 경우만 변경하고, 사용자에게 변경 이유를 짚어 확인받는다.

$ARGUMENTS
