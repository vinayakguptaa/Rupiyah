plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.krtky.financetracker"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.krtky.financetracker"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "1.4.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    // Prefer keystore.properties (gitignored), then -PRELEASE_* Gradle props.
    val keystorePropsFile = rootProject.file("keystore.properties")
    val keystoreProps = mutableMapOf<String, String>()
    if (keystorePropsFile.exists()) {
        keystorePropsFile.readLines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
            val eq = trimmed.indexOf('=')
            if (eq > 0) {
                keystoreProps[trimmed.substring(0, eq).trim()] =
                    trimmed.substring(eq + 1).trim()
            }
        }
    }
    fun signProp(name: String, fileKey: String): String? =
        providers.gradleProperty(name).orNull
            ?: keystoreProps[fileKey]?.takeIf { value -> value.isNotBlank() }

    val releaseStoreFilePath = signProp("RELEASE_STORE_FILE", "storeFile")
    val releaseStorePassword = signProp("RELEASE_STORE_PASSWORD", "storePassword")
    val releaseKeyAlias = signProp("RELEASE_KEY_ALIAS", "keyAlias")
    val releaseKeyPassword = signProp("RELEASE_KEY_PASSWORD", "keyPassword")
    val hasReleaseSigning = listOf(
        releaseStoreFilePath,
        releaseStorePassword,
        releaseKeyAlias,
        releaseKeyPassword,
    ).all { value -> value != null }

    signingConfigs {
        create("release") {
            check(hasReleaseSigning) {
                "Release signing missing. Add keystore.properties at repo root " +
                    "(storeFile, storePassword, keyAlias, keyPassword) or pass -PRELEASE_* props."
            }
            // Paths in keystore.properties are relative to the project root.
            storeFile = rootProject.file(releaseStoreFilePath!!)
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            this.keyPassword = releaseKeyPassword
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.ui.text.ExperimentalTextApi",
        )
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "META-INF/NOTICE.md",
            "META-INF/LICENSE.md",
            "META-INF/DEPENDENCIES",
        )
    }
    lint {
        checkReleaseBuilds = true
        abortOnError = true
        // AndroidX lifecycle's detector is incompatible with the Kotlin 2.0 UAST
        // shipped by this toolchain; it crashes before reporting any findings.
        disable += "NullSafeMutableLiveData"
    }
}

configurations.configureEach {
    resolutionStrategy {
        // BOM pins material3 1.4.0 stable which removed public Wavy APIs; force alpha with LinearWavyProgressIndicator
        force("androidx.compose.material3:material3:1.4.0-alpha15")
        force("androidx.compose.material3:material3-android:1.4.0-alpha15")
    }
}

dependencies {
    // Material3 color roles + motion foundations
    val composeBom = platform("androidx.compose:compose-bom:2025.10.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // M3 Expressive LinearWavyProgressIndicator
    implementation("androidx.compose.material3:material3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.4")
    implementation("androidx.compose.foundation:foundation")

    implementation("com.google.dagger:hilt-android:2.52")
    ksp("com.google.dagger:hilt-compiler:2.52")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Jetpack Glance (Material You AppWidgets)
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("org.osmdroid:osmdroid-android:6.1.18")
    implementation("com.sun.mail:android-mail:1.6.7")
    implementation("com.sun.mail:android-activation:1.6.7")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // -- Unit tests --
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.google.truth:truth:1.4.0")
    testImplementation("io.mockk:mockk:1.13.9")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.0.21")

    // -- Instrumentation tests --
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    androidTestImplementation("com.google.truth:truth:1.4.0")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
