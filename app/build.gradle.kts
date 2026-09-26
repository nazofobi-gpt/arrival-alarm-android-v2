plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val connectorBaseUrl = providers.gradleProperty("CONNECTOR_BASE_URL")
    .orElse(providers.environmentVariable("CONNECTOR_BASE_URL"))
    .orNull
    .orEmpty()
val escapedConnectorBaseUrl = connectorBaseUrl
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")

android {
    namespace = "com.nazofobi.arrivalalarm"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.nazofobi.arrivalalarm"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-v2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "CONNECTOR_BASE_URL", "\"" + escapedConnectorBaseUrl + "\"")
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
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.activity.compose)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.maplibre.compose)
    runtimeOnly(libs.maplibre.compose.runtime.opengl.android)

    testImplementation(libs.junit)
    // JVM unit tests exercise the production org.json adapter. Android's compile-time
    // org.json stubs are not executable on the host JVM, so provide the real JVM implementation.
    testImplementation("org.json:json:20240303")

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
