# Changelog

## Unreleased

### Networking

- **`ConnectivityFetcher` and `BulkFetcher` take `metadata: suspend RequestMetadata.() -> Unit`** in place of `calculator`, and implement `Fetcher.withMetadata`. Creating them with `calculator` is deprecated.
- **WebSocket metadata is now URL-encoded** when appended to the socket URL as query parameters.
- **`AuthClientEndpoints.accessToken(sessionToken, forceInvalidate, maxAge)` now returns an `AccessToken`** (`suspend () -> String`), the bare token, instead of a header-list lambda (breaking). `maxAge` (default 4 minutes) is how long a fetched token is reused. Send it with `api.withMetadata { set("Authorization", accessToken()) }`.
- **`LightningServerAuthentication.accessToken` is now an `AccessToken`**, not a header-list lambda.

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
