plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.piotv.keytab"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.piotv.keytab"
        minSdk = 24
        targetSdk = 34
        versionCode = 14
        versionName = "0.7.2"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // F-Droid/IzzyOnDroid-kompatibles Release-Signing via Umgebungsvariablen:
            //   KEYTAB_KEYSTORE=/pfad/zur/release.jks (Standard: keystore/keytab-release.jks)
            //   KEYTAB_KEYSTORE_PASSWORD / KEYTAB_KEY_ALIAS / KEYTAB_KEY_PASSWORD
            val ksPath = System.getenv("KEYTAB_KEYSTORE")
                ?: rootProject.file("keystore/keytab-release.jks").absolutePath
            val ksPassword = System.getenv("KEYTAB_KEYSTORE_PASSWORD") ?: "keytab-release"
            val keyAlias = System.getenv("KEYTAB_KEY_ALIAS") ?: "keytab"
            val keyPassword = System.getenv("KEYTAB_KEY_PASSWORD") ?: "keytab-release"
            signingConfig = signingConfigs.create("release") {
                storeFile = file(ksPath)
                storePassword = ksPassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests {
            // Robolectric: Ressourcen/Layouts für lokale Tests verfügbar machen
            isIncludeAndroidResources = true
        }
    }

    lint {
        // lintVital liefert jetzt saubere Ergebnisse (SuspiciousIndentation gefixt)
        checkReleaseBuilds = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.fragment:fragment-ktx:1.8.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.12.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
}
