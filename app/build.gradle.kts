import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// 签名信息从 local.properties 读取（该文件已被 .gitignore 排除）
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val ksPath = localProps.getProperty("signing.storeFile")

android {
    namespace = "com.dshm.calc"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.dshm.calc"
        minSdk = 23
        targetSdk = 37
        versionCode = 4
        versionName = "3.1"
    }

    signingConfigs {
        if (ksPath != null) {
            create("release") {
                storeFile = rootProject.file(ksPath)
                storePassword = localProps.getProperty("signing.storePassword")
                keyAlias = localProps.getProperty("signing.keyAlias")
                keyPassword = localProps.getProperty("signing.keyPassword")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            if (ksPath != null) signingConfig = signingConfigs.getByName("release")
        }
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += setOf(
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json",
                "kotlin/**",
                "META-INF/*.version",
                "META-INF/**/LICENSE.txt"
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.backdrop)
    implementation(libs.kyant.shapes)
    testImplementation(libs.junit)
}
