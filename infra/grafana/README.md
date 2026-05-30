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

증상 → 원인 → 로그 순서로 배치한다(위에서 아래로):

1. **한눈 건강(stat, 임계 색상)** — 앱 up · 5xx 에러율 · p99 지연 · JVM heap 사용률 · 호스트 메모리 사용률 · swap 사용량. 위험하면 노랑/빨강.
2. **트래픽(RED)** — 요청률(status별) · 5xx 요청률(uri별) · 지연 p50/95/99.
3. **JVM 메모리·GC** — heap used/max · nonheap · GC pause p99·빈도. (heap 256m·906Mi 박스라 1순위 리스크)
4. **호스트(node_exporter)** — 메모리 used/total · swap · 디스크 여유. OOM-kill·swap 폭증을 OS 레벨로 잡는다.
5. **의존성** — 502율(Gemini 등 외부 실패) · executor active/queued · HikariCP active/idle/pending.
6. **리소스** — 앱 process/system CPU · JVM 스레드 · 호스트 CPU.
7. **로그** — 에러/경고 필터 로그 + 전체 로그(`team3-blue`/`team3-green`).

앱 메트릭은 `application="PIKI"` 라벨로 필터한다(Alloy 의 prometheus.scrape 가 붙임).
blue-green 이라 한 시점에 한 슬롯만 활성이며 `instance` 라벨로 blue/green 을 구분한다.
호스트 메트릭(`node_*`)은 application 라벨이 없다 — `prometheus.exporter.unix` 가 별도로 내보낸다.

### 알려진 한계 (계측이 더 필요한 것)
- **Gemini(LLM) 호출 자체**는 직접 계측이 없어 `502` 서버 응답으로만 간접 관측된다. 호출 latency/실패율을 직접 보려면 `GeminiHttpClient` 의 RestClient 를 자동설정 builder/`@Observed` 로 바꿔야 한다.
- **S3·Redis** 호출도 자동 계측이 없다(필요 시 `@Timed`/MeterRegistry 추가).

## 갱신

GC 콘솔에서 대시보드를 수정한 뒤 **Dashboard settings → JSON Model** 을 복사해 이 파일을 덮어쓰면
변경이 버전관리된다. (반대로 이 파일을 고쳐 다시 import 해도 된다.)
