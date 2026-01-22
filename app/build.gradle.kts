plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // KSP (Kotlin Symbol Processing): Room DB 어노테이션 처리를 위한 최신 엔진
    id("com.google.devtools.ksp") version "1.9.25-1.0.20"
}

android {
    namespace = "com.example.arptapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.arptapp"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86_64"))
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false // 코드 난독화 비활성화 (개발 단계)
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

    buildFeatures {
        // ViewBinding: XML 레이아웃의 ID를 안전하게 참조하기 위한 기능
        viewBinding = true
    }

    packaging{
        jniLibs{
            // 라이브러리 추출 설정과 충돌 방지를 위해 추가
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // AndroidX Core & UI Components: 앱의 기본 동작 및 테마 관리
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // --- Jetpack Navigation (여기에 추가되었습니다!) ---
    // 화면 간 이동(NavGraph)과 데이터 전달을 체계적으로 관리하기 위한 라이브러리
    val navVersion = "2.7.7"
    implementation("androidx.navigation:navigation-fragment-ktx:$navVersion")
    implementation("androidx.navigation:navigation-ui-ktx:$navVersion")

    // --- CameraX: 고성능 카메라 제어 ---
    val cameraxVersion = "1.3.1"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // --- MediaPipe Tasks: AI 비전 기능 (Pose Landmarker 등) ---
    implementation("com.google.mediapipe:tasks-vision:0.10.14")

    // --- Room Database: 로컬 SQLite 데이터베이스 라이브러리 ---
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // --- Gson: JSON 데이터 직렬화 및 파싱 ---
    implementation("com.google.code.gson:gson:2.10.1")

    // --- MPAndroidChart: 데이터 시각화(그래프) 라이브러리 ---
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // --- Coroutines: 비동기 처리 및 백그라운드 작업 ---
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Unit & UI Testing: 코드 안정성 검토를 위한 테스트 도구
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
