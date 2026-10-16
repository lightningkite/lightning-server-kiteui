# Changelog

## Unreleased

### Telemetry

- **Added `telemetryUpload`** for KiteUI's `Telemetry`: `upload = telemetryUpload { api.clientTelemetry.ingest(it) }`. It drops batches the server refused for good (a 4xx other than 401, 408 or 429) and rethrows everything else so `Telemetry` retries them. Needs Lightning Server's `ClientTelemetryEndpoints` mounted on the server.

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
