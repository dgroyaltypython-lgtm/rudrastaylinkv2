plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.rudradigital.rudrastaylink"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.rudradigital.rudrastaylink"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    val ciKeyPath = System.getenv("CM_KEYSTORE_PATH") ?: System.getenv("KEYSTORE_PATH")
    val ciKeyAlias = System.getenv("CM_KEY_ALIAS") ?: System.getenv("KEY_ALIAS")
    val ciKeyPassword = System.getenv("CM_KEY_PASSWORD") ?: System.getenv("KEY_PASSWORD")
    val ciStorePassword = System.getenv("CM_KEYSTORE_PASSWORD") ?: System.getenv("KEYSTORE_PASSWORD")

    signingConfigs {
        create("ciRelease") {
            if (!ciKeyPath.isNullOrBlank() && !ciKeyAlias.isNullOrBlank() && !ciKeyPassword.isNullOrBlank() && !ciStorePassword.isNullOrBlank()) {
                storeFile = file(ciKeyPath)
                keyAlias = ciKeyAlias
                keyPassword = ciKeyPassword
                storePassword = ciStorePassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            manifestPlaceholders["allowCleartext"] = "true" // Local LAN API testing only.
        }
        release {
            manifestPlaceholders["allowCleartext"] = "false" // Production builds require HTTPS.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Local release smoke builds use the debug key; Codemagic provides the private release key.
            signingConfig = signingConfigs.getByName(
                if (!ciKeyPath.isNullOrBlank() && !ciKeyAlias.isNullOrBlank() && !ciKeyPassword.isNullOrBlank() && !ciStorePassword.isNullOrBlank()) "ciRelease" else "debug"
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

// Kotlin 2.3+ compiler options DSL (the legacy kotlinOptions DSL is an error).
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        optIn.add("kotlin.RequiresOptIn")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.google.code.gson:gson:2.13.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("io.coil-kt.coil3:coil-compose:3.6.3")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.6.3")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
