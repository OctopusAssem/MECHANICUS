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
            packageVersion = "1.0.0"
            description = "MECHANICUS - YOUR AUTO REPAIR ASSISTANT"
        }
    }
}
