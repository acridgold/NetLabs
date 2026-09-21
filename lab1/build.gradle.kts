import java.io.File

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

tasks.register("runAll") {
    group = "discovery"
    description = "Запускает все три копии параллельно; вывод каждой — в build/discovery-logs/nodeN.log"

    doLast {
        val javaBin = org.gradle.internal.jvm.Jvm.current().javaExecutable.absolutePath
        val classpath = sourceSets["main"].runtimeClasspath.asPath
        val logsDir = layout.buildDirectory.dir("discovery-logs").get().asFile
        logsDir.mkdirs()

        val processes = (1..3).map { i ->
            val logFile = File(logsDir, "node$i.log")
            ProcessBuilder(
                javaBin,
                "-Dfile.encoding=UTF-8",
                "-Dsun.stdout.encoding=UTF-8",
                "-Dsun.stderr.encoding=UTF-8",
                "-cp", classpath,
                "MainKt",
                *discoveryArgs.toTypedArray()
            )
                .redirectOutput(logFile)
                .redirectErrorStream(true)
                .start()
        }

        println("Запущено узлов: ${processes.size}")
        println("Логи: ${logsDir}/node1.log, node2.log, node3.log")
        println("Смотреть в реальном времени, например: tail -f ${logsDir}/node1.log ${logsDir}/node2.log ${logsDir}/node3.log")
        println("Ctrl+C здесь остановит все три узла.")

        Runtime.getRuntime().addShutdownHook(Thread {
            processes.forEach { it.destroy() }
        })

        processes.forEach { it.waitFor() }
    }
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "MainKt"
    }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}