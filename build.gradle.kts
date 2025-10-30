import java.time.Duration

plugins {
    id("java")
    id("org.springframework.boot") version "3.2.0"
    id("io.spring.dependency-management") version "1.1.4"
}

group = "org.amway"
version = "1.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
    google()
    gradlePluginPortal()
}

// ============================================
// 全局排除 slf4j-simple，避免日誌框架衝突
// ============================================
configurations.all {
    exclude(group = "org.slf4j", module = "slf4j-simple")
}

dependencies {
    // ============================================
    // Spring Boot Core
    // ============================================
    implementation("org.springframework.boot:spring-boot:3.2.0")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // ============================================
    // JWT Authentication
    // ============================================
    implementation("io.jsonwebtoken:jjwt-api:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.11.5")

    // ============================================
    // Redis & Distributed Lock
    // ============================================
    implementation("org.redisson:redisson-spring-boot-starter:3.24.3") {
        // 排除 redisson 中可能的日誌依賴
        exclude(group = "org.slf4j", module = "slf4j-simple")
    }

    // ============================================
    // Database
    // ============================================
    implementation("com.mysql:mysql-connector-j:8.1.0")

    // ============================================
    // API Documentation
    // ============================================
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.3.0")

    // ============================================
    // Rate Limiting
    // ============================================
    implementation("com.google.guava:guava:32.1.3-jre")

    // ============================================
    // Lombok
    // ============================================
    compileOnly("org.projectlombok:lombok:1.18.30")
    annotationProcessor("org.projectlombok:lombok:1.18.30")
    testCompileOnly("org.projectlombok:lombok:1.18.30")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.30")

    // ============================================
    // Test Dependencies
    // ============================================
    testImplementation(platform("org.junit:junit-bom:5.10.0"))

    // JUnit 5
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Spring Boot Test
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
        exclude(group = "org.slf4j", module = "slf4j-simple")
    }

    // Spring Security Test
    testImplementation("org.springframework.security:spring-security-test")

    // Mockito
    testImplementation("org.mockito:mockito-core:5.5.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.5.0")

    // H2 In-Memory Database for Testing
    testImplementation("com.h2database:h2")

    // Embedded Redis for Testing (可選)
    testImplementation("it.ozimov:embedded-redis:0.7.3") {
        exclude(group = "org.slf4j", module = "slf4j-simple")
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform()

    // 測試環境配置
    systemProperty("spring.profiles.active", "test")

    // 增加測試超時時間
    timeout.set(Duration.ofSeconds(300))

    // 並行執行測試
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).takeIf { it > 0 } ?: 1
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("${project.name}-${project.version}.jar")
}

tasks.named<Jar>("jar") {
    enabled = true
    archiveFileName.set("${project.name}-${project.version}-plain.jar")
}
