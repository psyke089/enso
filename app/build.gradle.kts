plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val ensoVersionCode = 14
val ensoVersionName = "1.7.0"

// Signing stays optional so debug builds work without a local keystore.
// Values come from ~/.gradle/gradle.properties (enso.*) or the CI environment.
// The keystore itself lives at ~/.android/keys/enso/enso-release.jks.
fun signingValue(property: String, env: String): String? =
    providers.gradleProperty(property).orNull ?: System.getenv(env)

val releaseStoreFile = signingValue("enso.storeFile", "ENSO_STORE_FILE")

android {
    namespace = "app.enso.meditation"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "app.enso.meditation"
        minSdk = 24
        targetSdk = 37
        versionCode = ensoVersionCode
        versionName = ensoVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = signingValue("enso.storePassword", "ENSO_STORE_PASSWORD")
                keyAlias = signingValue("enso.keyAlias", "ENSO_KEY_ALIAS") ?: "enso"
                keyPassword = signingValue("enso.keyPassword", "ENSO_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            optimization {
                // R8: smaller APK, faster install and startup. Gong sounds are referenced by
                // R.raw ids, so no resource-name reflection remains.
                enable = true
            }
            if (releaseStoreFile != null) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        variant.outputs.forEach { output ->
            output.outputFileName.set("enso-$ensoVersionName.apk")
        }
    }
}

dependencies {
    implementation(libs.androidx.datastore.preferences)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
