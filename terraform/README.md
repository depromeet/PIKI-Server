# Terraform 운영 가이드

본 디렉터리의 인프라 코드를 만지기 전에 한 번 읽어주세요.

## State 의 역할

```
코드 (.tf 파일)  =  의도        (what should exist)
AWS              =  실제 리소스  (what actually exists)
state            =  매핑         (terraform 이 자기가 만든 리소스 추적)
```

state 가 없으면 terraform 은 AWS 에 떠있는 리소스를 "내가 모르는 것" 으로 취급해 다음 apply 때 새로 만들려 든다. 즉 **state = terraform 의 기억**이며, 잃으면 안 된다.

## 우리가 채택한 방식 — S3 remote backend + native lock

| 결정 | 값 | 이유 |
|---|---|---|
| state 위치 | `s3://piki-tfstate-250758375457/terraform.tfstate` | 팀 공유 + 단일 진실 원천 |
| 락 메커니즘 | S3 native (`use_lockfile = true`) | Terraform 1.10+ 의 S3 conditional PUT 락. DynamoDB 추가 운영 부담 0 |
| 버킷 보안 | versioning ON / 퍼블릭 차단 / SSE-S3 | 롤백 가능, 외부 노출 차단 |
| 접근 권한 | IAM 그룹 `PIKI-Infra` (BE 3 명) | 외부도, 다른 그룹도 state 못 봄 |

대안과 비교:

| 방식 | 채택 안 한 이유 |
|---|---|
| Local state | 팀 협업 불가 |
| S3 + DynamoDB lock | DynamoDB 추가 운영 부담. native lock 으로 같은 효과 가능 |
| Terraform Cloud | 개인 프로젝트엔 오버 |
| Atlantis / Spacelift | 자체 호스팅 운영 부담 |

## 권한 모델

| 주체 | 가능한 일 |
|---|---|
| `PIKI-Infra` 그룹 (조재중 / qkrdmswl / parksevin98) | `terraform plan` / state 읽기·쓰기 |
| `AdministratorAccess` 부여된 사용자 (parksevin98) | 모든 AWS 리소스 변경 (= apply 가능) |
| 그 외 | state 접근 자체 불가 |

→ **계획 / 검토는 BE 3 명 모두, apply 실행은 1 명** — PR 리뷰 비유로 하면 "다 같이 보고 한 명이 머지" 형태.

## 일상 워크플로우

### 0. (최초 1 회) AWS CLI 자격증명 export

본인 IAM 사용자에서 액세스 키 발급 → 작업 세션 동안만 export.

```bash
export AWS_ACCESS_KEY_ID="AKIA..."
export AWS_SECRET_ACCESS_KEY="..."
export AWS_REGION="ap-northeast-2"
```

`~/.aws/credentials` 같은 영구 파일에 저장하지 말 것. **작업 종료 후 콘솔에서 키 비활성화 / 삭제.**

### 1. 작업 시작

```bash
cd terraform
terraform init   # 첫 실행 시 또는 backend 설정 변경 시. provider 다운 + S3 backend 연결
terraform plan   # state 읽고 AWS 와 비교, 변경 미리 보기
```

### 2. 적용 (admin 만)

```bash
terraform apply  # 락 잠그고 실제 AWS 변경 + state 업데이트
```

### 3. 작업 종료

```bash
unset AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY
# 콘솔에서 액세스 키 비활성화 / 삭제
```

## 동시성 / 사고 처리

### 락 (자동)

두 사람이 동시에 apply 시도하면, S3 의 `.tflock` conditional PUT 으로 두 번째 apply 가 즉시 실패. 슬랙으로 "나 apply 한다" 외치지 않아도 자동 직렬화.

### 락 풀기 (드물게)

terraform 이 비정상 종료되어 락이 안 풀린 채 남으면:

```bash
terraform force-unlock <LOCK-ID>
```

상황 파악한 뒤에만 사용. 진짜 누가 작업 중인 거면 절대 풀지 말 것.

### 롤백

state 가 망가지거나 잘못된 apply 가 일어났을 때 — S3 versioning 으로 복구:

```bash
aws s3api list-object-versions --bucket piki-tfstate-250758375457 --prefix terraform.tfstate
aws s3api get-object --bucket piki-tfstate-250758375457 --key terraform.tfstate \
  --version-id <VERSION_ID> terraform.tfstate.recovery
terraform state push terraform.tfstate.recovery
```

S3 기본 30 일+ 보존이라 시간 거꾸로 돌릴 수 있음.

## 왜 state 만 gitignore 인가

| 파일 | git? | 이유 |
|---|---|---|
| `*.tf` | ✅ | 코드 — 팀 공유 |
| `.terraform.lock.hcl` | ✅ | provider 버전 락. 팀 일관성 |
| `terraform.tfstate` | ❌ → S3 | 비밀값 포함 + apply 마다 변경 + 진실 원천 1 개여야 |
| `.terraform/` | ❌ | provider 플러그인 (~150MB, OS/arch별). `init` 으로 재생성 |
| `*.tfvars` | ❌ | secret. 전용 저장소 (env var / Secrets Manager) |
| `*.tfplan` | ❌ | 일회성 |

→ ignore 된 것 중 **state 만이 "공유는 필요하지만 git 에 두면 안 되는" 유일한 파일**. 그래서 별도 저장소 (S3) 가 필요.

## 절대 하지 말 것

- ❌ S3 객체 직접 편집 / 삭제 (콘솔, CLI 다)
- ❌ 로컬 `terraform.tfstate` 파일을 커밋
- ❌ 본인 `~/.aws/credentials` 에 액세스 키 영구 저장
- ❌ 검토 없이 `terraform apply` (반드시 plan 먼저)
- ❌ drift 발견 시 무작정 apply (별도 이슈로 검토)
