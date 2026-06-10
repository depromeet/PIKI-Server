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

# dev/prod EC2 가 같은 instance role 을 공유한다(naming 충돌 회피 + 좁은 권한이라 분리 실익 작음).
# 따라서 prod·dev 두 이미지 버킷 모두에 Put/Get 을 부여한다. 각 EC2 는 자기 환경의 S3_BUCKET
# 만 실제로 쓰므로, 상대 버킷 권한은 미사용 과권한이지만 blast radius 가 작다(public-read 저민감 이미지).
data "aws_iam_policy_document" "image_bucket_rw" {
  statement {
    actions = ["s3:PutObject", "s3:GetObject"]
    resources = [
      "${aws_s3_bucket.images.arn}/*",
      "${aws_s3_bucket.dev_images.arn}/*",
    ]
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
