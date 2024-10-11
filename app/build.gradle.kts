plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("de.undercouch.download")
    id("kotlin-parcelize")
}

android {
    namespace = "com.test.mlfacelandmarkplugin"
    compileSdk = 35

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
    implementation("androidx.fragment:fragment-ktx:1.8.4")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")

    // CameraX core library
    val cameraxVersion = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraxVersion")

    // CameraX Camera2 extensions
    implementation("androidx.camera:camera-camera2:$cameraxVersion")

    // CameraX Lifecycle library
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")

    // CameraX View class
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // MediaPipe Library
    implementation("com.google.mediapipe:tasks-vision:latest.release")

    implementation("com.vmadalin:easypermissions-ktx:latest.release")

    compileOnly(fileTree("libs") { include("*.jar", "*.aar") })
}