plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
        )
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(project(":app"))
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(libs.coroutines.swing)
}

compose.desktop {
    application {
        mainClass = "app.ailiaili.MainKt"
        nativeDistributions {
            packageName = "ailiaili"
            packageVersion = "1.3.0"
            description = "ailiaili"
            copyright = "ailiaili"
            vendor = "ailiaili"
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb,
            )
            macOS {
                bundleID = "app.ailiaili"
            }
            windows {
                menuGroup = "ailiaili"
                upgradeUuid = "b6e2f4a1-7c3d-4e58-9a0b-1d5f8c2e7a93"
            }
            linux {
                packageName = "ailiaili"
            }
        }
    }
}
