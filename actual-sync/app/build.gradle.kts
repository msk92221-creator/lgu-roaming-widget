plugins {
    id("com.android.application")
}

android {
    namespace = "com.msk.roamingwidget"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.msk.roamingwidget"
        minSdk = 28
        targetSdk = 35
        versionCode = 8
        versionName = "3.0.0-beta2"

        testInstrumentationRunner = "android.app.InstrumentationTestRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
