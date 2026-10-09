import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.xerial:sqlite-jdbc:3.53.4.0")
}

compose.desktop {
    application {
        mainClass = "com.assem.mechanicus.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "MECHANICUS"
            packageVersion = "1.0.5"
            description = "MECHANICUS - YOUR AUTO REPAIR ASSISTANT"
            windows {
                // Desktop shortcut + Start Menu entry, and our own app icon
                // (octopus mechanic) instead of the default Java icon.
                shortcut = true
                menu = true
                dirChooser = true
                perUserInstall = true
                upgradeUuid = "8F3B2C41-7A5D-4E90-B6C1-2D4E6F8A0B13"
                iconFile.set(project.file("icon.ico"))
            }
        }
    }
}
