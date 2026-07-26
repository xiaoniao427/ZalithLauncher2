// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp.plugin) apply false
    kotlin("plugin.serialization") version libs.versions.kotlin apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.hilt) apply false
}

buildscript {
    repositories {
        maven { url = uri("https://repo1.maven.org/maven2/") }
        google()
        mavenCentral()
    }
    dependencies {
        classpath(libs.buildkeys)
    }
}