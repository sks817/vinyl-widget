plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.kwansik.vinylwidget"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kwansik.vinylwidget"
        minSdk = 31          // Android 12 이상 (폴드 기기는 모두 해당)
        targetSdk = 36       // 2026.8.31부터 Play 필수 기준
        versionCode = 36
        versionName = "3.8.1"
    }
    signingConfigs {
        // Play 업로드용 키 (저장소는 반드시 Private 유지, 이 파일과 upload.jks는 따로 백업)
        create("upload") {
            storeFile = rootProject.file("upload.jks")
            storePassword = "d7kEmoJXk3TzkaZRFsZO"
            keyAlias = "upload"
            keyPassword = "d7kEmoJXk3TzkaZRFsZO"
        }
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("upload")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
