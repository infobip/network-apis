# Infobip Number Verification

## About

Backend for the Network APIs mobile application. It starts Silent Mobile Verification (SMV)
and CAMARA Number Verify (NV) flows through the Infobip API over HTTPS and exposes their results to the app.
It also serves the SIM-based verify phone number and get phone number flows (TS.43), which answer synchronously.

It is a plain Spring Boot application: it builds from Maven Central and runs as an executable jar or a Docker image.

## Usage

All endpoints are served under `/napi`. Every endpoint except the token endpoint, the SMV callback and the CAMARA
redirect target requires an `Authorization` header, either `Bearer {access_token}` with a token issued by
`/napi/oauth2/token` or `Basic` with one of the configured client IDs and secrets. Anything else returns `401`.

Infobip API calls send `infobip.authorization-header-value` unchanged as the `Authorization` header, so it must be the
complete value including the scheme, for example `App ...`, `Basic ...`, `Bearer ...` or `IBSSO ...`. The calls run on
the account that owns those credentials, which need the `mobile-identity:manage` scope.

| Method | Path | Description |
|---|---|---|
| POST | `/napi/oauth2/token` | OAuth2 client credentials grant, form-encoded `grant_type=client_credentials&client_id=...&client_secret=...`. Only the configured clients are accepted; this is an illustrative implementation, and integrators should replace it with their own authorization service. Returns `{access_token, token_type, expires_in}`; the access token is an HS256 JWT. An unsupported grant type or missing credentials return `400`, wrong credentials return `401`, both with `{error, error_description}`. |
| GET | `/napi/verify/{verificationType}/{msisdn}` | `verificationType` is `smv` for Silent Mobile Verification or `nv` for CAMARA Number Verify, which uses the client ID from `verify.camara-client-id` and returns `400` when it is not configured. Returns `{token, deviceRedirectUrl}`; an unknown type or malformed MSISDN returns `400`. |
| GET | `/napi/verify/result/{token}` | `204` while the result is pending or when the token is unknown or expired, `200` with `{token, result, errorDescription}` once available. Repeated reads return the same result until it expires. |
| POST | `/napi/verify/callback` | SMV result callback from the Infobip API. Results for tokens not issued by this backend are ignored. |
| GET | `/napi/verify/camara-authorize-complete` | CAMARA redirect target. `204` when the auth code was received, `422` when authorization failed; in both cases the result becomes available on the result endpoint. |
| POST | `/napi/sim-based/verify/dcql` | Body `{carrierIds}` (not empty). Returns `{nonce, carrierId, credentialRequest}` for verify phone number; `credentialRequest` is a JSON string for the app's `GetDigitalCredentialOption`. |
| POST | `/napi/sim-based/verify` | Body `{vpToken, phoneNumber}`. Returns `{verified}` in the response; there is no result endpoint. |
| POST | `/napi/sim-based/device-phone-number/dcql` | Same as `/sim-based/verify/dcql`, for get phone number. |
| POST | `/napi/sim-based/device-phone-number` | Body `{vpToken}`. Returns `{phoneNumber}` in the response; there is no result endpoint. |
| GET | `/napi/device-address` | Returns the device IP address and port as seen by the backend. |
| GET | `/actuator/health` | Health check, no authorization. |

The `/napi/sim-based/**` endpoints return `400` with `{errorDescription}` for invalid bodies and Infobip API rejections.

The backend calls these Infobip API endpoints:

| Flow | Infobip API endpoint |
|---|---|
| SMV start | `POST /mi/verification/1/verify` |
| NV authorization (device redirect built by the backend) | `GET /camara/number-verification/v0/authorize` |
| NV completion (auth code in `X-Correlator`) | `POST /camara/number-verification/v0/verify` |
| SIM-based credential request | `POST /camara/number-verification/v2/dcql/verify`, `POST /camara/number-verification/v2/dcql/device-phone-number` |
| SIM-based verify phone number (vp_token in `X-Correlator`) | `POST /camara/number-verification/v2/verify` |
| SIM-based get phone number (vp_token in `X-Correlator`) | `GET /camara/number-verification/v2/device-phone-number` |

### Configuration

Every setting is an environment variable. Variables marked required have no default and the backend does not start
without them.

