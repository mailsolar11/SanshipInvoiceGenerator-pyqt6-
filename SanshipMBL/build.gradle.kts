import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "1.9.21"
    id("org.jetbrains.compose") version "1.5.11"
}

group = "com.sanship"
version = "1.0.0"

repositories {
    mavenCentral()
    google()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    implementation(compose.desktop.currentOs)

    // PDF Generation
    implementation("org.apache.pdfbox:pdfbox:2.0.29")

    // Excel Handling
    implementation("org.apache.poi:poi-ooxml:5.2.5")

    // Database (SQLite + Exposed)
    implementation("org.xerial:sqlite-jdbc:3.45.1.0")
    implementation("org.jetbrains.exposed:exposed-core:0.50.1")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.50.1")

    // JSON Handling
    implementation("com.google.code.gson:gson:2.10.1")

    // Coroutines for Desktop (Swing)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.0")

    // Logging (Using simple to see errors, nop to silence)
    implementation("org.slf4j:slf4j-simple:2.0.9")

    // HTML to PDF (OpenHTMLToPDF)
    implementation("com.openhtmltopdf:openhtmltopdf-pdfbox:1.0.10")
    implementation("org.jsoup:jsoup:1.17.2")

    // Networking (OkHttp) & QR Code (Zxing)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.google.zxing:javase:3.5.3")
    // implementation("com.google.code.gson:gson:2.10.1") // Already Included
}

compose.desktop {
    application {
        mainClass = "com.sanship.MainKt"

        nativeDistributions {
            // Target formats: Exe is standard for Windows, Msi is good for Enterprise
            targetFormats(TargetFormat.Exe, TargetFormat.Msi)

            // --- CRITICAL FIX FOR "Failed to Launch JVM" ---
            // This forces the installer to include the java.sql module (needed for SQLite)
            // and java.naming module (often needed by JDBC drivers).
            modules("java.sql", "java.naming")

            packageName = "SanshipMBL"
            packageVersion = "1.0.0"
            description = "Sanship Logistics Software"
            vendor = "Sanship"
            copyright = "© 2026 Sanship"

            windows {
                // Ensure you have an icon at this path, or comment it out if not
                // iconFile.set(project.file("src/main/resources/icon.ico"))

                menu = true
                shortcut = true
                perUserInstall = false // Installs to Program Files
                dirChooser = true      // Lets user pick install folder
            }
        }

        // --- CRITICAL FIX: DISABLE PROGUARD ---
        // This fixes the "Unsupported version number [65.0]" error with Java 21
        buildTypes.release.proguard {
            isEnabled = false
        }
    }
}