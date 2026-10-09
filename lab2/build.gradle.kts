plugins {
    kotlin("jvm") version "2.1.10"
    application
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(23)
}

application {
    mainClass.set("MainKt")
}