| Property | Environment variable | Default | Description |
|---|---|---|---|
| `verify.domain` | `ENTERPRISE_BACKEND_DOMAIN` | required | Public base URL of this backend, for example `https://enterprise-backend.example.com`. Used for the SMV callback URL and the CAMARA redirect URL, so it must be reachable by the devices and by the Infobip API. |
| `infobip.base-url` | `INFOBIP_BASE_URL` | required | Your Infobip API base URL, for example `https://xxxxx.api.infobip.com` or `https://api-xx.infobip.com`. |
| `infobip.authorization-header-value` | `INFOBIP_AUTHORIZATION_HEADER_VALUE` | required | Complete `Authorization` header value sent to the Infobip API, including the scheme: `App`, `Basic`, `Bearer` or `IBSSO`. |
| `auth.clients[0].client-id` | `ENTERPRISE_CLIENT_ID` | required | Client ID the app authenticates with, as `client_id` on the token endpoint or as the Basic username. (THIS IS FOR THE EXAMPLE OF THE BASIC AUTHENTICATION FLOW, THE PRODUCTION IMPLEMENTATION SHOULD NOT USE THIS.)|
| `auth.clients[0].client-secret` | `ENTERPRISE_CLIENT_SECRET` | required | Client secret the app authenticates with, as `client_secret` on the token endpoint or as the Basic password.  (THIS IS FOR THE EXAMPLE OF THE BASIC AUTHENTICATION FLOW, THE PRODUCTION IMPLEMENTATION SHOULD NOT USE THIS.)|
| `auth.token-secret` | `ENTERPRISE_AUTH_TOKEN_SECRET` | required | Secret used to sign and verify access tokens. Use at least 32 random bytes.  (THIS IS FOR THE EXAMPLE OF THE BASIC AUTHENTICATION FLOW, THE PRODUCTION IMPLEMENTATION SHOULD NOT USE THIS.)|
| `server.port` | `SERVER_PORT` | `8080` | HTTP port. |
| `verify.camara-client-id` | `CAMARA_CLIENT_ID` | not set | CAMARA client ID sent in the Number Verify authorize URL. Required for `nv` verifications. |
| `verify.using-proxy` | `VERIFY_USING_PROXY` | `false` | Resolve the device address from the last `X-Forwarded-For` entry instead of the socket address. Set to `true` behind a reverse proxy or load balancer. |
| `verify.result-ttl` | | `5m` | How long pending and completed results are kept. |
| `verify.authorization-state-ttl` | | `5m` | How long CAMARA authorization states are kept. |
| `verify.max-entries` | | `10000` | Upper bound on entries in each in-memory store. |
| `auth.token-ttl` | | `1h` | Lifetime of issued access tokens. |
| `infobip.connect-timeout` | | `2s` | Connect timeout for Infobip API calls. |
| `infobip.read-timeout` | | `10s` | Read timeout for Infobip API calls. |

### Enterprise clients
`ENTERPRISE_CLIENT_ID` and `ENTERPRISE_CLIENT_SECRET` configure a single client. To accept several clients, list them
under `auth.clients` in `application.yml`, or set them as indexed environment variables. These replace the whole list,
so `ENTERPRISE_CLIENT_ID` and `ENTERPRISE_CLIENT_SECRET` are then ignored:

```bash
export AUTH_CLIENTS_0_CLIENTID=first-client AUTH_CLIENTS_0_CLIENTSECRET=...
export AUTH_CLIENTS_1_CLIENTID=second-client AUTH_CLIENTS_1_CLIENTSECRET=...
```
**_THIS IS FOR THE EXAMPLE OF THE BASIC AUTHENTICATION FLOW, THE PRODUCTION IMPLEMENTATION SHOULD NOT USE THIS._**

### Deployment requirements

- `ENTERPRISE_BACKEND_DOMAIN` must be a public HTTPS URL. The device follows a redirect to it at the end of the CAMARA flow,
  and the Infobip API delivers the SMV result to `{ENTERPRISE_BACKEND_DOMAIN}/napi/verify/callback`.
- The app must be built with the same domain (see the [mobile application README](../mobile-application/README.md)).
- Verification state is kept in memory. The service must run as a single instance: the start request, the SMV callback,
  the CAMARA redirect and the result polling have to reach the same instance.
- SMV needs the device IP address and port. Behind a reverse proxy set `VERIFY_USING_PROXY=true` and make the proxy
  append the client address to `X-Forwarded-For`, as `ip:port` if it can. Without a port the backend sends port `0`.

## Running

Requirements: JDK 21, or Docker.

Build and test:

```bash
./mvnw clean verify
java -jar infobip-napi-enterprise-backend/target/enterprise-backend-service-*.jar
```

Docker:

```bash
docker build -t enterprise-backend .
docker run --rm -p 8080:8080 \
  -e ENTERPRISE_BACKEND_DOMAIN=https://enterprise-backend.example.com \
  -e ENTERPRISE_CLIENT_ID=... -e ENTERPRISE_CLIENT_SECRET=... -e ENTERPRISE_AUTH_TOKEN_SECRET=... \
  -e INFOBIP_BASE_URL=https://xxxxx.api.infobip.com -e INFOBIP_AUTHORIZATION_HEADER_VALUE="..." \
  enterprise-backend
curl localhost:8080/actuator/health
```
