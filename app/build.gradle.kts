plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.symbol.processing)
}

android {
    namespace = "com.example.cosmicwhisper"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.cosmicwhisper"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    buildFeatures {
        viewBinding = true
    }
    packaging {
        jniLibs {
            // Must be true to compress JNI libs and resolve 16KB alignment issues via extraction at install time
            useLegacyPackaging = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.tracing.ktx)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.metrics.performance)

    // Using LiteRT for 16KB page size alignment support
    implementation(project(":audiototext"))

    implementation("com.google.ai.edge.litert:litert:1.4.2")
    implementation("com.google.ai.edge.litert:litert-gpu:1.4.2")

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}