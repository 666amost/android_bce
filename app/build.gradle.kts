plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp") version "1.9.24-1.0.20"
}

android {
    namespace = "com.bce.courier"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.bce.courier"
        minSdk = 29
        targetSdk = 34
        versionCode = 17
        versionName = "v1.0.17"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        fun gradleStringProperty(name: String, defaultValue: String = ""): String =
            ((project.findProperty(name) as String?) ?: defaultValue)
                .trim()
                .replace("\"", "\\\"")

        val supportWaNumber = gradleStringProperty("SUPPORT_WA_NUMBER")
        val supabaseUrl = gradleStringProperty("SUPABASE_URL")
        val supabaseAnonKey = gradleStringProperty("SUPABASE_ANON_KEY")
        val branchSyncUrl = gradleStringProperty("BRANCH_SYNC_URL")
        val branchApiKey = gradleStringProperty("BRANCH_API_KEY")
        val mqttHost = gradleStringProperty("MQTT_HOST")
        val mqttUsername = gradleStringProperty("MQTT_USERNAME")
        val mqttPassword = gradleStringProperty("MQTT_PASSWORD")
        val mqttEnabled = gradleStringProperty("MQTT_ENABLED")
            .takeIf { it.isNotBlank() }
            ?.toBoolean()
            ?: false
        val mqttPort = ((project.findProperty("MQTT_PORT") as String?) ?: "8883")
            .trim()
            .toIntOrNull()
            ?: 8883

        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
        buildConfigField("String", "BRANCH_SYNC_URL", "\"$branchSyncUrl\"")
        buildConfigField("String", "BRANCH_API_KEY", "\"$branchApiKey\"")
        buildConfigField("String", "SUPPORT_WA_NUMBER", "\"$supportWaNumber\"")
        buildConfigField("boolean", "MQTT_ENABLED", mqttEnabled.toString())
        buildConfigField("String", "MQTT_HOST", "\"$mqttHost\"")
        buildConfigField("int", "MQTT_PORT", mqttPort.toString())
        buildConfigField("String", "MQTT_USERNAME", "\"$mqttUsername\"")
        buildConfigField("String", "MQTT_PASSWORD", "\"$mqttPassword\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        create("minifiedDebug") {
            initWith(getByName("debug"))
            isMinifyEnabled = true
            isShrinkResources = true
            matchingFallbacks += listOf("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // ABI splits: tetap gunakan universal APK hanya ARM agar ukuran moderat
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = true
        }
    }

    lint {
        disable += setOf("IconLauncherShape", "IconDipSize", "IconLocation", "IconDuplicates")
        warningsAsErrors = false
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    implementation("io.coil-kt:coil-compose:2.5.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    implementation("androidx.navigation:navigation-compose:2.8.5")

    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("com.google.android.gms:play-services-location:21.3.0")

    
    implementation("org.osmdroid:osmdroid-android:6.1.20")

    implementation("com.google.zxing:core:3.5.3")

    implementation("com.airbnb.android:lottie-compose:6.3.0")

    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.work:work-runtime-ktx:2.9.1")

    implementation("com.gojek.courier:courier:0.3.6")
    implementation("com.gojek.courier:courier-message-adapter-gson:0.3.6")
    implementation("com.gojek.courier:alarm-pingsender:0.3.6")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.02.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

}
