import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    jvmToolchain(21)
    android {
        namespace = "com.assem.mechanicus.shared"
        compileSdk = 37
        minSdk = 26
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }
    jvm("desktop")

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("androidx.sqlite:sqlite-bundled:2.7.1")
            }
        }
        // Code that both the Android and the desktop targets can share, because
        // both run on the JVM (java.io, java.util, java.text are available here).
        val jvmShared by creating { dependsOn(commonMain) }
        val androidMain by getting { dependsOn(jvmShared) }
        val desktopMain by getting { dependsOn(jvmShared) }
    }
}
