plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.jetbrains.compose) apply false
}

tasks.register("verifyJvm") {
    group = "verification"
    description = "Runs shared JVM tests and compiles the desktop application."
    dependsOn(
        ":core:model:jvmTest",
        ":core:network:jvmTest",
        ":core:data:jvmTest",
        ":core:designsystem:jvmTest",
        ":core:player:jvmTest",
        ":feature:player:jvmTest",
        ":feature:search:jvmTest",
        ":app:jvmTest",
        ":desktopApp:compileKotlin",
    )
}
