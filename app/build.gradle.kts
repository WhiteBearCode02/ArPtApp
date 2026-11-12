import java.util.Properties
import java.security.MessageDigest
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.isFile) {
        localPropertiesFile.inputStream().buffered().use(::load)
    }
}

fun localPropertyAsBuildConfigString(name: String): String {
    val value = localProperties.getProperty(name).orEmpty()
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
    return "\"$value\""
}

plugins {
    id("com.android.application") version "8.13.2"
    id("org.jetbrains.kotlin.android") version "2.1.0"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.0"
    id("com.google.devtools.ksp") version "2.1.0-1.0.29"
}

// Keep generated artifacts out of the module source tree so IDE language servers
// do not index and lock generated jars such as R.jar on Windows.
val artifactFolder = providers.gradleProperty("verificationBuildDir").getOrElse("app")
require(artifactFolder.matches(Regex("[A-Za-z0-9_-]+"))) { "Build directory must be a simple folder name" }
layout.buildDirectory.set(rootProject.layout.buildDirectory.dir(artifactFolder))

// Explicit owner-only experiment. Ordinary debug/release/A-B APKs still exclude model weights.
val privateExp02Apk = providers.gradleProperty("privateExp02Apk").orNull == "true"
val privateExp02Assets = layout.buildDirectory.dir("generated/privateExp02Assets")
val preparePrivateExp02Assets = tasks.register<Copy>("preparePrivateExp02Assets") {
    val modelFile = rootProject.file("app/src/main/assets/airptcoach_yolo26n_exp02_fp32.tflite")
    onlyIf { privateExp02Apk }
    from(modelFile) { into("yolo_private"); rename { "exp02.bin" } }
    from(rootProject.file("licenses/ULTRALYTICS_AGPL-3.0.txt")) { into("yolo_private") }
    from(rootProject.file("docs/private-exp02-apk-notice.txt")) { into("yolo_private") }
    into(privateExp02Assets)
    doFirst {
        check(modelFile.length() == 9894030L) { "Private EXP02 model size mismatch" }
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(modelFile.readBytes()).joinToString("") { "%02x".format(it) }
        check(hash == "464cafe87c3d6e04ba71951e73efab8690367f77fb224105d4b1e50ab3d33749") {
            "Private EXP02 model SHA-256 mismatch"
        }
    }
}
tasks.configureEach {
    if (privateExp02Apk && (name == "mergeDebugAssets" ||
            (name.contains("Debug") && name.contains("Lint", ignoreCase = true)))) {
        dependsOn(preparePrivateExp02Assets)
    }
}

android {
    namespace = "com.example.arptapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.arptapp"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "SUPABASE_URL", localPropertyAsBuildConfigString("supabase.url"))
        buildConfigField("String", "SUPABASE_KEY", localPropertyAsBuildConfigString("supabase.key"))
        // Selected final app model is EXP02. EXP03 remains an explicit comparison-only build.
        buildConfigField("String", "YOLO_MODEL_VARIANT", "\"EXP02\"")
        buildConfigField("boolean", "YOLO_AB_COMPARISON", "false")
        buildConfigField("boolean", "YOLO_PRIVATE_BUNDLED", "false")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86_64"))
        }
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "YOLO_PRIVATE_BUNDLED", privateExp02Apk.toString())
            // Explicit local-only Shadow rollout. Contract VERIFIED alone cannot enable inference.
            buildConfigField("boolean", "YOLO_SHADOW_ENABLED", "true")
        }
        release {
            buildConfigField("boolean", "YOLO_SHADOW_ENABLED", "false")
            isMinifyEnabled = false // 코드 난독화 비활성화 (개발 단계)
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        // Build types rather than flavors preserve assembleDebug/assembleRelease and release identity.
        create("exp02Debug") {
            initWith(getByName("debug"))
            buildConfigField("boolean", "YOLO_PRIVATE_BUNDLED", "false")
            applicationIdSuffix = ".exp02"
            resValue("string", "app_name", "AIRPTCoach V1 - EXP02")
            buildConfigField("String", "YOLO_MODEL_VARIANT", "\"EXP02\"")
            buildConfigField("boolean", "YOLO_AB_COMPARISON", "true")
            matchingFallbacks += listOf("debug")
        }
        create("exp03Debug") {
            initWith(getByName("debug"))
            buildConfigField("boolean", "YOLO_PRIVATE_BUNDLED", "false")
            applicationIdSuffix = ".exp03"
            resValue("string", "app_name", "AIRPTCoach V2 - EXP03")
            buildConfigField("String", "YOLO_MODEL_VARIANT", "\"EXP03\"")
            buildConfigField("boolean", "YOLO_AB_COMPARISON", "true")
            matchingFallbacks += listOf("debug")
        }
        // Explicit, isolated seven-class SHADOW validation build. Never the default/release model.
        create("exp04Debug") {
            initWith(getByName("debug"))
            buildConfigField("boolean", "YOLO_PRIVATE_BUNDLED", "false")
            applicationIdSuffix = ".exp04"
            resValue("string", "app_name", "AIRPTCoach EXP04 - Shadow")
            buildConfigField("String", "YOLO_MODEL_VARIANT", "\"EXP04\"")
            buildConfigField("boolean", "YOLO_AB_COMPARISON", "true")
            matchingFallbacks += listOf("debug")
        }
    }

    sourceSets {
        if (privateExp02Apk) getByName("debug").assets.srcDir(files(privateExp02Assets).builtBy(preparePrivateExp02Assets))
        for (name in listOf("exp02Debug", "exp03Debug", "exp04Debug")) getByName(name) {
            java.srcDir("src/debug/java")
            manifest.srcFile("src/abComparison/AndroidManifest.xml")
        }
    }
    // AGP instruments one build type per invocation. Default and ordinary commands remain debug.
    val selectedTestBuild = providers.gradleProperty("abTestBuildType").getOrElse("debug")
    require(selectedTestBuild in listOf("debug", "exp02Debug", "exp03Debug", "exp04Debug")) { "Unknown test build type" }
    testBuildType = selectedTestBuild

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        // ViewBinding: XML 레이아웃의 ID를 안전하게 참조하기 위한 기능
        viewBinding = true
        buildConfig = true
    }

    androidResources {
        noCompress += "tflite"
        noCompress += "bin"
        // Default deny in ALL variants, including shared debug APKs. Keep original files on disk.
        // MediaPipe's reviewed .task bundle is retained; loose unreviewed weights are not.
        ignoreAssetsPattern = "!.svn:!.git:!.ds_store:!*.scc:.*:!CVS:!thumbs.db:!picasa.ini:!*~:*.tflite:*.pt:*.pth:*.onnx"
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
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.cardview:cardview:1.0.0")

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

    // --- TensorFlow Lite: YOLO 포즈 모델 추론 엔진 ---
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")

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

    // --- Supabase Kotlin client ---
    val supabaseVersion = "3.0.0"
    val ktorVersion = "3.0.0-rc-1"
    implementation("io.github.jan-tennert.supabase:postgrest-kt:$supabaseVersion")
    implementation("io.github.jan-tennert.supabase:auth-kt:$supabaseVersion")
    implementation("io.ktor:ktor-client-android:$ktorVersion")

    // Unit & UI Testing: 코드 안정성 검토를 위한 테스트 도구
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

}
