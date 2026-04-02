import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.io.File

plugins {
    id("java")
    // تحديث نسخ الإضافات لضمان التوافق مع بيئة تطوير 2026
    id("org.jetbrains.kotlin.jvm") version "2.2.0"
    id("org.jetbrains.intellij.platform") version "2.13.1"
}

group = "com.autolanguage"

// --- منطق توليد رقم الإصدار التلقائي ---
val versionFile = file("version.properties")
val now = LocalDateTime.now()
// تنسيق السنة والشهر (مثلاً: 26.4 لعام 2026 شهر أبريل)
val baseVersion = "${(now.year % 100).toString().padStart(2, '0')}.${now.monthValue}"

fun calculateBuildNumber(): Int {
    if (!versionFile.exists()) return 1

    val content = versionFile.readText().trim()
    if (content.isEmpty()) return 1

    val versionLine = content.lines().find { it.startsWith("version=") }
    if (versionLine == null) return 1

    val currentVersion = versionLine.substringAfter("version=").trim()
    if (currentVersion.isEmpty()) return 1

    val currentBaseVersion = currentVersion.substringBeforeLast('.')
    val currentBuildNumber = currentVersion.substringAfterLast('.').toIntOrNull() ?: 0

    // إذا تطابق الشهر والسنة، نزيد رقم البناء، وإلا نبدأ من 1
    return if (currentBaseVersion == baseVersion) {
        currentBuildNumber + 1
    } else {
        1
    }
}

val buildNumber = calculateBuildNumber()
val versionNumber = "$baseVersion.$buildNumber"
version = versionNumber

// تسجيل مهمة حفظ الإصدار
tasks.register("saveVersion") {
    group = "versioning"
    description = "Saves the auto-incremented version to properties file"
    doLast {
        versionFile.writeText("version=$versionNumber")
    }
}

// ربط تحديث الإصدار بعملية بناء الإضافة النهائية
tasks.named("buildPlugin") {
    dependsOn("saveVersion")
}

// --- إعدادات توافقية Java & Kotlin ---
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
        // استهداف إصدار 2026.1 (أحدث إصدار مستقر حالياً)
        intellijIdea("2026.1")

        // الأدوات الضرورية لتطوير الإضافات
        pluginVerifier()
        zipSigner()
    }

    // مكتبات JNA المحدثة لضمان التوافق مع كافة المنصات (x64, ARM64)
    implementation("net.java.dev.jna:jna:5.16.0")
    implementation("net.java.dev.jna:jna-platform:5.16.0")
}

// --- إعدادات إضافة IntelliJ Platform ---
intellijPlatform {
    pluginConfiguration {
        id = "com.autolanguage.switcher"
        name = "Auto Language"
        vendor {
            name = "AutoLanguage"
            email = "support@autolanguage.com" // اختياري
        }

        changeNotes = """
            <ul>
                <li>Updated compatibility for IntelliJ IDEA 2026.1</li>
                <li>Enhanced JNA libraries for better cross-platform support</li>
                <li>Fixed minor issues with Remote Development (Split Mode)</li>
                <li>Added support for modern icons and UI components</li>
            </ul>
        """.trimIndent()

        ideaVersion {
            // دعم من إصدار 2024.1 (الحد الأدنى)
            sinceBuild = "241"
            // دعم كافة إصدارات فروع 2025 و 2026
            untilBuild = "261.*"
        }
    }

    // إعدادات النشر (اختياري - يتم تفعيلها عند النشر للمتجر)
    publishing {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}

tasks.named<ProcessResources>("processResources") {
    // التعامل مع تكرار الملفات أثناء تجميع الموارد
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}