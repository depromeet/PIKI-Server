plugins {
	kotlin("jvm") version "2.3.20"
	kotlin("plugin.spring") version "2.3.21"
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

dependencyManagement {
	imports {
		mavenBom("org.testcontainers:testcontainers-bom:$testcontainersVersion")
	}
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("tools.jackson.module:jackson-module-kotlin")

	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.flywaydb:flyway-mysql")
	runtimeOnly("com.mysql:mysql-connector-j")

	// Spring Boot 4.0.5 / Jackson 3 호환 라인. Swagger UI 없이 /v3/api-docs 만 제공 (UI는 Stoplight Elements 사용)
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-api:3.0.3")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
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
