plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.assem.mechanicus"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.assem.mechanicus"
        minSdk = 26
        targetSdk = 37
        versionCode = 25
        versionName = "0.14.7"
        vectorDrawables { useSupportLibrary = true }
        val syncBlob = System.getenv("MECHANICUS_SYNC_BLOB") ?: ""
        buildConfigField("String", "SYNC_BLOB", "\"$syncBlob\"")
    }

    signingConfigs {
        create("release") {
            storeFile = file("keystore/mechanicus.p12")
            storeType = "pkcs12"
            storePassword = "mechanicus123"
            keyAlias = "mechanicus"
            keyPassword = "mechanicus123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures { compose = true; buildConfig = true }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.9.1")
    implementation("com.google.android.gms:play-services-auth:21.2.0")
}
