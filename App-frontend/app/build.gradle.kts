plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp") version "1.9.21-1.0.15"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    id("kotlin-kapt")
}

android {
    namespace = "com.example.safemindwatch"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.safemindwatch"
        minSdk = 26
        targetSdk = 34
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
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.7"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation ("androidx.gridlayout:gridlayout:1.0.0")
    implementation("com.github.bumptech.glide:glide:4.12.0")
    implementation("com.google.android.material:material:1.11.0")


    implementation ("androidx.cardview:cardview:1.0.0")
    implementation ("com.google.android.material:material:1.11.0")
    implementation ("com.hbb20:ccp:2.5.4")
    implementation("androidx.work:work-runtime-ktx:2.8.1")

    // Retrofit dependencies
    implementation ("com.squareup.retrofit2:retrofit:2.9.0")
    implementation ("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation ("com.squareup.okhttp3:logging-interceptor:4.9.1")

    // Gson for parsing JSON
    implementation ("com.google.code.gson:gson:2.8.8")
    implementation ("com.android.volley:volley:1.2.1")

    // Google Sign-In Dependencies
    implementation("com.google.android.gms:play-services-auth:21.0.0")
    implementation ("com.google.android.gms:play-services-auth:20.7.0")

    implementation ("com.google.android.material:material:1.11.0")
    implementation ("com.android.volley:volley:1.2.1")


    // Networking (Retrofit + Gson)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.google.code.gson:gson:2.10.1")

    implementation ("com.github.PhilJay:MPAndroidChart:v3.1.0")


    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Jetpack Compose
    implementation(platform("androidx.compose:compose-bom:2024.01.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.compiler:compiler:1.5.7")
    implementation("androidx.compose.ui:ui:1.6.3")
    implementation ("com.github.PhilJay:MPAndroidChart:v3.1.0")
    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.01.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    // doctor
    implementation("com.google.accompanist:accompanist-permissions:0.28.0")
    implementation("com.google.android.gms:play-services-location:21.0.1")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("io.coil-kt:coil-compose:2.4.0")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation ("androidx.recyclerview:recyclerview:1.3.1")
    implementation ("pl.droidsonroids.gif:android-gif-drawable:1.2.28")
    implementation("com.google.android.material:material:1.11.0")
    implementation ("androidx.appcompat:appcompat:1.6.1")
}
