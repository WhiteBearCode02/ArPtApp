plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.arptapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.arptapp"
        minSdk = 36
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
    buildFeatures {
        viewBinding = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // [CameraX Core Library] 카메라의 핵심 엔진입니다. 
    val cameraxVersion = "1.3.0" // 안정화된 최신 버전을 사용합니다.
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    
    // [Lifecycle Library] 앱이 꺼지면 카메라도 자동으로 꺼지게 관리해 줍니다.
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    
    // [View Library] 화면에 영상을 뿌려주는 PreviewView를 사용하기 위함입니다.
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // [MediaPipe Library] AI 자세 분석을 위한 핵심 엔진입니다.
    implementation("com.google.mediapipe:tasks-vision:0.10.0")
}