plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.android.cache.fix)
}

val keystorePath: String? = System.getenv("KEYSTORE_PATH")
val keystorePass: String? = System.getenv("KEYSTORE_PASS")
val keyPass: String? = System.getenv("KEYPASS")
val haveReleaseSigning = !keystorePath.isNullOrEmpty() && file(keystorePath).exists()

android {
    namespace = "io.github.mhmrdd.isolationpolicy"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.mhmrdd.isolationpolicy"
        minSdk = 29
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"
    }

    signingConfigs {
        if (haveReleaseSigning) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = keystorePass
                keyAlias = "isolationpolicy"
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        buildConfig = true
        aidl = true
    }

    packaging {
        resources.excludes += "**"
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    compileOnly(libs.xposed.api)
    implementation(libs.androidx.recyclerview)
}
