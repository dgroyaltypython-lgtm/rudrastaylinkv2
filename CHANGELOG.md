# Changelog

## 1.0.2
- Fixed Codemagic Gradle configuration failure with Kotlin Gradle Plugin 2.3.21 by migrating the deprecated `kotlinOptions` block to `kotlin.compilerOptions`.
- Configured JVM bytecode target 17 with typed `JvmTarget.JVM_17` and moved `kotlin.RequiresOptIn` to `compilerOptions.optIn`.
- Incremented Android versionCode to 2 and versionName to 1.0.1 for the next debug/release build.

## 1.0.1
- Codemagic scripts now discover the Android Gradle project when it is nested inside the repository.
- Build scripts write `local.properties` in the actual Android project directory.
- Added strict shell error handling, Gradle stack traces and info logging, and retained build logs as artifacts.
- Added explicit Android SDK and release signing checks with clearer error messages.

## 1.0.0
- Initial Rudra StayLink Android owner app project.
