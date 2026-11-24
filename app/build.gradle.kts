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
val debugApiBaseUrl = providers.environmentVariable("HONEYPHONE_API_BASE_URL")
    .orElse("http://127.0.0.1:8081/api/v1/").get()
val releaseApiBaseUrl = providers.environmentVariable("HONEYPHONE_API_BASE_URL").orElse("").get()
val googleClientIdFromJson = rootProject.projectDir.parentFile.resolve("Google OAuth")
    .listFiles()?.firstNotNullOfOrNull { file -> runCatching {
        val root = groovy.json.JsonSlurper().parse(file) as Map<*, *>
        (root["web"] as? Map<*, *>)?.get("client_id") as? String
    }.getOrNull() } ?: ""
val googleServerClientId = providers.environmentVariable("HONEYPHONE_GOOGLE_WEB_CLIENT_ID")
    .orElse(providers.gradleProperty("honeyphone.google.webClientId"))
    .orElse(googleClientIdFromJson).get()

android {
    namespace = "dev.honeyphone.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.honeyphone.app"
        minSdk = 29
        targetSdk = 36
        versionCode = 24
        versionName = "0.5.19"
        listOf("PROBE_DNS_NAME", "PROBE_EXPECTED_TXT", "PROBE_DOH_URL", "PROBE_HTTPS_URL", "PROBE_CONFIG_VERSION").forEach { key -> buildConfigField("String", key, probeLiteral(key)) }
        buildConfigField("String", "API_BASE_URL", "\"\"")
        buildConfigField("String", "GOOGLE_SERVER_CLIENT_ID", buildConfigLiteral(googleServerClientId))
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
        debug { buildConfigField("String", "API_BASE_URL", buildConfigLiteral(debugApiBaseUrl)) }
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
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1") {
        // googleid 1.1.1 declares Kotlin 1.8 compatibility shims that are obsolete
        // with the Kotlin stdlib already supplied by this project.
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk7")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
    }
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    kapt("androidx.room:room-compiler:2.8.5")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
