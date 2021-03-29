import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("java")
    id("com.github.johnrengelman.shadow") version "6.1.0"
    id("org.openjfx.javafxplugin") version "0.0.9"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.4.10"
    kotlin("jvm") version "1.4.20"
}

group = "co.proxychecker"
version = "1.1.1"
java.sourceCompatibility = JavaVersion.VERSION_11

repositories {
    mavenCentral()
    jcenter()
    maven { url = uri("https://jitpack.io") }
    maven { url = uri("https://plugins.gradle.org/m2/") }
    maven { url = uri("https://repository.mulesoft.org/nexus/content/repositories/public/") }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.0.1")
    implementation("no.tornado:tornadofx:1.7.20")
    implementation("com.google.code.gson:gson:2.8.5")

    implementation("org.openjfx:javafx-controls:${javafx.version}:win")
    implementation("org.openjfx:javafx-controls:${javafx.version}:linux")
    implementation("org.openjfx:javafx-controls:${javafx.version}:mac")

    implementation("org.openjfx:javafx-fxml:${javafx.version}:win")
    implementation("org.openjfx:javafx-fxml:${javafx.version}:linux")
    implementation("org.openjfx:javafx-fxml:${javafx.version}:mac")

    implementation("org.openjfx:javafx-graphics:${javafx.version}:win")
    implementation("org.openjfx:javafx-graphics:${javafx.version}:linux")
    implementation("org.openjfx:javafx-graphics:${javafx.version}:mac")
}

javafx {
    version = "11.0.2"
    modules("javafx.controls", "javafx.fxml", "javafx.graphics")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "11"
    }
}

tasks.shadowJar{
    archiveName = "ProxyChecker-${version}.${extension}"
    manifest {
        attributes (
            "Main-Class" to "co.proxychecker.ProxyChecker.startup.StartHere"
        )
    }
}