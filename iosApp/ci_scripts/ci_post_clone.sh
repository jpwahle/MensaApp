#!/bin/sh
# Xcode Cloud post-clone script for Kotlin Multiplatform / Compose Multiplatform.
#
# Apple's Xcode Cloud runner has no JDK on PATH, so the
# ":composeApp:embedAndSignAppleFrameworkForXcode" Gradle task (invoked by the
# "Compile Kotlin Framework" build phase in iosApp.xcodeproj) fails with
# "Unable to locate a Java Runtime."
#
# Note: env vars exported here do NOT propagate to xcodebuild's child shells.
# We therefore pin the JDK via ~/.gradle/gradle.properties (org.gradle.java.home),
# which Gradle picks up regardless of the parent process's environment.

set -euxo pipefail

# 1. Install JDK 21 (Homebrew is preinstalled on Xcode Cloud images)
brew install --quiet openjdk@21

# 2. Compute and verify the JDK path
JDK_HOME="$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home"
if [ ! -x "$JDK_HOME/bin/java" ]; then
  echo "ERROR: java binary not found at $JDK_HOME/bin/java"
  ls -la "$(brew --prefix openjdk@21)" || true
  ls -la "$(brew --prefix openjdk@21)/libexec" || true
  exit 1
fi

# 3. Pin JDK in ~/.gradle/gradle.properties so xcodebuild's "Compile Kotlin
#    Framework" build phase finds it. (Env vars from this script do not
#    propagate to xcodebuild subprocesses; properties files do.)
mkdir -p "$HOME/.gradle"
touch "$HOME/.gradle/gradle.properties"
# Strip any prior pin so re-runs don't accumulate duplicates
sed -i.bak '/^org\.gradle\.java\.home=/d' "$HOME/.gradle/gradle.properties"
rm -f "$HOME/.gradle/gradle.properties.bak"
echo "org.gradle.java.home=$JDK_HOME" >> "$HOME/.gradle/gradle.properties"

# 4. Skipping /Library/Java/JavaVirtualMachines symlink — Xcode Cloud's CI
#    user has no passwordless sudo, and the "Compile Kotlin Framework" build
#    phase in iosApp.xcodeproj finds the brew JDK directly via its discovery
#    loop ($(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home).

export JAVA_HOME="$JDK_HOME"
java -version

# 5. Pre-warm Gradle: download dependencies and build the Kotlin framework
#    Gradle-side so xcodebuild's "Compile Kotlin Framework" phase has a hot cache.
#    embedAndSignAppleFrameworkForXcode can't be invoked directly here (it needs
#    xcodebuild env vars: CONFIGURATION, SDK_NAME, ARCHS, BUILT_PRODUCTS_DIR).
#    Instead pre-warm the underlying linker task that matches the action
#    xcodebuild runs next, keyed off CI_XCODEBUILD_ACTION. An "Archive - iOS"
#    workflow builds the RELEASE framework for the arm64 device (this is what
#    App Store / TestFlight uploads use) — pre-warming Debug here just forces a
#    cold Release compile during the archive.
#      archive                       -> Release / device
#      test / test-without-building  -> Debug / simulator
#      build (default)               -> Debug / device
case "${CI_XCODEBUILD_ACTION:-}" in
  archive)
    PREWARM_TASK=":composeApp:linkReleaseFrameworkIosArm64"
    ;;
  test|test-without-building)
    PREWARM_TASK=":composeApp:linkDebugFrameworkIosSimulatorArm64"
    ;;
  *)
    PREWARM_TASK=":composeApp:linkDebugFrameworkIosArm64"
    ;;
esac

cd "$CI_PRIMARY_REPOSITORY_PATH"
chmod +x ./gradlew
./gradlew --no-daemon "$PREWARM_TASK"
