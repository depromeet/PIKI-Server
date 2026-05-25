세션 작업을 마무리한다 — `/session-check` 로 "닫아도 안전"이 확인된 뒤, 현재 작업 워크트리를 제거하고 그 브랜치를 정리한 다음, 사용자에게 `/clear` 입력을 안내한다. `/session-check` 의 짝(teardown) 스킬.

## 언제 쓰나

- `/session-check` 결과가 "닫아도 안전"이고, 지금 작업하던 워크트리를 정리하고 세션을 끝내려 할 때.
- 보통 `/session-check` 가 끝에 "지금 마무리할까요?"로 물어 자동 호출하지만, 단독 호출도 된다.

## 원칙

- **session-check 통과 상태를 전제하되, 자체 재확인한다.** 파괴적 동작(워크트리 삭제) 전에 안전 조건(머지 + clean)을 다시 확인하고, 통과 못 하면 **거부**하고 이유를 알린다.
- **현재 작업 워크트리 1개만 건드린다.** 다른 워크트리·다른 브랜치는 절대 손대지 않는다 — 머지된 stale 일괄 정리는 별도 세션 몫이다.
- **미머지·dirty 면 거부.** uncommitted 가 있거나 브랜치가 아직 머지 안 됐으면 워크트리를 지우지 않는다(작업 유실 방지).
- **`/clear` 는 자동 호출할 수 없다.** 스킬은 빌트인 슬래시 커맨드를 부를 수단이 없다(SlashCommand 도구 없음, 훅도 불가). 그래서 마지막에 사용자에게 `/clear` 입력을 안내하는 것으로 끝낸다.
- **이모지·체크기호 금지.** 굵게·불릿으로만 표시한다.

## 대상 식별

```bash
CUR=$(git rev-parse --show-toplevel)                                       # 지금 세션이 선 워크트리
MAIN=$(git worktree list --porcelain | awk '/^worktree /{print $2; exit}') # 메인 체크아웃 (첫 항목)
BR=$(git rev-parse --abbrev-ref HEAD)                                       # 현재 브랜치
```

- `CUR == MAIN` → 지금은 메인 체크아웃이라 **제거할 작업 워크트리가 없다.** 워크트리/브랜치 삭제는 건너뛰고 `### 3` prune + `### 4` 안내만 한다.
- `CUR != MAIN` → `CUR` 이 정리 대상 sub-worktree. `### 1` 로 진행.

## 절차

### 1. 안전 재확인 (CUR != MAIN 일 때)

```bash
git -C "$CUR" status --porcelain          # 비어 있어야 함 (clean)
git for-each-ref --format='%(upstream:track)' "refs/heads/$BR"             # [gone] 이면 머지+원격삭제
gh pr list --state merged --search "head:$BR" --json number --jq 'length'  # >0 이면 머지됨
```

- **dirty** (status 비어있지 않음) → **거부.** "uncommitted N건 — 먼저 `/commit` 하거나 `/session-check` 로 점검하세요" 안내 후 중단.
- **미머지** (`[gone]` 아니고 머지 PR 도 0건) → **거부.** "브랜치 `$BR` 가 아직 머지 안 됨 — PR 머지 후 다시 호출하세요" 안내 후 중단.
- 둘 다 통과(clean + 머지됨) → `### 2`.

### 2. 워크트리 제거 + 브랜치 삭제 (CUR != MAIN, 안전 통과 시)

자기 cwd 는 자기가 못 지운다. **메인 체크아웃에서 `git -C "$MAIN"` 로 처리**하고, 이것이 **이 스킬의 마지막 bash 호출**이어야 한다 — `$CUR` 을 지우면 현재 cwd 가 사라져 이후 cwd 의존 명령이 깨지기 때문이다(세 명령 모두 `-C "$MAIN"` 이라 cwd 비의존).

```bash
git -C "$MAIN" worktree remove "$CUR"     # clean 이라 안전 (dirty면 git이 한 번 더 거부)
git -C "$MAIN" branch -D "$BR"            # squash/rebase 머지는 ancestor 가 아니라 -d 가 거부 → -D
git -C "$MAIN" worktree prune
```

그 다음 `### 4` 안내로 끝낸다 (추가 bash 호출 없음).

### 3. prune (CUR == MAIN 일 때)

제거할 워크트리가 없으니 stale 워크트리 admin 파일만 정리한다:

```bash
git worktree prune
```

### 4. /clear 안내

모든 정리가 끝나면, 스킬이 자동으로 못 하므로 사용자에게 **명확히** 안내하고 끝낸다:

> 마무리 완료 — 제거한 워크트리 / 삭제한 브랜치를 한 줄로 보고한 뒤, "컨텍스트를 비우려면 이제 **`/clear`** 를 입력하세요." 라고 안내한다.

`/clear` 를 스킬이 직접 호출하려 시도하지 않는다(불가능하다).

owner/repo는 `depromeet/PIKI-Server` 고정. 레포가 바뀌면 갱신한다.

$ARGUMENTS
