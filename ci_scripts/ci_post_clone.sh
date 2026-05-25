#!/bin/sh
# Xcode Cloud post-clone script for Kotlin Multiplatform / Compose Multiplatform.
# Apple's Xcode Cloud runner has no JDK on PATH by default, so the
# `:composeApp:embedAndSignAppleFrameworkForXcode` Gradle task (called from the
# "Compile Kotlin Framework" build phase in iosApp.xcodeproj) would fail.
# This script installs a JDK, exports JAVA_HOME, and pre-warms Gradle so the
# Xcode archive step has a cached Kotlin framework ready.

set -euxo pipefail

# 1. Install JDK 21 via Homebrew (preinstalled on Xcode Cloud images)
brew install --quiet openjdk@21

# 2. Make the JDK discoverable via /usr/libexec/java_home
sudo ln -sfn "$(brew --prefix)/opt/openjdk@21/libexec/openjdk.jdk" \
  /Library/Java/JavaVirtualMachines/openjdk-21.jdk

export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
echo "JAVA_HOME=$JAVA_HOME"
java -version

# 3. Pre-warm Gradle so the in-Xcode build phase reuses the framework
cd "$CI_PRIMARY_REPOSITORY_PATH"
chmod +x ./gradlew
./gradlew --no-daemon :composeApp:embedAndSignAppleFrameworkForXcode \
  -PXCODE_CONFIGURATION=Release \
  -PSDK_NAME=iphoneos
