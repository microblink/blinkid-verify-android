plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.microblink.blinkidverify.sample"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.microblink.blinkidverify.sample"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.material3)
    // blinkid-verify-ux 4000.x is compiled against Compose UI 1.11+ (Painter.$stable)
    val composeUi = libs.versions.composeUi.get()
    implementation("androidx.compose.ui:ui:$composeUi")
    implementation("androidx.compose.ui:ui-graphics:$composeUi")
    implementation("androidx.compose.ui:ui-text:$composeUi")
    implementation("androidx.compose.foundation:foundation:$composeUi")
    implementation("androidx.compose.animation:animation:$composeUi")

    implementation(project(":lib-common"))
    implementation(libs.blinkid.verify.ux)
/**
    // use following set of dependencies if you want to use blinkid-verify-ux library module
    // instead of maven dependency, and remove implementation(libs.blinkid.verify.ux) dependency
    implementation(project(":blinkid-verify-ux"))
    implementation(libs.blinkid.verify.core)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.tooling)
*/
}