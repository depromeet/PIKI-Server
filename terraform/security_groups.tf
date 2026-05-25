# -----------------------------------------------------------------------------
# EC2 Security Group — SSH(22) + HTTP(80) + HTTPS(443)
#
# 8080 (Spring Boot) 은 외부에 노출하지 않는다.
# EC2 내부에서 Nginx 또는 Caddy 를 reverse proxy 로 두고 TLS 를 종료한 뒤
# localhost:8080 (var.app_port) 으로 forward 하는 구조를 전제로 한다.
# Spring Boot 는 application.yml 에서 server.address=127.0.0.1 로 바인딩.
# -----------------------------------------------------------------------------
resource "aws_security_group" "ec2" {
  name        = "${local.name_prefix}-ec2-sg"
  description = "Allow SSH and HTTP/HTTPS to EC2"
  vpc_id      = aws_vpc.main.id

  ingress {
    description = "SSH"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = [var.ssh_ingress_cidr]
  }

  ingress {
    description = "HTTP (redirect to HTTPS at reverse proxy layer)"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "HTTPS"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    description = "Allow all outbound"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # SSH(22) 등 ingress 규칙은 PIKI-Infra maintainer 들이 콘솔/cli 로 직접 관리한다.
  # github actions 배포용 0.0.0.0/0:22, 팀원 SSH IP 등이 콘솔/cli 로 추가돼 있어,
  # terraform 이 ingress 를 덮어쓰면 그 규칙이 제거돼 배포·팀원 SSH 가 끊긴다.
  # 위 ingress 블록은 신규 환경의 초기 생성값일 뿐, 이후엔 콘솔이 권위를 가지므로 변경을 무시한다.
  lifecycle {
    ignore_changes = [ingress]
  }

  tags = {
    Name = "${local.name_prefix}-ec2-sg"
  }
}

# -----------------------------------------------------------------------------
# RDS Security Group — EC2 SG 로부터의 3306 만 허용
# -----------------------------------------------------------------------------
resource "aws_security_group" "rds" {
  name        = "${local.name_prefix}-rds-sg"
  description = "Allow MySQL from EC2 SG only"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "MySQL from EC2"
    from_port       = 3306
    to_port         = 3306
    protocol        = "tcp"
    security_groups = [aws_security_group.ec2.id]
  }

  # egress 블록을 의도적으로 생략한다.
  # aws_security_group 은 VPC 내 생성 시 AWS 가 자동으로 붙이는 기본 all-allow 아웃바운드 룰을
  # Terraform 이 제거해 주므로, 블록을 두지 않으면 RDS SG 는 송신이 전부 차단된 상태가 된다.
  # RDS 는 인바운드 응답만으로 동작하고 공식 backup/snapshot 은 AWS 관리 경로라 SG egress 와 무관.
  # 추후 외부 연동(S3 Data Export 등) 이 필요하면 그때 최소 대상만 명시적으로 열 것.

  tags = {
    Name = "${local.name_prefix}-rds-sg"
  }
}
