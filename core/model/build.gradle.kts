plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
}

// 版本号单一来源在 gradle.properties（appVersion）。生成 AppVersion 常量供
// 应用内更新检查与设置页显示，避免手写常量与构建版本脱节。
val generatedVersionDir = layout.buildDirectory.dir("generated/appversion/kotlin")
val generateAppVersion by tasks.registering {
    val version = providers.gradleProperty("appVersion")
    val output = generatedVersionDir
    inputs.property("appVersion", version)
    outputs.dir(output)
    doLast {
        val file = output.get().file("app/ailiaili/core/model/AppVersion.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            package app.ailiaili.core.model

            /** 由 Gradle 从 gradle.properties 的 appVersion 生成，请勿手改。 */
            const val APP_VERSION = "${version.get()}"
            """.trimIndent() + "\n",
        )
    }
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

    sourceSets {
        val commonMain by getting
        val jvmShared by creating {
            dependsOn(commonMain)
            kotlin.srcDir(generateAppVersion)
            dependencies {
                implementation(libs.coroutines.core)
            }
        }
        val androidMain by getting {
            dependsOn(jvmShared)
        }
        val jvmMain by getting {
            dependsOn(jvmShared)
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

    }
}

android {
    namespace = "app.ailiaili.core.model"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
