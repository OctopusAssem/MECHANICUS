plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    jvmToolchain(21)
    androidTarget()
    jvm("desktop")

    sourceSets {
        val commonMain by getting
        // Code that both the Android and the desktop targets can share, because
        // both run on the JVM (java.io, java.util, java.text are available here).
        val jvmShared by creating { dependsOn(commonMain) }
        val androidMain by getting { dependsOn(jvmShared) }
        val desktopMain by getting { dependsOn(jvmShared) }
    }
}

android {
    namespace = "com.assem.mechanicus.shared"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
