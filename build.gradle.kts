import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.io.File

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.2.0"
    id("org.jetbrains.intellij.platform") version "2.10.5"
}

group = "com.autolanguage"

// إنشاء رقم إصدار تلقائي بناءً على التاريخ ورقم بناء متزايد
val versionFile = file("version.properties")
val now = LocalDateTime.now()
val baseVersion = "${(now.year % 100).toString().padStart(2, '0')}.${now.monthValue}"

// دالة لحساب رقم البناء
fun calculateBuildNumber(): Int {
    if (!versionFile.exists()) return 1
    
    val content = versionFile.readText().trim()
    if (content.isEmpty()) return 1
    
    // البحث عن سطر version=xxx
    val versionLine = content.lines().find { it.startsWith("version=") }
    if (versionLine == null) return 1
    
    val currentVersion = versionLine.substringAfter("version=").trim()
    if (currentVersion.isEmpty()) return 1
    
    val currentBaseVersion = currentVersion.substringBeforeLast('.')
    val currentBuildNumber = currentVersion.substringAfterLast('.').toIntOrNull() ?: 0
    
    // إذا تغير التاريخ الأساسي، نبدأ من 1، وإلا نزيد الرقم
    return if (currentBaseVersion == baseVersion) {
        currentBuildNumber + 1
    } else {
        1
    }
}

val buildNumber = calculateBuildNumber()
val versionNumber = "$baseVersion.$buildNumber"
version = versionNumber

// حفظ رقم الإصدار الجديد في الملف
tasks.register("saveVersion") {
    doLast {
        versionFile.writeText("version=$versionNumber")
    }
}

// ربط حفظ الإصدار مع مهمة البناء
tasks.named("build") {
    dependsOn("saveVersion")
}

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
        // ابتداءً من إصدار 2025.3، تم دمج كافة نسخ IntelliJ IDEA في منتج واحد موحد (Unified Product Model).
        // هذا المنتج يشمل كافة الميزات الأساسية ويوفر توافقاً شاملاً مع كافة المحررات.
        intellijIdea("2025.3.1")
    }

    // مكتبات JNA المحدثة لضمان توافقية أفضل عبر كافة المنصات والهندسات (x64, ARM64)
    implementation("net.java.dev.jna:jna:5.15.0")
    implementation("net.java.dev.jna:jna-platform:5.15.0")
}

intellijPlatform {
    pluginConfiguration {
        id = "com.autolanguage.switcher"
        name = "Auto Language"
        vendor {
            name = "AutoLanguage"
        }
        description = "Automatically switches keyboard layout based on the text context and characters."
        changeNotes = """
            <ul>
                <li>Added plugin icon support</li>
                <li>Added icons in multiple sizes (16x16, 32x32, 64x64, 128x128)</li>
                <li>Improved compatibility with all JetBrains IDEs</li>
                <li>Added support for Remote Development and JetBrains Client</li>
            </ul>
        """.trimIndent()

        // تحديد إصدارات المحررات المدعومة
        ideaVersion {
            sinceBuild = "241" // دعم من إصدار 2024.1 وما فوق
            untilBuild = ""    // لا يوجد حد أقصى للإصدار، مما يضمن العمل مع الإصدارات المستقبلية
        }
    }
}

// إضافة الموارد بما في ذلك الأيقونات
tasks.named<ProcessResources>("processResources") {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}
