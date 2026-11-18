# Auth screens

Lightning Server signs users in with *proofs*: an emailed code, a password, an authenticator app, a passkey, and so
on.  Each proof has a strength, and the server decides how much strength a subject needs.  This library renders
that whole conversation for you:

| Component | Use it for |
|---|---|
| `AuthComponent` / `authComponent()` | The login screen. |
| `ReAuthComponent` / `reAuthComponent()` | Re-proving identity before a sensitive action; issues a short session. |
| `SecurityComponent` | A "Security" settings page: password, authenticator app, passkeys, backup codes. |

All three live in `com.lightningkite.kiteui.auth` and take an `AuthEndpoints`, built from the generated SDK.

## Minimal login page

```kotlin
@Routable("/login")
class LoginPage : Page {
    override fun ElementWriter.CanAddTheme.render() {
        col { centered.sizeConstraints(maxWidth = 40.rem).col {
            h1("Sign in")
            authComponent(
                endpoints = AuthEndpoints(
                    subjects = mapOf("User" to api.userAuth),
                    emailProof = api.userAuth.email,
                    passwordProof = api.userAuth.password,
                    oneTimePasswordProof = api.userAuth.totp,
                    backupCodeProof = api.userAuth.backupCode,
                ),
                onAuthentication = { token ->
                    sessionToken.value = token          // see api-client.md, "Sessions"
                    context.pageNavigator.reset(HomePage())
                },
            )
        } }
    }
}
```

`api` is the signed-out `LiveApi`.  Pass only the proofs your server mounts; each one you leave `null` disappears
from the UI.  The token handed to `onAuthentication` is the long-lived session token.

The flow the user sees: type an email or phone number (detected from the format), pick a proof, complete it, repeat
until the server's required strength is met, then choose "Keep me logged in" and, if the server offers known
devices, "This is my device".

## The server side

The component needs, per subject, an `AuthEndpoints` session module plus a module per proof.  The demo's
[`UserAuth.kt`](../demo-server/src/main/kotlin/com/lightningkite/lskiteuistarter/UserAuth.kt) mounts email,
TOTP, password and backup-code proofs; Lightning Server's guide (`proof-session.md`) explains each.  Which proofs a
user must combine is the server's `requiredProofStrengthFor`, not anything in the client.

| `AuthEndpoints` parameter | Server module |
|---|---|
| `subjects` | a subclass of `AuthEndpoints<User, ID>`, the session endpoints (generated as `api.userAuth`) |
| `emailProof` / `smsProof` | `EmailProofEndpoints` / `SmsProofEndpoints` |
| `passwordProof` | `PasswordProofEndpoints` |
| `oneTimePasswordProof` | `TimeBasedOTPProofEndpoints` |
| `backupCodeProof` | `BackupCodeEndpoints` |
| `webAuthNProof` | `WebAuthNProofEndpoints` (`sessions-webauthn`) |
| `knownDeviceProof` | `KnownDeviceProofEndpoints` |

## Common tasks

**React to what the user typed.**  Build the class instead of calling the DSL; its signals are public.

```kotlin
val auth = AuthComponent(endpoints, onAuthentication = { token -> sessionToken.value = token })
reactive { if (auth.rawPrimaryInput() == "i am a dev") showServerPicker.value = true }
auth.render(this)
```

**Hide or reorder methods.**  `filterMethods` sees who is signing in and the offered proofs:

```kotlin
authComponent(
    endpoints,
    filterMethods = { who, methods -> if (who?.property == "phone") methods.filter { it.via != "email" } else methods },
    onAuthentication = { ... },
)
```

**Allow usernames** as the identifier: `supportUsernames = true`.

**Turn off known devices:** `knownDeviceLocalStorageName = null`.

**Re-authenticate before something dangerous:**

```kotlin
context.dialog { close ->
    col {
        reAuthComponent(
            endpoints = endpoints,
            subjectId = session.userId.toString(),
            onAuthentication = { shortLivedToken -> deleteAccount(shortLivedToken); close() },
        )
    }
}
```

The new session lasts `newSessionDuration` (15 minutes by default) and only offers proofs the user already set up.

**A security settings page.**  `SecurityComponent` re-authenticates the user itself, then calls `elevate` with the
fresh token so changes go out on an elevated session:

```kotlin
val security = SecurityComponent(
    endpoints = endpoints,
    subjectId = session.userId.toString(),
    elevate = { token ->
        val accessToken = api.userAuth.accessToken(token)
        val elevated = api.withMetadata { set("Authorization", accessToken()) }.userAuth
        SecurityComponent.Elevated(
            password = elevated.password,
            totp = SecurityComponent.TotpEndpoints(elevated.totp),
            backupCode = elevated.backupCode,
        )
    },
)
card.frame { security.render(this) }
```

Leave out any `Elevated` field your server doesn't offer.  The demo's
[`SecurityPage.kt`](../demo-apps/src/commonMain/kotlin/com/lightningkite/lskiteuistarter/SecurityPage.kt) is the
same page wired to its session.

## Gotchas

- **Several subject types?**  `subjectType` defaults to `endpoints.subjects.keys.single()`, which throws when there
  is more than one.  Pass `subjectType = "User"` explicitly.
- **The components aren't reactive in their endpoints.**  If the server can change (a server picker, say), rebuild
  the component when it does: the demo's
  [`LoginScreen.kt`](../demo-apps/src/commonMain/kotlin/com/lightningkite/lskiteuistarter/LoginScreen.kt) keeps it in
  `remember { }` and re-renders it into a frame.
- **Passkeys need platform support.**  `WebAuthNProofComponent` hides itself where `supported()` is false.
- **UI tests:** every interactive element has a stable `debugName` (`primaryInput`, `loginButton`, `codeInput`, ...;
  listed in the CHANGELOG), so drivers needn't rely on view indices.
