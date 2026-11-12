# Infobip Network APIs Demo

This repository demonstrates how to integrate Infobip's Network APIs from your own backend and mobile app. It covers:

* [Silent Mobile Verification](https://www.infobip.com/docs/mobile-identity/services/#silent) (SMV)
* CAMARA Number Verification, with the device redirected through the mobile network operator
* SIM-based Number Verification (NV 2.0), built on GSMA TS.43 and Android `CredentialManager` (Android only)

The demo has two parts: an enterprise backend that calls the Infobip API, and a mobile app that never calls Infobip directly and goes through that backend instead. More information is on the [Wiki](https://github.com/infobip/network-apis/wiki).
Published under the [Apache 2.0 license](LICENSE).

## Repository layout

| Path | Description |
|---|---|
| [`number-verification/backend`](number-verification/backend/README.md) | Enterprise backend, Spring Boot (Kotlin). Starts SMV and CAMARA Number Verification flows, serves the SIM-based flows, and returns the results to the app. |
| [`number-verification/mobile-application`](number-verification/mobile-application/README.md) | Mobile app for Android and iOS, built with Kotlin Multiplatform and Compose Multiplatform. |
| [`number-verification-2.0/docs`](number-verification-2.0/docs/Developer_guide_for_NV2_Infobip.md) | NV 2.0 developer guide, with sequence diagrams. |

## Requirements

### Backend
* JDK 21, or Docker

### Mobile application
* JDK 21
* Android Studio or IntelliJ IDEA with Kotlin Multiplatform support
* Android: Android SDK. Minimum API level 26 (Android 8.0), target API level 36.
* iOS: macOS with Xcode. Minimum deployment target iOS 18.2.

## Quick start guide

    $ git clone https://github.com/infobip/network-apis.git
    $ cd network-apis

### Get API key and base URL
The backend uses the [API Key Header](https://www.infobip.com/docs/essentials/api-authentication#api-key-header) authentication method to communicate with Infobip's Network APIs. Other authentication methods are also available (see [the official documentation](https://www.infobip.com/docs/essentials/api-authentication)).
Once you have an [Infobip account](https://www.infobip.com/signup), you can manage your API keys on the Infobip [API key management page](https://portal.infobip.com/settings/accounts/api-keys). The key needs the `mobile-identity:manage` scope.
To find your base URL, log in to the [Infobip API Resource](https://www.infobip.com/docs/api) hub with your Infobip credentials, or visit your [Infobip account](https://portal.infobip.com/homepage/).

### Run the backend
The backend is configured with environment variables only. These are the required ones:

    $ export ENTERPRISE_BACKEND_DOMAIN=https://enterprise-backend.example.com
    $ export INFOBIP_BASE_URL=https://xxxxx.api.infobip.com
    $ export INFOBIP_AUTHORIZATION_HEADER_VALUE="<autorization-header-value>"
    $ export ENTERPRISE_CLIENT_ID=... ENTERPRISE_CLIENT_SECRET=... ENTERPRISE_AUTH_TOKEN_SECRET=...
    $ cd number-verification/backend
    $ ./mvnw clean verify
    $ java -jar infobip-napi-enterprise-backend/target/enterprise-backend-service-*.jar

To use CAMARA Number Verification (`nv`), also set `CAMARA_CLIENT_ID`. The [backend README](number-verification/backend/README.md) lists every setting, every endpoint, and how to run the backend with Docker.

`ENTERPRISE_BACKEND_DOMAIN` must be a public HTTPS URL that both the devices and the Infobip API can reach. The Infobip API sends SMV results to `{ENTERPRISE_BACKEND_DOMAIN}/napi/verify/callback`, and at the end of the CAMARA flow the device is redirected back to the backend.

### Run the mobile application
Build the app with the same backend domain:

    $ cd number-verification/mobile-application
    $ ./gradlew :androidApp:assembleDebug -PbackendDomain=https://enterprise-backend.example.com

For a backend running locally, use `-PbackendDomain=http://10.0.2.2:8080` on the Android emulator. To run on iOS, open `iosApp` in Xcode. In the app settings, sign in with `ENTERPRISE_CLIENT_ID` and `ENTERPRISE_CLIENT_SECRET`. The [mobile application README](number-verification/mobile-application/README.md) covers iOS setup, the authorization options, tests and release signing.
