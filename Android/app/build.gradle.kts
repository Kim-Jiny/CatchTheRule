import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// 릴리스 서명 정보는 저장소에 커밋하지 않는 keystore.properties 에서 읽는다.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) load(FileInputStream(keystorePropertiesFile))
}

android {
    namespace = "com.jiny.catchtherule"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jiny.catchtherule"
        minSdk = 24
        targetSdk = 35
        versionCode = 6
        versionName = "1.0.2"
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true   // BuildConfig.DEBUG 분기(테스트/실제 광고 ID)에 필요
    }

    // 퍼즐 콘텐츠 단일 소스: 리포 루트 shared/content 를 그대로 asset 으로 사용.
    // (iOS 는 빌드 시 Run Script 로 같은 파일을 번들에 복사)
    sourceSets {
        getByName("main") {
            assets.srcDirs("src/main/assets", "../../shared/content")
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // 인앱결제 (광고 제거)
    implementation("com.android.billingclient:billing-ktx:7.1.1")

    // 리워드 광고 (광고 보고 힌트 받기)
    implementation("com.google.android.gms:play-services-ads:23.6.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
