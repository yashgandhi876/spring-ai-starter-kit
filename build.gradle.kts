plugins {
    java
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "dev.starterkit"
version = "0.2.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

val springAiVersion: String by project

dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:$springAiVersion")
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    // Our own tables (users, usage); the pgvector table is still created by Spring AI
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    // Chat providers: the active one is chosen with spring.ai.model.chat (AI_PROVIDER)
    implementation("org.springframework.ai:spring-ai-starter-model-anthropic")
    implementation("org.springframework.ai:spring-ai-starter-model-openai")
    implementation("org.springframework.ai:spring-ai-starter-model-ollama")

    // RAG
    implementation("org.springframework.ai:spring-ai-starter-vector-store-pgvector")
    implementation("org.springframework.ai:spring-ai-pdf-document-reader")
    runtimeOnly("org.postgresql:postgresql")

    // Native macOS DNS resolver for Netty (used by the streaming HTTP client). Without it, startup on a Mac
    // logs a scary-looking ERROR and falls back to default DNS servers. Ignored on other systems.
    runtimeOnly("io.netty:netty-resolver-dns-native-macos::osx-aarch_64")
    runtimeOnly("io.netty:netty-resolver-dns-native-macos::osx-x86_64")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    // A real Postgres for repository tests, started from bundled binaries: no Docker needed
    testImplementation("io.zonky.test:embedded-postgres:2.1.1")
    testImplementation(platform("io.zonky.test.postgres:embedded-postgres-binaries-bom:17.6.0"))
    testRuntimeOnly("io.zonky.test.postgres:embedded-postgres-binaries-darwin-arm64v8") // Apple Silicon
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
