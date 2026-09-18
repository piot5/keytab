// Top-level build file
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    // Statische Analyse (Qualitäts-Gate neben Kover): detekt über Kotlin-Quellen
    id("io.gitlab.arturbosch.detekt") version "1.23.7" apply false
}
