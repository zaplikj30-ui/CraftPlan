import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application") version "8.13.2"
    id("org.jetbrains.kotlin.android") version "2.3.10"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.10"
}

val m1Keystore = providers.environmentVariable("CRAFTPLAN_M1_KEYSTORE").orNull
val m1Password = providers.environmentVariable("CRAFTPLAN_M1_KEYSTORE_PASSWORD").orNull
val m1VersionCode = providers.environmentVariable("CRAFTPLAN_M1_VERSION_CODE").orNull
val configureM1 = listOf(m1Keystore, m1Password, m1VersionCode).any { it != null }
val verifiedM1VersionCode = if (configureM1) {
    require(!m1Keystore.isNullOrBlank() && file(m1Keystore).isFile) { "CraftPlan M1 keystore is missing." }
    require(!m1Password.isNullOrBlank()) { "CraftPlan M1 keystore password is missing." }
    requireNotNull(m1VersionCode?.toIntOrNull()?.takeIf { it in 1_001..2_100_000_000 }) {
        "CraftPlan M1 version code is missing or outside the supported range."
    }
} else null
android {
    namespace = "cz.craftplan.app"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "cz.craftplan.app"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-test"
    }

    if (verifiedM1VersionCode != null) {
        val m1Signing = signingConfigs.create("m1") {
            storeFile = file(requireNotNull(m1Keystore))
            storeType = "PKCS12"
            storePassword = m1Password
            keyAlias = "craftplan-m1"
            keyPassword = m1Password
        }
        val debugBuildType = buildTypes.getByName("debug")
        buildTypes.create("m1") {
            initWith(debugBuildType)
            signingConfig = m1Signing
            matchingFallbacks += "debug"
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

androidComponents {
    if (verifiedM1VersionCode != null) {
        onVariants(selector().withBuildType("m1")) { variant ->
            variant.outputs.forEach { it.versionCode.set(verifiedM1VersionCode) }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.04.01"))
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")

    testImplementation("junit:junit:4.13.2")
}
