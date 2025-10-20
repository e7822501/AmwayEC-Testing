plugins {
    id("java")
    id("org.springframework.boot") version "3.2.0"
    id("io.spring.dependency-management") version "1.1.3"
}

group = "org.amway"
version = "1.0-SNAPSHOT"
//sourceCompatibility = '17' // Spring Boot 3.x 要求 Java 17+
//targetCompatibility = '17'

repositories {
    mavenCentral()
    google()
    gradlePluginPortal()
}

dependencies {
    /* TEST Dependencies*/
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    // 單元測試
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
    testImplementation("org.mockito:mockito-core:5.5.0")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // https://mvnrepository.com/artifact/org.springframework.boot/spring-boot
    implementation("org.springframework.boot:spring-boot:3.2.0")
    // Spring Boot Web
    implementation("org.springframework.boot:spring-boot-starter-web")

    // Spring Security
    implementation("org.springframework.boot:spring-boot-starter-security")

    // JWT
    implementation("io.jsonwebtoken:jjwt-api:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.11.5")

    // Spring Data JPA + Hibernate
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    // Redis + Redisson
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.redisson:redisson-spring-boot-starter:3.21.3")

    // SpringDoc OpenAPI 3
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.3.0")

    // Lombok
    compileOnly("org.projectlombok:lombok:1.18.30")
    annotationProcessor("org.projectlombok:lombok:1.18.30")

    // MySQL
    implementation("com.mysql:mysql-connector-j:8.1.0")

//    runtimeOnly("com.mysql:mysql-connector-j:8.1.0")
}

tasks.test {
    useJUnitPlatform()
}