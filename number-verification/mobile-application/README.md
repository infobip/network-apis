# Infobip network APIs mobile application

## About

Infobip Network APIs mobile application. A Kotlin Multiplatform / Compose Multiplatform client targeting Android and iOS
that runs Silent Mobile Verification and CAMARA Number Verification through the enterprise backend. On Android it
also runs SIM-based number verification and gets the device phone number from the SIM.

## Project layout

- [`/androidApp`](./androidApp) — the Android application.
- [`/iosApp`](./iosApp) — the iOS application (Xcode project).
- [`/shared`](./shared/src) — code shared across targets.
  - [`commonMain`](./shared/src/commonMain/kotlin) — shared by all targets.
  - [`androidMain`](./shared/src/androidMain/kotlin) — Android-specific code.
  - [`iosMain`](./shared/src/iosMain/kotlin) — iOS-specific code.

## What you need to install

Shared:

- Git
- JDK 21
- Android Studio or IntelliJ IDEA with Kotlin Multiplatform support

Android: Android SDK, plus an emulator or a physical device.

iOS: macOS, Xcode, Xcode Command Line Tools, and a JDK reachable via `JAVA_HOME`.

Dependencies are resolved from Google Maven, Maven Central and the Gradle Plugin Portal.

## First-time setup

1. Clone the repository.
2. Open `number-verification/mobile-application` in Android Studio or IntelliJ IDEA, or `iosApp` in Xcode, depending on the target.
3. Ensure `JAVA_HOME` points to your JDK in your shell or IDE.
4. On macOS, to build the iOS app create `iosApp/Configuration/Local.xcconfig` with your local JDK path and your
   Apple Developer team ID (needed to run on a device):

   ```xcconfig
   JAVA_HOME = /path/to/your/jdk/home
   DEVELOPMENT_TEAM = YOURTEAMID
   ```

   The file is gitignored so each developer keeps their own values. It can also override `PRODUCT_BUNDLE_IDENTIFIER`.
5. Sync Gradle once so dependencies and generated files are created.

## Backend domain

The backend domain is set at build time with the Gradle property `backendDomain` and compiled into
`com.infobip.napi.config.BuildConfig.BACKEND_DOMAIN`. It is the host used for every backend call, the access token
request, and for recognising the end of the device redirect chain. It must be the same URL the backend uses as its
own domain (`ENTERPRISE_BACKEND_DOMAIN`).

The default is `http://localhost:8080`, the backend's default port. If you run the backend on another host or port,
for example with your own `application-development.yml`, set `backendDomain` to match. Set it on the command line or
in `gradle.properties` (or `~/.gradle/gradle.properties`):

```bash
./gradlew :androidApp:assembleDebug -PbackendDomain=http://10.0.2.2:8080       # Android emulator, local backend
./gradlew :androidApp:bundleRelease -PbackendDomain=https://enterprise-backend.example.com
```

```properties
backendDomain=https://enterprise-backend.example.com
```

For iOS, set the property in `gradle.properties` before building in Xcode, because Xcode runs the Gradle build
itself.

Android debug builds allow cleartext HTTP only to `localhost` and `10.0.2.2`. Release builds and iOS require HTTPS.

## Running the apps

- Android: `./gradlew :androidApp:assembleDebug`
- iOS: open [`iosApp`](./iosApp) in Xcode and run it there on macOS

## Backend and settings

The app talks to the enterprise backend under the `/napi` path. See the [backend README](../backend/README.md) for
setting it up.

- **Verification type** is part of the verify path: `GET /napi/verify/smv/{msisdn}` for Silent Mobile
  Verification and `GET /napi/verify/nv/{msisdn}` for CAMARA Number Verify. The CAMARA client ID is configured on
  the backend, not in the app.
- **SIM-based verification** (Android only) uses `POST /napi/sim-based/verify/dcql` and
  `POST /napi/sim-based/verify` to verify a number, and `POST /napi/sim-based/device-phone-number/dcql` and
  `POST /napi/sim-based/device-phone-number` to get the device phone number. The app sends the SIM carrier IDs,
  passes the backend's credential request to Android `CredentialManager` and sends the returned `vp_token` back. The
  button and the *Get phone number* card are hidden on iOS.
- **Authorization** is configured in the app's settings:
  - *Username & Password*: enter the backend's `ENTERPRISE_CLIENT_ID` and `ENTERPRISE_CLIENT_SECRET`. The app requests a token
    from `{backendDomain}/napi/oauth2/token` and refreshes it before it expires.
  - *Authorization header*: send a credential with a selectable scheme, `Basic` (base64 of
    `ENTERPRISE_CLIENT_ID:ENTERPRISE_CLIENT_SECRET`) or `Bearer` (a token issued by the backend).

  The same `Authorization` header is sent on the verify request, the result poll and the device-address call.

## Running tests

- All shared tests: `./gradlew :shared:allTests`
- Android host tests: `./gradlew :shared:testAndroidHostTest`
- iOS simulator tests: `./gradlew :shared:iosSimulatorArm64Test`

## Android release signing

Release builds are signed with an **upload key**. Neither the keystore nor its credentials live in this repository —
the build reads them from environment variables only, so every release build needs them set:

| Variable | Meaning |
| --- | --- |
| `ANDROID_KEYSTORE_FILE` | Path to the keystore. Absolute, or relative to the root project. |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password. The key entry uses the same password — the PKCS12 format `keytool` writes by default cannot hold a separate one. |
| `ANDROID_KEY_ALIAS` | Alias of the key entry inside the keystore. |

If any of them is missing, the release build still succeeds but is **unsigned** and cannot be uploaded to Google
Play. The build logs a warning saying so.

```bash
export ANDROID_KEYSTORE_FILE=/path/to/upload-keystore.jks
export ANDROID_KEYSTORE_PASSWORD='...'
export ANDROID_KEY_ALIAS=upload

./gradlew :androidApp:signingReport   # release variant should show "Config: release"
./gradlew :androidApp:bundleRelease -PbackendDomain=https://enterprise-backend.example.com
```

Keep the keystore outside the repository working tree; `*.jks`, `*.keystore`, `*.p12`, `*.cer` and
`*.mobileprovision` are gitignored as a safety net, but the material should not be there in the first place. In CI,
provide the variables from the CI system's secret store.

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)
and [Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform/#compose-multiplatform).
