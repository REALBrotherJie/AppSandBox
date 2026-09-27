# Android Build Environment

- JDK: Android Studio bundled JDK 21; Gradle 8.13.
- SDK packages: `platform-tools`, `platforms;android-36`, `build-tools;35.0.0`, `emulator`, and one API36 x86_64 system image.
- Keep `ANDROID_HOME`, `ANDROID_SDK_ROOT`, Gradle `sdk.dir`, `adb`, and `emulator` on the same SDK root.
- API31 validation uses the existing physical device where available.
- API36 validation uses one cold-booted AVD and waits for `sys.boot_completed=1`.
- Accept licenses with the official SDK manager; do not copy license hashes from another machine.
