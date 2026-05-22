# -----------------------------------------------------------------------------
# EC2 instance role — 앱이 이미지 버킷에 업로드할 수 있는 최소 권한 (#144)
#
# 앱은 AWS SDK DefaultCredentialsProvider 로 자격증명을 얻는다. EC2 에서는 이 instance role 이
# 자동 적용되어 access key 를 코드/환경변수에 박지 않아도 된다. 권한은 이미지 버킷 객체의
# Put/Get 으로 한정한다 (state 버킷 등 다른 리소스와 분리).
# -----------------------------------------------------------------------------
data "aws_iam_policy_document" "ec2_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "app" {
  name               = "${local.name_prefix}-app-role"
  assume_role_policy = data.aws_iam_policy_document.ec2_assume.json
}

data "aws_iam_policy_document" "image_bucket_rw" {
  statement {
    actions   = ["s3:PutObject", "s3:GetObject"]
    resources = ["${aws_s3_bucket.images.arn}/*"]
  }
}

resource "aws_iam_role_policy" "app_image_bucket" {
  name   = "${local.name_prefix}-image-bucket-rw"
  role   = aws_iam_role.app.id
  policy = data.aws_iam_policy_document.image_bucket_rw.json
}

resource "aws_iam_instance_profile" "app" {
  name = "${local.name_prefix}-app-profile"
  role = aws_iam_role.app.name
}
