plugins {
 id("com.android.application")
 id("org.jetbrains.kotlin.android")
 id("org.jetbrains.kotlin.plugin.compose")
}
android {
 namespace = "com.yofect.play"
 compileSdk = 35
 defaultConfig {
  applicationId = "com.yofect.play"
  minSdk = 24
  targetSdk = 35
  versionCode = 5
  versionName = "0.3.2"
  val tmdbApiKey = project.findProperty("TMDB_API_KEY")?.toString().orEmpty()
  buildConfigField("String", "TMDB_API_KEY", "\"" + tmdbApiKey + "\"")
 }
 buildFeatures { compose = true; buildConfig = true }
 compileOptions {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
 }
 kotlinOptions { jvmTarget = "17" }
}
dependencies {
 implementation(platform("androidx.compose:compose-bom:2024.12.01"))
 implementation("androidx.activity:activity-compose:1.10.0")
 implementation("androidx.compose.ui:ui")
 implementation("androidx.compose.ui:ui-tooling-preview")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.compose.material:material-icons-extended")
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
 implementation("io.coil-kt:coil-compose:2.7.0")
 implementation("androidx.media3:media3-exoplayer:1.5.1")
 implementation("androidx.media3:media3-ui:1.5.1")
 debugImplementation("androidx.compose.ui:ui-tooling")
}
