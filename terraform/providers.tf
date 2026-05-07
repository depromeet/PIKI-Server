terraform {
  required_version = ">= 1.10"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  backend "s3" {
    bucket       = "piki-tfstate-250758375457"
    key          = "terraform.tfstate"
    region       = "ap-northeast-2"
    encrypt      = true
    use_lockfile = true
  }
}

# -----------------------------------------------------------------------------
# AWS Provider
#
# 자격증명은 환경변수(AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY)로 주입한다.
# 로컬 ~/.aws/credentials 에 영구 저장하지 않는 정책.
# 키는 작업 시점에 콘솔에서 생성 → export → 작업 종료 후 삭제.
# -----------------------------------------------------------------------------
provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = var.project
      Environment = var.environment
      ManagedBy   = "terraform"
    }
  }
}
