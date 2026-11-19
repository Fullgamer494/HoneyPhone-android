import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
    id("org.jetbrains.kotlin.plugin.compose")
}

val probeProperties = Properties().apply {
    val config = rootProject.file("probe.properties")
    if (config.exists()) config.inputStream().use { load(it) }
}
fun probeLiteral(key: String): String = "\"" + probeProperties.getProperty(key, "").trim()
    .replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\""
fun buildConfigLiteral(value: String): String = "\"" + value.trim()
    .replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\""

val releaseStoreFile = providers.environmentVariable("HONEYPHONE_RELEASE_STORE_FILE").orNull
val releaseStorePassword = providers.environmentVariable("HONEYPHONE_RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("HONEYPHONE_RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("HONEYPHONE_RELEASE_KEY_PASSWORD").orNull
val releaseSigningValues = listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
val releaseSigningConfigured = releaseSigningValues.all { !it.isNullOrBlank() }
require(releaseSigningValues.all { it.isNullOrBlank() } || releaseSigningConfigured) {
    "Configure all HONEYPHONE_RELEASE_* signing variables or leave them all unset."
}
val releaseApiBaseUrl = providers.environmentVariable("HONEYPHONE_API_BASE_URL").orElse("").get()

android {
    namespace = "dev.honeyphone.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.honeyphone.app"
        minSdk = 29
        targetSdk = 36
        versionCode = 4
        versionName = "0.4.0"
        listOf("PROBE_DNS_NAME", "PROBE_EXPECTED_TXT", "PROBE_DOH_URL", "PROBE_HTTPS_URL", "PROBE_CONFIG_VERSION").forEach { key -> buildConfigField("String", key, probeLiteral(key)) }
        buildConfigField("String", "API_BASE_URL", "\"\"")
    }

    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = file(requireNotNull(releaseStoreFile))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }
    buildTypes {
        debug { buildConfigField("String", "API_BASE_URL", "\"http://127.0.0.1:8081/api/v1/\"") }
        release {
            buildConfigField("String", "API_BASE_URL", buildConfigLiteral(releaseApiBaseUrl))
            if (releaseSigningConfigured) signingConfig = signingConfigs.getByName("release")
        }
    }
    kotlin {
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    }
}

kapt { correctErrorTypes = true; arguments { arg("room.schemaLocation", projectDir.resolve("schemas").path) } }

dependencies {
    testImplementation("junit:junit:4.13.2")
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.11.0")
    implementation("androidx.work:work-runtime-ktx:2.10.5")
    implementation("androidx.core:core:1.17.0")
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    kapt("androidx.room:room-compiler:2.8.5")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
