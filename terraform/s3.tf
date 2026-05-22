# -----------------------------------------------------------------------------
# 크롭한 상품 이미지 저장 버킷 (#144)
#
# 위시 상품 이미지는 민감도가 낮아 public-read 로 둔다 — item.imageUrl 에 영구 공개 URL 을
# 그대로 내려준다 (presigned/CloudFront 는 범위 밖). 공개는 bucket policy(s3:GetObject)로만
# 열고, ACL 기반 공개는 계속 차단한다(BlockPublicAcls 유지). 업로드는 EC2 instance role 만 가능.
# -----------------------------------------------------------------------------
resource "aws_s3_bucket" "images" {
  bucket = var.image_bucket_name

  tags = {
    Name = "${local.name_prefix}-images"
  }
}

# 객체 공개 읽기를 위해 BlockPublicPolicy 만 해제하고, ACL 경로 공개는 차단을 유지한다.
resource "aws_s3_bucket_public_access_block" "images" {
  bucket = aws_s3_bucket.images.id

  block_public_acls       = true
  ignore_public_acls      = true
  block_public_policy     = false
  restrict_public_buckets = false
}

# 누구나 객체를 GET 할 수 있게 한다 (PUT/DELETE 는 instance role 만 — iam.tf).
resource "aws_s3_bucket_policy" "images_public_read" {
  bucket     = aws_s3_bucket.images.id
  depends_on = [aws_s3_bucket_public_access_block.images]

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid       = "PublicReadGetObject"
      Effect    = "Allow"
      Principal = "*"
      Action    = "s3:GetObject"
      Resource  = "${aws_s3_bucket.images.arn}/*"
    }]
  })
}

resource "aws_s3_bucket_server_side_encryption_configuration" "images" {
  bucket = aws_s3_bucket.images.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}
