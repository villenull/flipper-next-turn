plugins { id("com.android.application"); kotlin("android") }
android {
 namespace = "io.github.flippernowplaying.nav"
 compileSdk = 36
 buildToolsVersion = "35.0.0"
 defaultConfig { applicationId = "io.github.flippernowplaying.nav"; minSdk = 26; targetSdk = 36; versionCode = 1; versionName = "0.1" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}
dependencies { implementation(project(":navcore")); testImplementation(kotlin("test")) }
