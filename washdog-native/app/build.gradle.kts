import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

android {
    namespace = "com.washdog.app"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.washdog.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 7
        versionName = "1.3.4"
        buildConfigField("String", "SUPABASE_URL",      "\"${localProps["SUPABASE_URL"] ?: ""}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${localProps["SUPABASE_ANON_KEY"] ?: ""}\"")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // Firma de release con una llave ESTABLE (keystore propio), para que todas las
    // versiones queden firmadas igual y se puedan actualizar sin desinstalar. El keystore
    // y las contraseñas llegan por variables de entorno (el CI las inyecta desde GitHub
    // Secrets); nunca van en el repo. Si no están presentes (build local sin secretos),
    // el release queda sin firmar y se usa debug para probar.
    val keystoreFileEnv = System.getenv("KEYSTORE_FILE")
    signingConfigs {
        create("release") {
            if (keystoreFileEnv != null) {
                storeFile = file(keystoreFileEnv)
                storePassword = System.getenv("STORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            // minify desactivado hasta validar proguard en dispositivo (evita crashes de
            // serialización/reflection sin verificar). Se puede reactivar tras probar.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystoreFileEnv != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint {
        disable += setOf(
            "OldTargetApi", "AndroidGradlePluginVersion", "GradleDependency",
            "DiscouragedApi", "UnusedResources", "MissingApplicationIcon"
        )
        abortOnError = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.05.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.biometric:biometric:1.1.0")
    // fragment >= 1.3.0 es requisito para registerForActivityResult en FragmentActivity
    // (biometric arrastra una versión más vieja). Necesario para el flujo de permisos.
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Supabase (mismas versiones que DocenCheck)
    val supabaseBom = platform("io.github.jan-tennert.supabase:bom:3.1.1")
    implementation(supabaseBom)
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.ktor:ktor-client-okhttp:3.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
