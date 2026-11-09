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

tasks.test {
    useJUnitPlatform()
}
