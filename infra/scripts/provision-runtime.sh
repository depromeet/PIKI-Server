#!/usr/bin/env bash
# EC2 런타임 프로비저닝 — 멱등(idempotent). 배포 때 실행되어 박스 안 런타임 설정
# (swap / redis / nginx default 사이트)이 레포 정의 상태가 되도록 보장한다.
# 이미 있는 자원은 건드리지 않고 skip 한다 — 기존 인스턴스엔 영향 0, 새 인스턴스에서만 생성. (#217)
#
# docker 명령은 sudo 없이(ubuntu 가 docker 그룹), 시스템·nginx 는 sudo 로 — deploy.yml 기존 패턴과 동일.
set -euo pipefail

# 1) swap — 메모리 906Mi 라 1G swap 이 필수다. 없을 때만 생성하고 fstab 에 등록해 재부팅에도 유지되게 한다.
if sudo swapon --show | grep -q '/swapfile'; then
  echo "[swap] 이미 활성 — skip"
else
  echo "[swap] /swapfile 1G 생성"
  sudo fallocate -l 1G /swapfile || sudo dd if=/dev/zero of=/swapfile bs=1M count=1024
  sudo chmod 600 /swapfile
  sudo mkswap /swapfile
  sudo swapon /swapfile
  grep -q '/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
fi

# 2) redis — RefreshToken 저장소(RedisRefreshTokenStore). 없을 때만 named 볼륨(team3-redis-data)으로 기동.
#    기존 컨테이너(익명 볼륨 포함)는 보존한다 — 멱등 skip. 새 인스턴스에서만 named 볼륨으로 생성돼,
#    이후 컨테이너 재생성에도 refresh token 이 유지된다.
if docker ps -a --format '{{.Names}}' | grep -qx 'team3-redis'; then
  echo "[redis] team3-redis 이미 존재 — skip"
else
  echo "[redis] team3-redis named 볼륨으로 기동"
  docker run -d \
    --name team3-redis \
    --restart unless-stopped \
    -p 172.17.0.1:6379:6379 \
    -v team3-redis-data:/data \
    redis:7-alpine
fi

# 3) nginx default 사이트 — 불필요한 stock catch-all 노출. 있으면 제거하고 reload.
if [ -e /etc/nginx/sites-enabled/default ]; then
  echo "[nginx] sites-enabled/default 제거"
  sudo rm -f /etc/nginx/sites-enabled/default
  sudo nginx -t && sudo nginx -s reload
else
  echo "[nginx] default 없음 — skip"
fi

echo "런타임 프로비저닝 완료"
