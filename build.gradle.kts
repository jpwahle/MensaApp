plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
}
// Pin transitive npm packages of the wasmJs toolchain to patched versions
// (kotlin-js-store/wasm/yarn.lock). Dependabot cannot update that lockfile
// because it has no package.json next to it. After changing a pin, run
// ./gradlew kotlinWasmUpgradeYarnLock
plugins.withType<org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnPlugin> {
    the<org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnRootExtension>().apply {
        resolution("ws", "8.21.0") // GHSA: memory disclosure (<8.20.1), fragment DoS (<8.21.0)
    }
}
