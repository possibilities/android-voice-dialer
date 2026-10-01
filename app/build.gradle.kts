plugins { id("com.android.application") }
android {
    namespace = "dev.possibilities.voicedialer"
    compileSdk = 36
    defaultConfig { applicationId = "dev.possibilities.voicedialer"; minSdk = 29; targetSdk = 36; versionCode = 1; versionName = "0.1.0"; ndk { abiFilters += "arm64-v8a" } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildTypes { release { isMinifyEnabled = false } }
    lint { abortOnError = true }
}
dependencies {
    implementation("io.github.webrtc-sdk:android:150.7871.01")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")
    testImplementation("junit:junit:4.13.2")
}
