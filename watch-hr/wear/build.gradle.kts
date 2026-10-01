plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.aion.hrtest"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.aion.hrtest"   // 반드시 mobile과 동일
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures { compose = true }
}

dependencies {
    // Data Layer
    implementation("com.google.android.gms:play-services-wearable:19.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    // Health Services (심박수)
    implementation("androidx.health:health-services-client:1.1.0-rc02")

    // ListenableFuture를 코루틴으로 쓰기 위해
    implementation("androidx.concurrent:concurrent-futures-ktx:1.1.0")
    implementation("com.google.guava:guava:32.1.3-android")

    // Wear Compose
    implementation("androidx.wear.compose:compose-material3:1.6.2")
    implementation("androidx.wear.compose:compose-foundation:1.6.2")
    implementation("androidx.activity:activity-compose:1.13.0")

    // 포그라운드 서비스 (화면이 꺼져도 측정 유지)
    implementation("androidx.lifecycle:lifecycle-service:2.10.0")
    implementation("androidx.wear:wear-ongoing:1.1.0")
    implementation("androidx.core:core-ktx:1.18.0")
}
