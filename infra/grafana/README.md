# Grafana 대시보드

`dashboard.json` 은 PIKI 앱 개요 대시보드다 — 주요 메트릭과 앱 로그를 한 화면에 모은다.
Grafana Cloud 가 호스팅하는 Grafana 이므로 셀프호스팅 provisioning 이 아니라 **수동 import** 한다.

## Import 방법

1. Grafana Cloud(`piki.grafana.net`) → **Dashboards → New → Import**
2. `dashboard.json` 내용을 붙여넣기(또는 파일 업로드)
3. 데이터소스 매핑:
   - **DS_PROM** → `grafanacloud-piki-prom` (Prometheus)
   - **DS_LOKI** → `grafanacloud-piki-logs` (Loki)
4. **Import**

## 패널

HTTP 요청률·p95 지연, JVM heap 메모리·라이브 스레드·GC pause, HikariCP 커넥션풀,
executor 스레드풀, process/system CPU, 그리고 `team3-blue`/`team3-green` 앱 로그.

모든 메트릭은 `application="PIKI"` 라벨로 필터한다(Alloy 의 prometheus.scrape 가 붙임).
blue-green 이라 한 시점에 한 슬롯만 활성이며, `instance` 라벨로 blue/green 을 구분한다.

## 갱신

GC 콘솔에서 대시보드를 수정한 뒤 **Dashboard settings → JSON Model** 을 복사해 이 파일을 덮어쓰면
변경이 버전관리된다. (반대로 이 파일을 고쳐 다시 import 해도 된다.)
