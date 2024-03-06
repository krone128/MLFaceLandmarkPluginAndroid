plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("de.undercouch.download")
}

android {
    namespace = "com.test.mlfacelandmarkplugin"
    compileSdk = 33

    defaultConfig {
        minSdk = 29
        targetSdk = 33
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

// import DownloadMPTasks task

project.ext.set("ASSET_DIR", "$projectDir/src/main/assets")
apply("download_tasks.gradle")

dependencies {

    implementation("androidx.core:core-ktx:1.8.0")
    implementation("androidx.appcompat:appcompat:1.5.0")
    implementation("com.google.android.material:material:1.7.0")
    compileOnly(fileTree(mapOf(
        "dir" to "I:\\Unity Installs\\2019.4.23f1\\Editor\\Data\\PlaybackEngines\\AndroidPlayer\\Variations\\il2cpp\\Release\\Classes",
        "include" to listOf("*.aar", "*.jar")
    )))
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    // CameraX core library
    val camerax_version = "1.2.0"
    implementation("androidx.camera:camera-core:$camerax_version")

    // CameraX Camera2 extensions
    implementation("androidx.camera:camera-camera2:$camerax_version")

    // CameraX Lifecycle library
    implementation("androidx.camera:camera-lifecycle:$camerax_version")

    // CameraX View class
    implementation("androidx.camera:camera-view:$camerax_version")

    // MediaPipe Library
    implementation("com.google.mediapipe:tasks-vision:0.10.10")
}