import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties // 추가

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization") // 추가
}

// 추가: local.properties 값 읽기
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun localValue(key: String) = "\"${localProps.getProperty(key, "")}\""

android {
    namespace = "com.finpop.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.finpop.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        // 추가: 코드에서 BuildConfig.XXX 로 사용
        buildConfigField("String", "SUPABASE_URL", localValue("SUPABASE_URL"))
        buildConfigField("String", "SUPABASE_KEY", localValue("SUPABASE_KEY"))
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", localValue("GOOGLE_WEB_CLIENT_ID"))
    }

    buildFeatures {
        compose = true
        buildConfig = true // 추가
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // 추가: Supabase (로그인, DB, 통신)
    implementation(platform("io.github.jan-tennert.supabase:bom:3.2.2"))
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.ktor:ktor-client-okhttp:3.2.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    // 추가: 구글 로그인 (계정 선택 창)
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
}