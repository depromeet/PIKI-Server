세션을 종료해도 되는지 점검한다 — 기본은 현재 워크트리/브랜치만 read-only로 훑고, 정리 가능한 항목(현재 스코프)은 셀렉트로 처리한다. 인자 `all` 을 주면 전체 워크트리·브랜치·PR을 함께 스윕한다. 점검만 하며, 워크트리 정리(close)는 하지 않는다 — close 는 `/session-close` 를 직접 호출할 때만 일어난다.

## 모드

인자에 `all` 이 포함됐는지로 스코프를 정한다.

- **기본 (`/session-check`)** — 현재 워크트리 + 현재 브랜치/PR만 본다. "지금 이 작업"을 닫아도 되는지 확인하는 좁은 점검. session-close 와 스코프가 일치한다.
- **전역 (`/session-check all`)** — 위에 더해 전체 워크트리 dirty·prunable, 모든 로컬 브랜치 미푸시/미삭제, 본인 열린 PR 전부를 스윕한다. 여러 워크트리를 동시에 굴린 뒤 흘린 게 없는지 보는 위생 점검.

## 언제 쓰나

- (기본) 지금 작업을 마무리하고 세션을 닫기 전, 이 워크트리/브랜치에 흘린 변경·미푸시·미삭제가 없는지 확인할 때.
- (`all`) 여러 워크트리를 동시에 굴린 뒤 어디에 dirty/미푸시/미삭제가 남았는지 한눈에 보고 싶을 때.

## 원칙

- **점검만 한다 — close 는 하지 않는다.** session-check 는 상태를 점검하고, 동의받은 정리 셀렉트(커밋/푸시/브랜치 삭제/prune)까지만 한다. **워크트리를 나가거나 지우는 close 는 절대 하지 않고, `/session-close` 를 자동 호출하지도 않는다.** 닫아도 안전하다고 판단되면 사용자에게 `/session-close` 를 직접 입력하라고 **안내만** 한다 (아래 `## 절차` 6).
- **읽기 먼저, 변경은 동의 후.** 모든 점검은 read-only로 먼저 끝낸다. 커밋·푸시·브랜치 삭제·prune 같은 변경 동작은 `AskUserQuestion` 셀렉트로 사용자가 고른 것만 수행한다.
- **액션 가능한 항목만 셀렉트.** 처리 선택지가 있는 항목(uncommitted, 미푸시, 미삭제 브랜치, prunable 워크트리)만 셀렉트를 띄운다. 정보성 항목(CI 실패, 리뷰 대기, 새 TODO, 다른 워크트리 dirty)은 셀렉트 없이 리포트만 한다. **액션 가능한 게 하나도 없으면 셀렉트를 아예 띄우지 않고 리포트로 끝낸다.**
- **체크아웃 전환 금지.** 브랜치를 갈아타지 않는다 — 다른 워크트리/브랜치는 `git -C <path>` 와 ref 연산(push, branch -d, worktree prune)으로만 다룬다. 메인 체크아웃이 작업 중 외부에서 바뀌는 환경이므로 격리한다.
- **파괴적 동작 안 함.** untracked 파일 삭제, `git checkout -- .`, stash drop, 강제 푸시는 스킬이 하지 않는다. 커밋은 반드시 `/commit` 으로 위임한다(컨벤션 보존).
- **이모지·체크기호 금지.** 굵게·불릿으로만 상태를 표시한다.

## 점검 항목

먼저 현재 위치를 잡는다 (모드 공통).

```bash
BR=$(git rev-parse --abbrev-ref HEAD)
CUR=$(git rev-parse --show-toplevel)
```

### A. 현재 워크트리 Git 상태 (기본·전역 공통)

```bash
git status --porcelain        # staged / unstaged / untracked
git stash list                # 남은 stash
```

- uncommitted(staged + unstaged) → **액션**: `/commit` 호출 / stash / 그대로 둠
- untracked → 그중 `.md`·스크래치 파일은 작업 부산물일 수 있으니 따로 표시한다(삭제는 안 함, 커밋 여부는 `/commit` 판단에 위임).
- stash 잔여 → **정보성** 리포트만 (apply/drop은 위험하므로 자동 처리하지 않는다).

