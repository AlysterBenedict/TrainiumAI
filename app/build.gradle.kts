plugins {

    alias(libs.plugins.android.application)

    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.gms.google.services)

}



android {

    namespace = "com.example.aifitnesscoach"

    compileSdk = 36

    buildFeatures {

        viewBinding = true

    }

    aaptOptions {

        noCompress.add("tflite")

    }

    defaultConfig {

        applicationId = "com.example.aifitnesscoach"

        minSdk = 24

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

}



dependencies {



    implementation(libs.androidx.core.ktx)

    implementation(libs.androidx.appcompat)

    implementation(libs.material)

    implementation(libs.androidx.activity)

    implementation(libs.androidx.constraintlayout)

    implementation("androidx.core:core-splashscreen:1.0.1")
    // Allow BoM to manage this version
    implementation("com.google.firebase:firebase-auth")
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))

    // Firebase Authentication
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.github.bumptech.glide:glide:4.12.0")
    implementation("com.github.bumptech.glide:glide:4.16.0")// Google Sign-In SDK
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("androidx.dynamicanimation:dynamicanimation:1.0.0")
    implementation("androidx.biometric:biometric-ktx:1.2.0-alpha05")

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.junit)

    androidTestImplementation(libs.androidx.espresso.core)

// CameraX core libraries

    val camerax_version = "1.3.1" // Changed def to val for Kotlin

    implementation("androidx.camera:camera-core:$camerax_version") // Added parentheses for consistency

    implementation("androidx.camera:camera-camera2:$camerax_version") // Added parentheses

    implementation("androidx.camera:camera-lifecycle:$camerax_version") // Added parentheses

    implementation("androidx.camera:camera-view:$camerax_version") // Added parentheses



// TensorFlow Lite Task Vision library for Pose Estimation

    implementation("org.tensorflow:tensorflow-lite-task-vision:0.4.3")

// MediaPipe

    implementation("com.google.mediapipe:tasks-vision:0.10.0")



    implementation("com.google.code.gson:gson:2.10.1")

    // With the newer, more compatible versions
    implementation("org.pytorch:pytorch_android:2.1.0")
    implementation("org.pytorch:pytorch_android_torchvision:2.1.0")
    // Retrofit for networking
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.9.3")

    implementation("com.google.code.gson:gson:2.9.0")

// Coroutines for asynchronous programming
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.5.1")
} // Moved this closing brace to a new line
