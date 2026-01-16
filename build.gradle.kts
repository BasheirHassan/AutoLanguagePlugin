import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.2.0"
    id("org.jetbrains.intellij.platform") version "2.0.0"
}

group = "com.autolanguage"

// إنشاء رقم إصدار تلقائي بناءً على التاريخ والوقت
val now = LocalDateTime.now()
val versionNumber = "${now.year}.${now.monthValue.toString().padStart(2, '0')}.${now.dayOfMonth.toString().padStart(2, '0')}.${now.hour.toString().padStart(2, '0')}${now.minute.toString().padStart(2, '0')}"
version = versionNumber

// Set consistent JVM target for both Java and Kotlin
tasks.withType<JavaCompile> {
    sourceCompatibility = "21"
    targetCompatibility = "21"
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        phpstorm("2025.3.1.1") // استهداف نسخة PhpStorm 2025.3.1.1
        instrumentationTools()
    }
    implementation("net.java.dev.jna:jna:5.13.0")
    implementation("net.java.dev.jna:jna-platform:5.13.0")
}

intellijPlatform {
    pluginConfiguration {
        id = "com.autolanguage.switcher"
        name = "Auto Language Switcher"
        vendor {
            name = "AutoLanguage"
        }
        description = "Automatically switches keyboard layout based on the text context and characters."
    }
}