### B. 현재 브랜치 / PR (기본·전역 공통)

기본 모드에선 **현재 브랜치 하나만** 본다.

```bash
git for-each-ref --format='%(refname:short) | %(upstream:short) | %(upstream:track)' refs/heads/"$BR"   # 현재 브랜치의 ahead / no-upstream / [gone]
gh pr list --author "@me" --state open --head "$BR" --json number,title,headRefName,reviewDecision,statusCheckRollup
```

- `%(upstream:track)` 가 `[gone]` → 현재 브랜치의 upstream(원격 추적 브랜치)이 삭제됨 = **머지된 신호**. 단 현재 브랜치는 정의상 현재 워크트리에 체크아웃돼 있어 `git branch -D` 로 삭제 불가다. → **액션 아님, 안내**: "이 브랜치는 머지됨 → 워크트리째 정리하려면 `/session-close`." (session-close 가 워크트리 제거 + 브랜치 삭제를 함께 처리한다.)
- upstream 이 없음(=한 번도 push 안 됨)이거나 ahead → **액션**: `git push`(필요 시 `-u`) / 그대로 둠. `[gone]` 과 혼동하지 않는다 — `[gone]` 은 push 가 아니라 머지 신호다.
- 현재 브랜치 PR의 CI 실패(`statusCheckRollup` 에 FAILURE)·리뷰 대기(`reviewDecision` 가 REVIEW_REQUIRED / CHANGES_REQUESTED) → **정보성** 리포트.

### C. 전체 워크트리·브랜치 스윕 (`all` 모드 한정)

기본 모드에선 **건너뛴다.** 인자에 `all` 이 있을 때만 실행한다.

```bash
git worktree list --porcelain     # worktree 경로 / branch 파싱
git worktree prune --dry-run -v   # prunable(경로가 사라진) 워크트리
git for-each-ref --format='%(refname:short) | %(upstream:short) | %(upstream:track)' refs/heads   # 모든 브랜치
gh pr list --author "@me" --state open --json number,title,headRefName,reviewDecision,statusCheckRollup
```

파싱한 각 워크트리 경로에 대해:

```bash
git -C "<worktree_path>" status --porcelain
```

- 현재 세션 워크트리가 아닌 다른 워크트리에 dirty/untracked → **정보성** 리포트(거기서 정리하라고 안내). 커밋은 여기서 하지 않는다.
- prunable 워크트리 → **액션**: `git worktree prune` / 그대로 둠
- 현재 브랜치가 아닌 다른 브랜치가 `[gone]`(머지됐는데 안 지움) → **액션**: 삭제.
  - **삭제는 `git branch -D` 를 쓴다.** squash/rebase 머지된 브랜치는 커밋이 현재 HEAD 의 ancestor 가 아니라 `git branch -d`(안전 삭제)가 "not fully merged" 로 거부한다. `[gone]` 은 원격에서 이미 사라진 머지 완료 브랜치라 force 삭제(`-D`)가 안전하다. 확신이 필요하면 `gh pr list --state merged --json headRefName` 로 머지 여부를 교차 확인한다.
  - 단, 그 브랜치가 어느 워크트리에 체크아웃돼 있으면(위 `git worktree list` 의 branch 칼럼으로 판별) 삭제 불가 → 삭제 대신 "그 워크트리 먼저 정리 필요"로 안내한다(워크트리 제거는 사용자가 명시할 때만).
- 다른 브랜치의 upstream 없음/ahead → **액션**: `git push`(필요 시 `-u`). 다른 워크트리에 물린 브랜치는 `git -C <path> push` 로 격리한다.
- 다른 열린 본인 PR의 CI 실패·리뷰 대기 → **정보성** 리포트.

### D. 작업 흔적 (기본·전역 공통, 정보성)

```bash
# base 는 origin/dev (이 레포의 PR base). origin/main 이 아니다.
# .claude/** (커맨드 문서 등 툴링)는 제외 — 스킬 자기 문서의 "TODO" 단어가 오탐되므로.
git diff origin/dev...HEAD -- ':(exclude).claude/**' | grep -nE '^\+[^+].*(TODO|FIXME)'
```

