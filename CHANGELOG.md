# Changelog

## Unreleased

### Networking

- **`ConnectivityFetcher` and `BulkFetcher` take `metadata: suspend RequestMetadata.() -> Unit`** in place of `calculator`, and implement `Fetcher.withMetadata`. Creating them with `calculator` is deprecated.
- **WebSocket metadata is now URL-encoded** when appended to the socket URL as query parameters.
- **`AuthClientEndpoints.accessToken(sessionToken, forceInvalidate, maxAge)` now returns an `AccessToken`** (`suspend () -> String`), the bare token, instead of a header-list lambda (breaking). `maxAge` (default 4 minutes) is how long a fetched token is reused. Send it with `api.withMetadata { set("Authorization", accessToken()) }`.
- **`LightningServerAuthentication.accessToken` is now an `AccessToken`**, not a header-list lambda.

### WebAuthn

- **WebAuthn now works on Android and iOS**, so `ClientAuthenticator` creates and uses passkeys and security keys there instead of reporting itself unavailable. JVM is still unsupported.
  - **Android** goes through Jetpack Credential Manager, which offers whatever the user's credential providers support. Google Play services covers synced passkeys and FIDO2 security keys over USB and NFC. Android 14+ needs nothing more. To support Android 9–13, add `androidx.credentials:credentials-play-services-auth` to the app; the library leaves it out so apps don't pull in Play services unless they choose to. Serve `https://<rpId>/.well-known/assetlinks.json` with the `delegate_permission/common.get_login_creds` relation for the app.
  - **iOS 15+** goes through AuthenticationServices, offering iCloud Keychain passkeys and security keys over NFC, USB-C or Lightning. Add a `webcredentials:<rpId>` Associated Domains entitlement and serve an apple-app-site-association file from the rpId domain.
  - The server's `allowedOrigins` must include `https://<rpId>` for iOS and `android:apk-key-hash:<hash>` for each Android signing key.
  - Passkey autofill (`autofillAvailable()`) is not supported on either platform yet.
- **WebAuthn behaves the same on every platform.**
  - Failures throw a `PlainTextException` whose message depends only on the kind of failure: cancelled, timed out or no usable credential; already registered; not set up for this app; not supported; or other, with the platform's description appended. Previously each platform showed its own error text, such as iOS's "AuthorizationError error 1001".
  - Binary values (attestation object, client data, signature, user handle, large blob) are unpadded base64url on every platform. The JS client used to pad them. The server accepts both.
  - Unknown transports such as `smart-card` are dropped instead of failing the registration in JS, and a missing `authenticatorAttachment` is inferred from the transports instead of crashing.
  - The request's `timeout` is enforced on Android and iOS, failing like a browser's timeout.
  - `Silent` mediation only offers credentials already on the device on iOS 16+, as it does on Android.
  - iOS supports the `largeBlob` extension from iOS 17, and reports `credProps` only when the server asks for it.
  - Asking to read and write a large blob at once is rejected as not supported, as browsers do.
  - The JVM stub throws the not-supported failure instead of `TODO()`.
- **Fixed the JS client ignoring the server's `rpId` and `hints` when signing in.** It set `rp` instead of `rpId`, so browsers fell back to the page's own domain, which fails when the app runs on a subdomain of the rpId. The `PublicKeyCredentialRequestOptions` external now has `rpId` and `hints` in place of `rp`.
- **Fixed the JS client writing a large blob as the UTF-8 bytes of its base64 text.** It now writes the decoded bytes, as the model documents and Android does.
- **Fixed the JS client crashing on a large blob read**, which reports no `written` flag.

### Auth Components

- **Added stable `debugName` values to all interactive auth elements**, making it possible for AI driver tests and automated test suites to reliably target every interactive element without fragile index-based paths.

  **`AuthComponent` / `authComponent()`**
  - `"primaryInput"` — email/phone/username text input
  - `"cancelButton"` — back/cancel button shown while proofs are in progress
  - `"usePasskeyButton"` — passkey/WebAuthN trigger button
  - `"rememberDeviceCheckbox"` — "This is my device" checkbox
  - `"keepLoggedInCheckbox"` — extended session duration checkbox
  - `"loginButton"` — final login button in the finalization screen
  - Proof picker buttons use the proof method name as `debugName` (e.g., `"Email Code"`, `"Text Code"`, `"Enter Password"`, `"Use Authenticator App"`, `"Enter Backup Code"`)

  **`ReAuthComponent` / `reAuthComponent()`**
  - `"loginButton"` — final login button
  - Proof picker buttons use the proof method name as `debugName` (same as above)

  **`EmailProofComponent`**
  - `"codeInput"` — email verification code text input
  - `"submitButton"` — submit code button
  - `"resendButton"` — resend code button

  **`SmsProofComponent`**
  - `"codeInput"` — SMS verification code text input
  - `"submitButton"` — submit code button
  - `"resendButton"` — resend code button

  **`PasswordProofComponent`**
  - `"passwordInput"` — password text input
  - `"submitButton"` — submit button

  **`TotpProofComponent`**
  - `"codeInput"` — TOTP code text input
  - `"submitButton"` — submit button

  **`BackupCodeProofComponent`**
  - `"codeInput"` — backup code text input
  - `"submitButton"` — submit button
