plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("de.undercouch.download")
    id("kotlin-parcelize")
}

android {
    namespace = "com.visionsnap.facetracking"
    compileSdk = 33

    defaultConfig {
        minSdk = 29
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
    buildToolsVersion = "35.0.0"
}

// import DownloadMPTasks task

project.ext.set("ASSET_DIR", "$projectDir/src/main/assets")
apply("download_tasks.gradle")

repositories {
    allprojects {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://jitpack.io")
    }
}

dependencies {
    val appCompatVersion = "1.5.1"
    val cameraxVersion = "1.3.4"
    val mediapipeTasksVersion = "0.10.21"

    implementation("androidx.appcompat:appcompat:$appCompatVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("com.google.mediapipe:tasks-vision:$mediapipeTasksVersion")

    compileOnly(fileTree("libs") { include("*.jar", "*.aar") })

    implementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}