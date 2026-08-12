plugins {
    java
    id("org.springframework.boot") version "3.3.4"
    id("io.spring.dependency-management") version "1.1.6"
}

group = "com.chatrelay"
version = "0.1.0"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:postgresql:1.20.2")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

springBoot {
    mainClass.set("app.BackendApplication")
}

tasks.register<JavaExec>("migrateFlatFiles") {
    group = "chatrelay"
    description = "Imports legacy dbFiles/development flat files into Postgres (Migration Plan Phase 1)."
    mainClass.set("server.support.MigrateFlatFilesToPostgres")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf(project.findProperty("flatFileDir")?.toString() ?: "../dbFiles/development")
}
