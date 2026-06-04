plugins {
    kotlin("jvm") version "2.3.20"
    kotlin("plugin.spring") version "2.3.20"
    kotlin("plugin.jpa") version "2.3.20"
    id("org.springframework.boot") version "4.0.5"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.2"
}

ktlint {
    version.set("1.4.1")
}

group = "com.depromeet"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

// Spring Boot 4.0.5 의 platform 은 testcontainers 코어 2.0.x 만 포함하고
// junit-jupiter / mysql 모듈은 1.21.x 라인이 최신이라 BOM 불일치가 난다.
// 모듈이 따라올 때까지 testcontainers BOM 을 1.21.4 로 명시 고정.
val testcontainersVersion = "1.21.4"
val awsSdkVersion = "2.44.11"

dependencyManagement {
    imports {
        mavenBom("org.testcontainers:testcontainers-bom:$testcontainersVersion")
        mavenBom("software.amazon.awssdk:bom:$awsSdkVersion")
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    // 개발 서버 전용 admin 챗봇의 SSR 페이지(admin.enabled 로 게이팅). 버전은 Spring Boot 4.0.5 BOM 이 관리.
    // CSRF hidden field 는 thymeleaf-spring 의 RequestDataValueProcessor 가 자동 주입하므로
    // thymeleaf-extras-springsecurity 는 첫 단계에선 불필요(sec: dialect 미사용).
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("tools.jackson.module:jackson-module-kotlin")

    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-mysql")
    runtimeOnly("com.mysql:mysql-connector-j")

    // Spring Boot 4.0.5 / Jackson 3 호환 라인. Swagger UI 없이 /v3/api-docs 만 제공 (UI는 Stoplight Elements 사용)
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-api:3.0.3")

    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // 알림 템플릿 플레이스홀더 치환 (StringSubstitutor). Spring Boot BOM 미관리라 버전 명시 (Maven Central 최신 안정).
    implementation("org.apache.commons:commons-text:1.15.0")

    // 옵저버빌리티: Actuator 로 health/metrics 노출 + Micrometer Prometheus 레지스트리로
    // /actuator/prometheus 텍스트 포맷 export. 수집은 EC2 의 Grafana Alloy → Grafana Cloud (별도 PR).
    // 버전은 Spring Boot BOM 이 관리한다.
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")

    // 분산 추적: 요청마다 traceId/spanId 생성 → MDC·로그 correlation 패턴·응답 body(traceId)에 노출.
    // Grafana Loki 에서 traceId 로 한 요청의 전체 로그를 추적한다. 버전은 Spring Boot BOM 이 관리.
    //
    // Spring Boot 4 는 autoconfigure 를 모듈로 쪼갰다. tracing 코어(spring-boot-micrometer-tracing)만 있으면
    // NoopTracerAutoConfiguration 이 NOOP Tracer 를 등록해 traceId 가 비어 버린다. 둘 다 있어야 실제 Tracer 가 뜬다:
    //  - spring-boot-micrometer-tracing-brave: Spring Boot 의 Brave autoconfig(@ConditionalOnClass(brave...))
    //  - micrometer-tracing-bridge-brave: micrometer Tracer ↔ Brave 구현 + brave core(autoconfig 의 ConditionalOnClass 대상)
    // Zipkin 등 외부 export 는 하지 않으므로 reporter(zipkin-reporter-brave)는 넣지 않는다 — traceId 생성·전파만 목적.
    implementation("org.springframework.boot:spring-boot-micrometer-tracing-brave")
    implementation("io.micrometer:micrometer-tracing-bridge-brave")

    // 크롭한 상품 이미지를 S3 에 업로드 (#144). 버전은 BOM 이 관리.
    implementation("software.amazon.awssdk:s3")

    // JJWT - Jackson 2.x 와 3.x 는 그룹 ID 가 달라 공존 가능 (com.fasterxml vs tools.jackson)
    val jjwtVersion = "0.13.0"
    implementation("io.jsonwebtoken:jjwt-api:$jjwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:$jjwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:$jjwtVersion")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:mysql")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    // JDK 21+부터 동적 에이전트 로딩이 제한됨. Mockito(ByteBuddy)가 런타임에 에이전트를 붙이므로 명시적 허용 필요.
    jvmArgs("-XX:+EnableDynamicAgentLoading")
}
