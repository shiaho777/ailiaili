@file:OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)

import java.net.URI
import java.security.MessageDigest
import java.util.HexFormat
import java.util.zip.ZipInputStream

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.compose)
}

val miSansArchive = "https://hyperos.mi.com/font-download/MiSans.zip"
val miSansEntry = "MiSans/ttf/MiSans-Regular.ttf"
val miSansSha256 = "9c120f0a849bc0aa5048daae2a3c0f6eecd828b5b33fce682a9622833f5feea6"
val miSansResources = layout.buildDirectory.dir("generated/misans")
val prepareMiSans by tasks.registering {
    inputs.property("archive", miSansArchive)
    inputs.property("entry", miSansEntry)
    inputs.property("sha256", miSansSha256)
    outputs.dir(miSansResources)
    outputs.cacheIf { true }

    doLast {
        val connection = URI.create(miSansArchive).toURL().openConnection().apply {
            connectTimeout = 15_000
            readTimeout = 60_000
        }
        val fontBytes = ZipInputStream(connection.getInputStream().buffered()).use { archive ->
            while (true) {
                val entry = archive.nextEntry ?: error("MiSans Regular is missing from the Xiaomi archive")
                if (entry.name == miSansEntry) break
            }
            archive.readNBytes(8_122_325)
        }
        val actualSha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(fontBytes))
        check(actualSha256 == miSansSha256) { "Xiaomi's MiSans Regular checksum has changed" }
        miSansResources.get().file("font/misans_regular.ttf").asFile.apply {
            parentFile.mkdirs()
            writeBytes(fontBytes)
        }
    }
}

compose.resources {
    packageOfResClass = "app.ailiaili.core.designsystem.resources"
    customDirectory("jvmShared", prepareMiSans.map { miSansResources.get() })
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
        )
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.components.resources)
            }
        }
        val jvmShared by creating {
            dependsOn(commonMain)
            dependencies {
                implementation(libs.coroutines.core)
                implementation(project(":core:model"))
                implementation(project(":core:data"))
                implementation(project(":core:network"))

                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(libs.material3)
                implementation(compose.ui)
                implementation(compose.materialIconsExtended)
                implementation(libs.lifecycle.viewmodel)
                implementation(libs.lifecycle.runtime)
                implementation(libs.coil.compose)
                implementation(libs.ui.backhandler)

            }
        }
        val androidMain by getting {
            dependsOn(jvmShared)
            dependencies {
                implementation(libs.coroutines.android)
                implementation(libs.core.ktx)
            }
        }
        val jvmMain by getting {
            dependsOn(jvmShared)
            dependencies {
                implementation(libs.coroutines.swing)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(compose.uiTest)
                implementation(compose.desktop.currentOs)
            }
        }

    }
}

android {
    namespace = "app.ailiaili.core.designsystem"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
