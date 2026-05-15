import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val holidayApiKey: String = localProperties.getProperty("HOLIDAY_API_KEY", "")
val kakaoNativeAppKey: String = localProperties.getProperty("KAKAO_NATIVE_APP_KEY", "")

val releaseKeystoreFile: String? = localProperties.getProperty("RELEASE_KEYSTORE_FILE")
val releaseKeystorePassword: String? = localProperties.getProperty("RELEASE_KEYSTORE_PASSWORD")
val releaseKeyAlias: String? = localProperties.getProperty("RELEASE_KEY_ALIAS")
val releaseKeyPassword: String? = localProperties.getProperty("RELEASE_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    releaseKeystoreFile,
    releaseKeystorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "com.unboundapex.octalink"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.unboundapex.octalink"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
        vectorDrawables { useSupportLibrary = true }
        buildConfigField("String", "HOLIDAY_API_KEY", "\"$holidayApiKey\"")
        buildConfigField("String", "KAKAO_NATIVE_APP_KEY", "\"$kakaoNativeAppKey\"")
        // AndroidManifest 의 카카오 AuthCodeHandlerActivity intent-filter scheme 에 주입
        manifestPlaceholders["KAKAO_NATIVE_APP_KEY"] = kakaoNativeAppKey
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseKeystoreFile!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.2")
    implementation("androidx.activity:activity-compose:1.9.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Firebase BOM — 모든 Firebase 라이브러리 버전 일괄 관리
    // 주의: BOM 33.0+ 부터 `-ktx` 접미사 라이브러리 폐지 (KTX 확장이 메인 라이브러리에 통합).
    //       firebase-xxx-ktx → firebase-xxx 로 이름 변경됨
    implementation(platform("com.google.firebase:firebase-bom:34.13.0"))

    // Firebase products (BOM이 버전 결정, 이름에서 -ktx 제거)
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-analytics")
    // Cloud Functions Callable — Kakao 토큰 ↔ Firebase Custom Token 교환용
    implementation("com.google.firebase:firebase-functions")

    // 이미지 로딩 (HTTPS URL → Compose) — CommunityScreen post 이미지, Firebase Storage download URL.
    implementation("io.coil-kt:coil-compose:2.7.0")

    // 카카오 로그인 SDK (v2-user) — Kakao OAuth → Firebase Custom Token 교환 진입점
    implementation("com.kakao.sdk:v2-user:2.20.6")
    // suspend 코루틴 어댑터용 — Tasks.await() 등에서 사용
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