- 이번 브랜치에서 새로 추가된 TODO/FIXME → 리포트만.
- **base 는 `origin/dev`** 다 — 이 레포 PR base 가 dev 이므로. `origin/main` 으로 비교하면 엉뚱한 diff 가 나온다.
- `.claude/**` 는 pathspec 으로 제외한다 — 이 스킬 문서처럼 산문에 "TODO"·"FIXME" 단어가 들어가면 코드 TODO 가 아닌데도 잡히는 오탐을 막기 위함.
- 현재 브랜치 PR에 미해결 CodeRabbit/사람 리뷰 thread가 있으면 건수만 알리고 `/coderabbit` 로 처리하라고 안내한다(여기서 처리하지 않는다).

## 액션 처리 (AskUserQuestion)

- 위에서 **액션** 으로 표시된 항목 중 실제로 건수가 있는 것만 모아 **한 번의** `AskUserQuestion` 에 질문으로 담는다(질문 최대 4개). 건수 0인 항목은 질문에 넣지 않는다.
- 한 항목에 대상이 여러 개면(예: 미삭제 브랜치 여러 개) `multiSelect: true` 로 어떤 걸 처리할지 고르게 한다.
- **액션 가능한 항목이 하나도 없으면 `AskUserQuestion` 을 호출하지 않는다.**
- 선택 결과대로만 실행하고, 각 실행 후 결과(커밋 해시 / 푸시 결과 / 삭제된 브랜치명)를 짧게 보고한다.
- 정리 셀렉트는 커밋/푸시/브랜치 삭제/prune 까지다. **워크트리 정리(close)는 셀렉트에 넣지 않는다** — close 는 `/session-close` 전용이다.

## 최종 리포트 형식

점검이 끝나면(액션을 했다면 그 이후 상태로) 다음 형식으로 요약한다. 이모지 없이 굵게·불릿으로. 전역(`all`) 모드일 때만 다른 워크트리/브랜치 줄을 포함한다.

```
## 세션 마무리 점검 (<기본 | 전역(all)>)

브랜치: <현재 브랜치>

**정리 안 됨 (주의 <M>건)**
- (현재) uncommitted <n>건 — staged <a> / unstaged <b> / untracked <c>
- 미푸시: <branch> ahead <n>
- 머지됨(현재 브랜치): <branch> (upstream [gone]) — 정리는 /session-close
- CI 실패: PR #<num> (정보성)
- [all] (워크트리 <name>) dirty <n>건
- [all] 미삭제(머지됨): 로컬 브랜치 <branch> (upstream [gone])

**안전**
- stash 없음 / 새 TODO 없음
```

주의 0건이면 맨 위에 **"닫아도 안전합니다"** 한 줄을 명확히 띄운다.

## 절차

1. 인자에 `all` 이 포함됐는지로 모드를 정한다 (없으면 기본).
2. read-only로 상태를 수집한다 — 기본 모드는 A·B·D, 전역(`all`) 모드는 A·B·C·D.
3. 최종 리포트 형식으로 현재 상태를 먼저 보여준다.
4. 액션 가능한 항목이 있으면 `AskUserQuestion` 으로 처리 여부를 받고, 고른 것만 실행한다(없으면 이 단계 생략).
5. 실행 결과를 반영해 한 줄 결론(닫아도 안전 / 남은 주의 N건)으로 마무리한다.
6. **close 는 하지 않는다 — 안내만 한다.** 결론이 "닫아도 안전"이고, 현재 워크트리(`CUR`)가 메인 체크아웃이 아니면서 머지+clean 인 **제거 대상 sub-worktree** 일 때, 사용자에게 **안내만** 한다:
   - "이 워크트리를 정리하고 마무리하려면 **`/session-close`** 를 입력하세요."
   - **`/session-close` 를 `Skill` 도구로 자동 호출하지 않는다.** close 는 사용자가 직접 호출할 때만 일어난다.
   - 현재가 메인 체크아웃이거나 제거 대상 워크트리가 없으면(마무리할 게 없으면) 이 안내를 생략한다.
   - 결론이 "닫아도 안전"이 아니면(주의 N건 남음) 안내하지 않는다 — 먼저 정리가 우선이다.

owner/repo는 `depromeet/PIKI-Server` 고정. 레포가 바뀌면 갱신한다.

$ARGUMENTS
