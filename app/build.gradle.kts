plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Wersja dla użytkownika: zmieniana ręcznie przy wydaniu w Google Play.
val appVersionName = "1.0.0"
// versionCode musi rosnąć z każdym plikiem wysłanym do Google Play: w CI to numer przebiegu GitHub Actions, przy
// budowaniu lokalnym podaj go ręcznie, np. ./gradlew :app:bundleRelease -PversionCode=26.
val appVersionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
    ?: (findProperty("versionCode") as String?)?.toIntOrNull()
    ?: 1
val ciShortSha = System.getenv("GITHUB_SHA")?.take(7) ?: "local"

android {
    namespace = "pl.czytnik.app"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "pl.czytnik.glosowy"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = appVersionCode
        versionName = appVersionName

        // Tylko procesory telefonów (bez emulatorów x86) – biblioteki ML Kit mają kod natywny dla każdego ABI,
        // a PRD wymaga APK ≤ 60 MB.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    signingConfigs {
        // Wspólny klucz debug w repozytorium (nie jest tajny): kolejne APK z CI instalują się na poprzednie
        // bez odinstalowania, więc użytkownik nie traci pobranych modeli ani ustawień.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
            // Wersje testowe z CI rozpoznawalne w Ustawienia → O aplikacji: 1.0.0-25-abc1234.
            versionNameSuffix = "-$appVersionCode-$ciShortSha"
        }
        release {
            // R8: usuwa nieużywany kod i zasoby, zmniejsza APK/AAB.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // Release z R8, ale podpisany wspólnym kluczem debug: ./gradlew :app:installR8test instaluje go na wersję
        // testową, żeby sprawdzić na telefonie, czy R8 niczego nie zepsuł (Google Play nie przyjmie tego pliku).
        create("r8test") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            versionNameSuffix = "-r8-$appVersionCode-$ciShortSha"
            matchingFallbacks += "release"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("pl.czytnik:core")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.language.id)
    implementation(libs.mlkit.translate)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
