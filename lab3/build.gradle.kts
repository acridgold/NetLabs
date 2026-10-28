plugins {
    kotlin("jvm") version "2.0.21"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("APICalling.MainKt")
    applicationDefaultJvmArgs = listOf("-Dfile.encoding=UTF-8", "-Dswing.aatext=true", "-Dawt.useSystemAAFontSettings=on")
}
