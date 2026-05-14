## AI Assistant

Android client for local and remote AI provider access.

### Build

- Android Studio
- JDK 17
- Android SDK 36
- Kotlin
- Jetpack Compose
- Room
- DataStore
- OkHttp

`google-services.json` is only required for builds that depend on Google services.

### Quick start

1. Clone the repository.
2. Initialize submodules if needed:
   - `git submodule update --init --recursive`
3. Copy `local.properties.example` to `local.properties`.
4. Update `sdk.dir` to your local Android SDK path.
5. Open the project in Android Studio and let Gradle sync.

### Build APK

- Debug APK:
  - `gradlew.bat assembleDebug`
- Release APK:
  - Requires a valid signing config in `local.properties`
  - `gradlew.bat assembleRelease`

### Notes

- `local.properties` is machine-specific and should not be committed.
- `release` builds require your own keystore settings.
- If `google-services.json` is absent, Firebase-related features are disabled automatically for unsupported builds.
