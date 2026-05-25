세션 작업을 마무리한다 — `/session-check` 로 "닫아도 안전"이 확인된 뒤, 지금 들어가 있는 작업 워크트리를 (머지+clean 일 때만) 나가면서 제거하고, 사용자에게 `/clear` 입력을 안내한다. `/session-check` 의 짝(teardown) 스킬.

## 언제 쓰나

- `/session-check` 결과가 "닫아도 안전"이고, 지금 작업하던 워크트리를 정리하고 세션을 끝내려 할 때.
- 보통 `/session-check` 가 끝에 "지금 마무리할까요?"로 물어 자동 호출하지만, 단독 호출도 된다.

## 전제 — 작업 중엔 워크트리에 "들어가 있다"

이 워크플로우에서 claude 는 메인에서 시작하지만, 작업 중엔 `EnterWorktree` 로 생성·진입한 `.claude/worktrees/<task>` **안에 들어가 있다**(세션 cwd 가 그 워크트리). 그래서 마무리 = "지금 들어가 있는 그 워크트리를 나가면서 지우기"다. (`/issue` 의 `gh issue develop --checkout` 은 브랜치만 만들어 메인에서 체크아웃하는 별개 동작 — 워크트리를 만들지 않는다.)

## 원칙

- **머지+clean 일 때만 제거.** uncommitted 가 있거나 브랜치가 아직 머지 안 됐으면 **거부**한다(작업 유실 방지). session-check 통과를 전제하되 자체 재확인한다.
- **제거는 `ExitWorktree` 로 한다.** `ExitWorktree({action:"remove"})` 가 워크트리 나가기 + 디렉터리/브랜치 삭제 + cwd 복원(메인으로)을 한 번에 처리한다 — "자기가 선 폴더를 자기가 못 지운다"는 문제를 도구가 해결한다. 수동 `git -C ... worktree remove` 보다 안전·정석.
- **현재 작업 1개만.** 다른 워크트리·머지된 다른 stale 브랜치는 안 건드린다(별도 세션 몫).
- **`/clear` 는 자동 호출 불가.** 스킬은 빌트인 슬래시 커맨드를 못 부른다(claude-code-guide 확인). 정리 후 사용자에게 `/clear` 입력을 안내하는 것으로 끝낸다.
- **이모지·체크기호 금지.** 굵게·불릿으로만 표시한다.

## 절차

### 1. 지금 워크트리에 들어가 있는지 판별

```bash
CUR=$(git rev-parse --show-toplevel)
MAIN=$(git worktree list --porcelain | awk '/^worktree /{print $2; exit}')
BR=$(git rev-parse --abbrev-ref HEAD)
```

- `CUR == MAIN` → 작업 워크트리에 안 들어가 있음(메인). **제거할 게 없다** → `### 3` 안내로 바로 간다.
- `CUR != MAIN` → 지금 워크트리 안. `### 2` 로.

### 2. 안전 재확인 후 제거 (CUR != MAIN)

```bash
git status --porcelain                                                     # 비어야 함 (clean)
git for-each-ref --format='%(upstream:track)' "refs/heads/$BR"             # [gone] 이면 머지+원격삭제
gh pr list --state merged --search "head:$BR" --json number --jq 'length'  # >0 이면 머지됨
```

- **dirty** (status 비어있지 않음) → **거부.** "uncommitted N건 — 먼저 `/commit` 하거나 `/session-check`." 중단.
- **미머지** (`[gone]` 아니고 머지 PR 0건) → **거부.** "브랜치 `$BR` 미머지 — PR 머지 후 다시." 중단.
- 둘 다 통과(clean + 머지됨) → **제거한다**:
  1. `ExitWorktree({action: "remove"})` 를 호출한다.
  2. 거부하면서 변경 목록을 돌려주면 — clean 은 이미 확인했으니 그 목록은 **squash-merge 커밋(원래 브랜치 dev 의 ancestor 가 아닌 것)** 인 false alarm 이다. 이때만 `ExitWorktree({action: "remove", discard_changes: true})` 로 재호출한다. (clean·머지를 확인하기 **전에는 절대** `discard_changes: true` 를 주지 않는다.)
  3. ExitWorktree 가 **no-op** 이라고 하면(이번 세션의 `EnterWorktree` 로 들어간 워크트리가 아님 — 이전 세션·수동 생성 등), **fallback** 으로 메인에서 ref 연산한다. 이건 이 스킬의 **마지막 bash 호출**이어야 한다(`$CUR` 삭제 시 cwd 가 사라짐 — 세 명령 모두 `-C "$MAIN"` 이라 cwd 비의존):
     ```bash
     git -C "$MAIN" worktree remove "$CUR" && git -C "$MAIN" branch -D "$BR" && git -C "$MAIN" worktree prune
     ```

### 3. /clear 안내

정리 결과(나간/지운 워크트리·브랜치, 또는 "제거할 워크트리 없음")를 한 줄로 보고한 뒤, 사용자에게 **명확히** 안내하고 끝낸다:

> "컨텍스트를 비우려면 이제 **`/clear`** 를 입력하세요."

`/clear` 를 스킬이 직접 호출하려 시도하지 않는다(불가능하다).

owner/repo는 `depromeet/PIKI-Server` 고정. 레포가 바뀌면 갱신한다.

$ARGUMENTS
