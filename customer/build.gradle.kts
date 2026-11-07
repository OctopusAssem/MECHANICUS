plugins {
    id("com.android.application")
}

android {
    namespace = "com.engauto.cs"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.engauto.cs"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
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
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
