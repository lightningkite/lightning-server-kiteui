# Changelog

## Unreleased

### Networking

- **`ConnectivityFetcher` and `BulkFetcher` take `metadata: suspend RequestMetadata.() -> Unit`** in place of `calculator`, and implement `Fetcher.withMetadata`. Creating them with `calculator` is deprecated.
- **WebSocket metadata is now URL-encoded** when appended to the socket URL as query parameters.
- **`AuthClientEndpoints.accessToken(sessionToken, forceInvalidate, maxAge)` now returns an `AccessToken`** (`suspend () -> String`), the bare token, instead of a header-list lambda (breaking). `maxAge` (default 4 minutes) is how long a fetched token is reused. Send it with `api.withMetadata { set("Authorization", accessToken()) }`.
- **`LightningServerAuthentication.accessToken` is now an `AccessToken`**, not a header-list lambda.
- **Added `AuthClientEndpoints.accessTokenFromCookie(forceInvalidate, maxAge)`**, which gets access tokens from the HttpOnly refresh-token cookie (`getTokenFromCookie`) with the same reuse rules as `accessToken`. After logging in, hand the refresh token to `setRefreshTokenCookie` instead of storing it. Call it on endpoints built over `ConnectivityFetcher(webIncludeCredentials = true)`, not `BulkFetcher`; a 401 on first use means logged out.
- **`ConnectivityFetcher` gained `webIncludeCredentials`** (send and accept cookies on cross-origin browser requests; the server's CORS must allow credentials), carried through `withMetadata`.
- Requires lightning-server with the cookie session endpoints (`subsession` and `SubSessionRequest` are gone) and KiteUI with `webIncludeCredentials` on `fetch`.

### Files

- **`FileReference.toServerFile` takes `ClientFileUploadEndpoints`** (what generated SDKs now emit) and follows its flow: `getFileUploadUrl()`, PUT the file, `verifyUpload(token)`. It now returns a non-null `ServerFile` and throws if the PUT is not successful. `ClientUploadEarlyEndpoints` is a deprecated typealias of the new interface in lightning-server, so existing callers still compile.

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
