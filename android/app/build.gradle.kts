plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "dz.cardiag.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "dz.cardiag.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildTypes {
        debug {
            buildConfigField(
                "String",
                "SUPABASE_URL",
                "\"${System.getenv("SUPABASE_URL") ?: ""}\""
            )
            buildConfigField(
                "String",
                "SUPABASE_PUBLISHABLE_KEY",
                "\"${System.getenv("SUPABASE_PUBLISHABLE_KEY") ?: ""}\""
            )
        }

        release {
            buildConfigField(
                "String",
                "SUPABASE_URL",
                "\"${System.getenv("SUPABASE_URL") ?: ""}\""
            )
            buildConfigField(
                "String",
                "SUPABASE_PUBLISHABLE_KEY",
                "\"${System.getenv("SUPABASE_PUBLISHABLE_KEY") ?: ""}\""
            )
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.08.00"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.navigation:navigation-compose:2.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    implementation(platform("io.github.jan-tennert.supabase:bom:3.7.0"))
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:functions-kt")
    implementation("io.ktor:ktor-client-android:3.5.1")

    constraints {
        implementation("androidx.browser:browser:1.8.0")
    }
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
