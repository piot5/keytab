import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Coverage-Gate (Roadmap Punkt 1): Kover 0.8.3, kompatibel mit AGP 8.5 / Kotlin 1.9
    id("org.jetbrains.kotlinx.kover") version "0.8.3"
    // Statisches Qualitäts-Gate: detekt; Issues stehen in config/detekt/detekt.yml,
    // bestehende, bewusst belassene Befunde in der Baseline (fix-erst-schrittweise)
    id("io.gitlab.arturbosch.detekt") version "1.23.7"
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    baseline = file("$rootDir/config/detekt/baseline.xml")
    parallel = true
}

// Coverage-Gate: koverVerify ist das harte Gate (Zeilen-Coverage ≥ 20 %).
// Der Wert ist bewusst ein unteres Limit gegen grobe Regressionen, kein Ziel:
// Gemessen wird der gesamte :app-Scope (UI/Service inklusive), die reinen
// Logik-Klassen liegen individuell deutlich höher.
kover {
    reports {
        verify {
            rule {
                bound {
                    minValue = 20 // Prozent Zeilen-Coverage
                }
            }
        }
    }
}

// Bequemer Einpunkt-Task für CI: Report + Log + hartes Gate.
tasks.register("coverageGate") {
    group = "verification"
    dependsOn("koverHtmlReport", "koverLog", "koverVerify")
    doLast {
        println("Coverage-Gate bestanden (Schwelle 20 % Zeilen; Report: build/reports/kover)")
    }
}

android {
    namespace = "com.piotv.keytab"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.piotv.keytab"
        minSdk = 24
        targetSdk = 34
        versionCode = 26
        versionName = "0.11"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // F-Droid/IzzyOnDroid-kompatibles Release-Signing: Passwörter kommen NUR
            // aus der Umgebung oder aus keystore/keystore.properties (gitignored) —
            // keine Defaults im Buildfile. Ohne Credentials bleibt das Release-APK
            // unsigniert (Debug-Builds sind davon unberührt).
            val props = Properties().apply {
                val f = rootProject.file("keystore/keystore.properties")
                if (f.exists()) f.inputStream().use { load(it) }
            }
            fun credential(envKey: String, propKey: String): String? =
                System.getenv(envKey) ?: props.getProperty(propKey)
            val ksPassword = credential("KEYTAB_KEYSTORE_PASSWORD", "KEYTAB_KEYSTORE_PASSWORD")
            val keyPassword = credential("KEYTAB_KEY_PASSWORD", "KEYTAB_KEY_PASSWORD")
            if (ksPassword != null && keyPassword != null) {
                signingConfig = signingConfigs.create("release") {
                    storeFile = file(
                        System.getenv("KEYTAB_KEYSTORE")
                            ?: props.getProperty("KEYTAB_KEYSTORE")
                            ?: rootProject.file("keystore/keytab-release.jks").absolutePath
                    )
                    storePassword = ksPassword
                    this.keyAlias = credential("KEYTAB_KEY_ALIAS", "KEYTAB_KEY_ALIAS") ?: "keytab"
                    this.keyPassword = keyPassword
                }
            } else {
                logger.warn(
                    "Release-Signing übersprungen: KEYTAB_KEYSTORE_PASSWORD/KEYTAB_KEY_PASSWORD " +
                        "fehlen (Env oder keystore/keystore.properties). Release-APK bleibt unsigniert."
                )
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
    // aarch64-Hosts: Conscrypt 2.5.2 (Robolectric-Transitiv) hat keine linux-aarch_64
    // Native-Bibliothek; 2.6.3 bringt libconscrypt_openjdk_jni-linux-aarch_64.so mit
    // (in /usr/lib/jni installiert) und hebt die Klassen auf die gleiche Version.
    testImplementation("org.conscrypt:conscrypt-openjdk-uber:2.6.3")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
}
