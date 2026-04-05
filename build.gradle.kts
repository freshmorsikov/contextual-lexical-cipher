plugins {
    kotlin("jvm") version "2.3.0"
}

group = "com.github.freshmorsikov"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("ai.koog:prompt-executor-ollama-client-jvm:0.7.3")
    implementation("ai.koog:prompt-executor-openai-client-jvm:0.6.4")
    implementation("io.ktor:ktor-client-cio-jvm:3.2.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}
