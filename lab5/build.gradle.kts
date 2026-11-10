plugins {
    kotlin("jvm") version "2.1.10"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("dnsjava:dnsjava:3.6.3")
    runtimeOnly("org.slf4j:slf4j-nop:2.0.16")

    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(22)
}

application {
    mainClass.set("socks.MainKt")
    applicationName = "lab5"
}

// Аргументы программы по умолчанию для `run` (и для запуска из IDEA через Gradle).
// Переопределить: ./gradlew run -PproxyArgs="1080 1.1.1.1:53"
tasks.named<JavaExec>("run") {
    args = (findProperty("proxyArgs") as String?)?.split(" ") ?: listOf("1080")
}

tasks.test {
    useJUnitPlatform()
}
