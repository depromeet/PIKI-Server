output "vpc_id" {
  description = "생성된 VPC ID"
  value       = aws_vpc.main.id
}

output "public_subnet_id" {
  description = "EC2 가 위치한 퍼블릭 서브넷 ID"
  value       = aws_subnet.public.id
}

output "private_subnet_ids" {
  description = "RDS DB Subnet Group 에 사용된 프라이빗 서브넷 ID 목록"
  value       = aws_subnet.private[*].id
}

output "ec2_public_ip" {
  description = "EC2 에 연결된 Elastic IP (고정)"
  value       = aws_eip.app.public_ip
}

output "ec2_public_dns" {
  description = "EC2 퍼블릭 DNS (EIP 기준)"
  value       = aws_eip.app.public_dns
}

output "rds_endpoint" {
  description = "RDS 엔드포인트 (host:port)"
  value       = aws_db_instance.mysql.endpoint
}

output "rds_address" {
  description = "RDS 호스트"
  value       = aws_db_instance.mysql.address
}

output "image_bucket_name" {
  description = "크롭 상품 이미지 저장 버킷명 (앱 S3_BUCKET)"
  value       = aws_s3_bucket.images.id
}

output "image_public_base_url" {
  description = "이미지 버킷 공개 베이스 URL (앱 S3_PUBLIC_BASE_URL)"
  value       = "https://${aws_s3_bucket.images.bucket_regional_domain_name}"
}
