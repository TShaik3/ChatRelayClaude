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
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Spring Boot's own dependency-management BOM pins org.testcontainers:* to an older release
// (1.19.8) than what's actually needed here -- that old release's docker-java client sends a
// hardcoded low API version during its initial Docker daemon compatibility probe, which colima's
// Docker Engine (minimum supported API 1.40) rejects outright. A plain Gradle platform() import
// isn't enough to override Spring's own BOM constraints; this has to go through
// io.spring.dependency-management's own import mechanism, whose last-declared import wins.
dependencyManagement {
    imports {
        mavenBom("org.testcontainers:testcontainers-bom:1.21.4")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()

    // Testcontainers needs to find the Docker daemon. Normal auto-detection (DOCKER_HOST env var,
    // ~/.docker/config.json's active context, /var/run/docker.sock) covers Docker Desktop and CI
    // runners out of the box. It does NOT reliably cover colima (a CLI-only Docker runtime used
    // here since neither Docker nor Docker Desktop is installed on this machine) -- the Gradle
    // daemon caches its environment at daemon startup, so setting DOCKER_HOST in the shell doesn't
    // reach an already-running daemon's forked test JVM. Only fall back to colima's socket when
    // it actually exists and nothing else already specifies DOCKER_HOST, so this is a no-op on any
    // machine with a normal Docker install.
    val dockerHostAlreadySet = System.getenv("DOCKER_HOST") != null
    val colimaSocket = file(System.getProperty("user.home") + "/.colima/default/docker.sock")
    if (!dockerHostAlreadySet && colimaSocket.exists()) {
        environment("DOCKER_HOST", "unix://${colimaSocket.absolutePath}")
        // The path above is a host-side forward of the real socket colima's dockerd listens on
        // inside its Linux VM -- valid for the API client, but not as a bind-mount *source* for
        // sidecar containers (Ryuk, the resource reaper) since the daemon doesn't see that path
        // in its own filesystem. This tells Testcontainers to bind-mount the canonical in-VM path
        // instead when wiring the socket into those containers.
        environment("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE", "/var/run/docker.sock")
    }
}

springBoot {
    mainClass.set("app.BackendApplication")
}
