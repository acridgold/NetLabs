plugins {
    kotlin("jvm") version "1.9.24"
    application
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
    }
}

application {
    mainClass.set("MainKt")
}

val discoveryArgs = listOf("230.0.0.1", "5000")
// val discoveryArgs = listOf("ff02::1", "5000", "wlan0")

fun JavaExec.configureDiscovery() {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("MainKt")
    args = discoveryArgs
    systemProperty("file.encoding", "UTF-8")
    systemProperty("sun.stdout.encoding", "UTF-8")
    systemProperty("sun.stderr.encoding", "UTF-8")
    standardInput = System.`in`
}

tasks.named<JavaExec>("run") {
    configureDiscovery()
}

fun registerNodeTask(name: String) = tasks.register<JavaExec>(name) {
    group = "discovery"
    description = "Запуск узла self-discovery ($name)"
    configureDiscovery()
}

val runNode1 by registerNodeTask("runNode1")
val runNode2 by registerNodeTask("runNode2")
val runNode3 by registerNodeTask("runNode3")

tasks.jar {
    manifest {
        attributes["Main-Class"] = "MainKt"
    }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}