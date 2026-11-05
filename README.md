# Rudra StayLink — Owner Android App

**Android native application** for property owners. The app connects to the Rudra StayLink Django REST API and uses the same hosted database as the operator's web portal.

## What is included

- Owner-only JWT login using the backend's `/api/v1/auth/token/` endpoint.
- AES/GCM-encrypted access and refresh tokens stored with Android Keystore.
- Automatic JWT refresh on an expired access token.
- Owner dashboard with property, room, enquiry and unread-notification counts.
- Room list, search and status filters.
- Add/edit rooms, rates, capacity, facilities, notes and room photographs.
- Update Available / Occupied / Maintenance status, current guest and checkout date.
- Soft archive rooms (preserves enquiry history).
- Add/edit property contact and WhatsApp details.
- Enquiry status updates and notification read/mark-all-read actions.
- Change password and sign out.
- Responsive Compose UI for phones and tablets.
- Foreground notification polling every 30 seconds. Actual background push delivery requires a Firebase project and service credentials; this app does not claim to provide FCM until those are configured.

## Compatibility and toolchain

- Minimum Android: **7.0 (API 24)**.
- Compile / target SDK: **36 (Android 16)**.
- Java toolchain: **JDK 17**.
- Android Gradle Plugin: **8.13.2**.
- Gradle: **8.13**.
- Kotlin: **2.3.21**.
- Jetpack Compose BOM: **2026.06.01** (Compose 1.11.4; compatible with compileSdk 36 / AGP 8.13.2).

