plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.kma.drowsinessalertapp"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.kma.drowsinessalertapp"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    //buildFeatures {
    //    compose = true
    //}
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    // Wearable Data Layer (통신)
    implementation("com.google.android.gms:play-services-wearable:18.1.0")
    // Google Play Services의 Kotlin Coroutines 확장
    implementation("com.google.android.gms:play-services-tasks:18.1.0")

    //implementation("com.google.android.gms:play-services-base-ktx:18.0.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
    // 코루틴 (await 사용)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    // lifecycleScope 사용시 필요
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    //implementation(project(":wearable"))
    //wearApp(project(":wearable"))
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}