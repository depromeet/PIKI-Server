# -----------------------------------------------------------------------------
# Ubuntu 24.04 LTS (Noble Numbat) arm64 최신 AMI 조회
#
# t4g.* 는 Graviton(ARM) 이므로 반드시 arm64 AMI 를 써야 한다.
# owner 099720109477 은 Canonical 공식 계정.
#
# 이 data source 는 var.ec2_ami_id 가 null 일 때만 실행된다. 새 AMI 가 공개될
# 때마다 apply 시점에 인스턴스가 교체될 수 있으므로 부트스트랩 용도로만 사용하고,
# 최초 apply 이후에는 resolved 된 AMI ID 를 var.ec2_ami_id 로 고정해 교체를 막을 것.
#
# 또한 실제 AWS API 호출이 필요하므로 mock provider 로는 plan 시점에 실패한다.
# 계정 연결 후에만 활성화할 것.
# -----------------------------------------------------------------------------
data "aws_ami" "ubuntu_2404_arm64" {
  count       = var.ec2_ami_id == null ? 1 : 0
  most_recent = true
  owners      = ["099720109477"] # Canonical

  filter {
    name   = "name"
    values = ["ubuntu/images/hvm-ssd-gp3/ubuntu-noble-24.04-arm64-server-*"]
  }

  filter {
    name   = "architecture"
    values = ["arm64"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

# -----------------------------------------------------------------------------
# 운영(prod) EC2 — 기존 인스턴스. Name 태그만 prod 로 명시.
# var.environment 기본값(dev)이 바뀌어도 이 태그는 영향 받지 않도록 하드코딩.
# -----------------------------------------------------------------------------
resource "aws_instance" "app" {
  ami                    = var.ec2_ami_id != null ? var.ec2_ami_id : data.aws_ami.ubuntu_2404_arm64[0].id
  instance_type          = var.ec2_instance_type
  subnet_id              = aws_subnet.public.id
  availability_zone      = var.azs[0]
  vpc_security_group_ids = [aws_security_group.ec2.id]
  key_name               = "team3-SE-1"
  iam_instance_profile   = aws_iam_instance_profile.app.name

  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    http_put_response_hop_limit = 2
  }

  root_block_device {
    volume_size           = 20
    volume_type           = "gp3"
    encrypted             = true
    delete_on_termination = true
  }

  tags = {
    Name = "team3-prod-app"
  }
}

# -----------------------------------------------------------------------------
# 개발(dev) EC2 — 신규 인스턴스.
# 동일 VPC/서브넷/SG 를 공유하며, 별도 EIP 와 도메인(dev.*)으로 분리.
# -----------------------------------------------------------------------------
resource "aws_instance" "dev_app" {
  ami                    = var.ec2_ami_id != null ? var.ec2_ami_id : data.aws_ami.ubuntu_2404_arm64[0].id
  instance_type          = var.ec2_instance_type
  subnet_id              = aws_subnet.public.id
  availability_zone      = var.azs[0]
  vpc_security_group_ids = [aws_security_group.ec2.id]
  key_name               = "team3-SE-1"
  iam_instance_profile   = aws_iam_instance_profile.app.name

  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    http_put_response_hop_limit = 2
  }

  root_block_device {
    volume_size           = 20
    volume_type           = "gp3"
    encrypted             = true
    delete_on_termination = true
  }

  tags = {
    Name = "team3-dev-app"
  }
}
