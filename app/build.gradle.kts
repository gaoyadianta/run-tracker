import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin)
    alias(libs.plugins.maps.secrets)
    alias(libs.plugins.dagger.hilt.android)
    alias(libs.plugins.jetbrains.kotlin.kapt)
    alias(libs.plugins.jetbrains.kotlin.compose)
}

val localProperties = Properties().apply {
    rootProject.file("local.properties")
        .takeIf { it.exists() }
        ?.inputStream()
        ?.use { load(it) }
}

fun secretValue(name: String): String =
    providers.gradleProperty(name).orNull
        ?: System.getenv(name)
        ?: localProperties.getProperty(name).orEmpty()

android {
    namespace = "com.sdevprem.runtrack"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.sdevprem.runtrack"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        
        // Handle optional AMAP API key
        val amapApiKey = secretValue("AMAP_API_KEY")
        manifestPlaceholders["AMAP_API_KEY"] = amapApiKey

        // Secrets are injected from local.properties, -P properties, or CI environment variables.
        resValue("string", "coze_access_token", secretValue("COZE_ACCESS_TOKEN"))
        resValue("string", "ai_ws_volcano_token", secretValue("AI_WS_VOLCANO_TOKEN"))
        resValue("string", "ai_ws_bailian_token", secretValue("AI_WS_BAILIAN_TOKEN"))
        resValue("string", "ai_bailian_api_key", secretValue("AI_BAILIAN_API_KEY"))
        resValue("string", "ai_volcano_access_key", secretValue("AI_VOLCANO_ACCESS_KEY"))
        resValue("string", "ai_volcano_ark_api_key", secretValue("AI_VOLCANO_ARK_API_KEY"))
        resValue("string", "news_program_api_key_value", secretValue("NEWS_PROGRAM_API_KEY"))
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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
    buildFeatures {
        compose = true
        // Enable BuildConfig generation (silences buildConfigFields warning)
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)

    //compose
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.compose.material.icons.extended)

    //test
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    //kotlin
    implementation(platform(libs.kotlin.bom))

    //navigation
    implementation(libs.androidx.navigation.compose)

    //maps
    implementation(libs.maps.compose)
    implementation(libs.play.services.maps)
    implementation(libs.play.services.location)
    
    //amap
    implementation("com.amap.api:3dmap:10.0.600")

    //hilt
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    // lifecycle
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.service)

    //Timber
    implementation(libs.timber)

    //room
    implementation(libs.androidx.room.runtime)
    annotationProcessor(libs.androidx.room.compiler)
    kapt(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)

    //datastore
    implementation(libs.androidx.datastore.preferences)

    //coil
    implementation(libs.coil.compose)

    //paging
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)

    //vico
    implementation(libs.vico.compose)
    implementation(libs.vico.compose.m3)
    implementation(libs.vico.core)
    
    //coze webrtc - 真实实现
    implementation("com.coze:coze-api:0.2.1")
    // Volcengine ByteRTC SDK artifact (contains com.ss.bytertc.engine.* classes)
    implementation("com.volcengine:VolcEngineRTC:3.58.1.19400")
    implementation("com.fasterxml.jackson.core:jackson-core:2.15.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.15.2")
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
}

kapt {
    correctErrorTypes = true
    arguments {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.incremental", "true")
    }
}
