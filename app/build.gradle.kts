import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// ---------------------------------------------------------------------------------------------
// NDI SDK resolution
//
// Production builds REQUIRE the official NDI SDK for Android (downloaded by the developer after
// registering at https://ndi.video/for-developers/ndi-sdk/download/). The SDK is proprietary and
// is never committed to this repository. Expected layout (as shipped by NDI):
//   <sdk>/include/Processing.NDI.Lib.h
//   <sdk>/lib/arm64-v8a/libndi.so   (and other ABIs)
//
// Validation builds (-Pndi.validationBuild=true) compile everything except the NDI bridge and
// produce an APK that reports "NDI SDK not installed" at runtime. They are for CI only and are
// clearly labelled; they never pretend to send NDI.
// ---------------------------------------------------------------------------------------------
val validationBuild: Boolean = (findProperty("ndi.validationBuild") as String?)?.toBoolean() ?: false

val ndiSdkDir: File = run {
    val fromProp = findProperty("ndi.sdkDir") as String?
    val fromEnv = System.getenv("NDI_SDK_DIR")
    val path = fromProp?.takeIf { it.isNotBlank() } ?: fromEnv?.takeIf { it.isNotBlank() }
    if (path != null) File(path) else rootProject.file("third_party/ndi-sdk")
}

val ndiAbis = listOf("arm64-v8a", "armeabi-v7a")
val requestedAbis: List<String> = ((findProperty("ndi.abis") as String?) ?: "arm64-v8a,armeabi-v7a")
    .split(',').map { it.trim() }.filter { it.isNotEmpty() }

fun ndiHeader(): File = File(ndiSdkDir, "include/Processing.NDI.Lib.h")
fun ndiLib(abi: String): File = File(ndiSdkDir, "lib/$abi/libndi.so")

if (!validationBuild) {
    val missing = mutableListOf<String>()
    if (!ndiHeader().isFile) missing += ndiHeader().path
    requestedAbis.forEach { abi -> if (!ndiLib(abi).isFile) missing += ndiLib(abi).path }
    if (missing.isNotEmpty()) {
        throw GradleException(
            """
            |NDI SDK for Android not found. This is a production NDI build and it will not be faked.
            |Missing files:
            |${missing.joinToString("\n") { "  - $it" }}
            |
            |Fix one of:
            |  1. Install the official NDI SDK for Android and copy its 'include' and 'lib' folders
            |     into third_party/ndi-sdk/ (see docs/NDI_SDK_SETUP.md), or
            |  2. Pass -Pndi.sdkDir=/path/to/sdk or set NDI_SDK_DIR, or
            |  3. Restrict ABIs with -Pndi.abis=arm64-v8a if your SDK copy only has that ABI, or
            |  4. For CI compile validation ONLY: -Pndi.validationBuild=true (APK will NOT send NDI).
            """.trimMargin()
        )
    }
}

val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.isFile) f.inputStream().use { load(it) }
}
fun signingValue(key: String, env: String): String? =
    (keystoreProps.getProperty(key) ?: System.getenv(env))?.takeIf { it.isNotBlank() }

val releaseStoreFile = signingValue("storeFile", "SIGNING_STORE_FILE")
val hasReleaseSigning = releaseStoreFile != null && File(releaseStoreFile).isFile &&
    signingValue("storePassword", "SIGNING_STORE_PASSWORD") != null &&
    signingValue("keyAlias", "SIGNING_KEY_ALIAS") != null &&
    signingValue("keyPassword", "SIGNING_KEY_PASSWORD") != null

android {
    namespace = "com.ndicamera.professional"
    compileSdk = 36
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "com.ndicamera.professional"
        minSdk = 29
        targetSdk = 36
        // CI passes -PversionCode=<run number> so every uploaded bundle has a higher versionCode.
        versionCode = (findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
        versionName = "1.0.0"

        buildConfigField("boolean", "NDI_VALIDATION_BUILD", validationBuild.toString())

        ndk {
            abiFilters.clear()
            abiFilters.addAll(requestedAbis.filter { it in ndiAbis })
        }
        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17", "-fno-exceptions", "-fno-rtti")
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    // 16 KB page-size compatibility (required by Google Play for Android 15+ devices).
                    "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON",
                    "-DNDI_VALIDATION_BUILD=${if (validationBuild) "ON" else "OFF"}",
                    "-DNDI_SDK_DIR=${ndiSdkDir.absolutePath.replace('\\', '/')}",
                )
            }
        }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = File(releaseStoreFile!!)
                storePassword = signingValue("storePassword", "SIGNING_STORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "SIGNING_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = if (validationBuild) "-debug-validation" else "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
            if (validationBuild) versionNameSuffix = "-validation"
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.play.billing)
    implementation(libs.androidx.core.splashscreen)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