A custom `gradlew` / `gradlew.bat` bootstrap downloads Gradle 8.13 when it is not already present. It avoids requiring a separately installed global Gradle command. Android SDK 36 and platform build tools must still be installed (Android Studio's SDK Manager can install them).

## 1. Build with Android Studio on Windows

1. Install Android Studio and select the Android SDK Platform 36 and Build Tools 36.x in SDK Manager.
2. Install/select JDK 17 (Android Studio's bundled JBR is suitable when it is JDK 17).
3. Extract this project ZIP to a short path such as `C:\RudraStayLinkAndroid`.
4. Open the extracted folder in Android Studio as an existing Gradle project.
5. Wait for Gradle sync and SDK setup to finish.
6. Select **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
7. The debug APK will be created at:

   `app\build\outputs\apk\debug\app-debug.apk`

Or run in Command Prompt from the project folder:

```bat
gradlew.bat clean assembleDebug
```

The first build downloads Gradle and Android dependencies, so internet access is required.

## 2. Build from Codemagic

1. Push this extracted project to a Git repository.
2. In Codemagic, add the repository and select `codemagic.yaml` as the workflow definition.
3. Start **Rudra StayLink - Android Debug APK**.
4. Download `app-debug.apk` from the build artifacts.

For a production signed APK, upload your Android keystore in Codemagic under **Team settings → Code signing identities → Android keystores** and use the reference name `rudra_staylink_keystore`. The release workflow declares this reference using Codemagic's `android_signing` configuration; Codemagic supplies `CM_KEYSTORE_PATH`, `CM_KEYSTORE_PASSWORD`, `CM_KEY_ALIAS` and `CM_KEY_PASSWORD` to Gradle.

Then run **Rudra StayLink - Signed Release APK**. Keep the keystore and passwords private and backed up securely. Never commit them to Git. The local fallback release signing uses the debug key only for smoke testing and is **not suitable for Play Store distribution**.

## 3. Connect the app to the Django API

The app's login screen has **Connection settings**. Enter the base URL of the backend, including the trailing slash (the app adds it if missing).

### Production (after PythonAnywhere deployment)

```text
https://YOURDOMAIN.pythonanywhere.com/
```

Use your actual deployed HTTPS domain. Keep Django's `DEBUG=False`, valid `ALLOWED_HOSTS`, HTTPS enabled and a strong independent `JWT_SIGNING_KEY`.

### Local phone testing on the same Wi-Fi

1. Start the backend on the Windows computer:

   ```bat
   python manage.py runserver 0.0.0.0:8000
   ```

2. Find the computer's LAN IPv4 address with `ipconfig` (for example `192.168.1.5`).
3. Make sure that IP is allowed in Django `ALLOWED_HOSTS` and Windows Firewall allows inbound TCP port 8000 on the private network.
4. In the app's Connection settings enter:

   ```text
   http://192.168.1.5:8000/
   ```

   Replace the sample IP with the actual PC address.
5. A physical Android phone and the PC must be on the same Wi-Fi network. For the Android emulator, use `http://10.0.2.2:8000/`.

The debug build permits cleartext HTTP for local LAN testing. The release build explicitly disables cleartext traffic and requires HTTPS. **Do not use plain HTTP over public networks.** Use HTTPS for PythonAnywhere/production.

## 4. Owner accounts

The app does not contain public sign-up. The operator must create an owner account in the Django web portal and share the credentials privately. Superuser/operator accounts are intentionally rejected by the mobile owner token endpoint.

## 5. API operations used

The app uses the backend API v1 endpoints:

- `POST auth/token/`, `POST auth/token/refresh/`, `POST auth/logout/`
- `GET dashboard/`, `GET me/`
- `GET/POST/PATCH properties/`
- `GET/POST/PATCH/DELETE rooms/`, `PATCH rooms/{id}/availability/`, `POST rooms/{id}/restore/`
- `GET enquiries/`, `PATCH enquiries/{id}/status/`
- `GET notifications/`, `POST notifications/{id}/read/`, `POST notifications/read-all/`
- `POST auth/change-password/`

The backend remains the source of truth. Owner IDs are never sent by the app; the backend scopes all records to the verified JWT user.

## 6. Notifications: current behaviour and next step

While the app is open, it checks the server for new owner notifications every 30 seconds and posts Android notifications (permission requested on Android 13+). For delivery while the app is closed or the phone is asleep, configure Firebase Cloud Messaging in a follow-up release: add the project's `google-services.json`, Google Services Gradle plugin, FCM service and backend Firebase Admin credentials. The Django backend already has device-token registration support, but the supplied APK source does not yet claim background push delivery.

## 7. Troubleshooting

- **Cannot reach server:** verify the backend is running, the base URL, Wi-Fi, Windows Firewall and `ALLOWED_HOSTS`.
- **401 / owner login rejected:** verify the account was created as an active Property Owner, not as a superuser/operator. Reset the owner's password from Django admin if needed.
- **Cleartext not permitted:** confirm you are using this project's manifest and that the URL is only an HTTP LAN address. Production must use HTTPS.
- **Photo upload fails:** use a JPG/PNG/WebP image and keep it reasonably sized; also check PythonAnywhere media upload/storage configuration.
- **Gradle/JDK error:** use JDK 17, Gradle 8.13, and Android SDK Platform 36.
- **API schema mismatch:** this app is paired with the `Rudra_StayLink_Backend_0.2.0` API contract. Keep the backend and app versions aligned.

## Release note

This package contains Android source code and build workflows, not a precompiled APK. The runtime APK must be built with Android SDK/Gradle on Android Studio or Codemagic, then tested against your deployed API and on representative physical Android devices. No app can honestly be guaranteed to run on every Android phone/model; this version targets API 24+ and uses responsive layouts and Android-supported Jetpack components.

## Codemagic troubleshooting (v1.0.1)

The updated `codemagic.yaml` automatically locates `settings.gradle.kts` and `gradlew` even when the GitHub repository contains the `RudraStayLinkAndroid/` folder at its root. It writes `local.properties` into that project directory and runs Gradle with `--stacktrace --info`.

After a failed build, download `gradle-debug-build.log` from Codemagic Artifacts. The log records the first real Gradle error rather than only the generic exit code. If the build fails before that artifact is created, open the failed script step and copy the first error lines from its console output.

For a debug build, no release keystore is required. Configure the `rudra-staylink-android-debug` workflow first. Configure the `rudra_staylink_keystore` signing identity only before running the release workflow.

## Kotlin 2.3.21 build-script compatibility (v1.0.2)

This release uses the modern `kotlin { compilerOptions { ... } }` DSL. It intentionally does not use the legacy `kotlinOptions {}` block, because Kotlin Gradle Plugin 2.3.21 rejects that deprecated configuration during Gradle build-script compilation. JVM bytecode remains targeted at Java 17, and `kotlin.RequiresOptIn` is configured through `compilerOptions.optIn`.



## Compose / Android Gradle Plugin compatibility (v1.0.3)

The app pins Compose BOM `2026.06.01`, which selects the Compose 1.11.4 line. Do not change this to BOM `2026.08.00` or `2026.09.00` while keeping AGP 8.13.2 and `compileSdk = 36`: Compose 1.12 requires API 37 and AGP 9.1.1 or newer. This project deliberately keeps AGP 8.13.2, Gradle 8.13, JDK 17 and SDK 36 together for the current Codemagic workflow.

The Codemagic workflows now run Gradle `dependencyInsight` for `androidx.compose.runtime:runtime-saveable` before compilation and preserve the output as an artifact. Check that it resolves to Compose `1.11.4` if a later dependency update causes another AAR metadata failure.
