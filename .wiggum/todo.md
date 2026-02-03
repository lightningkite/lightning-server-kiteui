# Code Review TODO - Prioritized Issues
<!-- by Claude -->

Generated from code review completed 2026-01-28.

---

## P0 - Critical (Crashes at Runtime)

### iOS WebAuthN Implementation Missing
**File**: `client/src/iosMain/kotlin/com/lightningkite/kiteui/webAuthN.kt`
- All functions throw `TODO()` which will crash at runtime if called
- Needs actual iOS implementation using `ASAuthorizationController`
- **Risk**: Any iOS app using WebAuthN/Passkeys will crash

---

## P1 - High (Bugs / Incorrect Behavior)

### CollectionAdminScreen Permission Display Error
**File**: `admin/src/commonMain/kotlin/com/lightningkite/lightningserver/admin/CollectionAdminScreen.kt`
**Line**: ~359
- Copy-paste error in permission display logic
- Shows incorrect permission status to admins

### Force Unwrap Could Throw NPE
**File**: `client/src/commonMain/kotlin/com/lightningkite/kiteui/forms/annotationReaders.kt`
**Line**: 101
- `!!` force unwrap on potentially null value
- Will throw NPE if annotation doesn't have expected argument

---

## P2 - Medium (Poor Error Handling / UX)

### Unhelpful Exception Message
**File**: `client/src/commonMain/kotlin/com/lightningkite/kiteui/forms/helpers.kt`
**Line**: 55
- Throws exception with message "WAT"
- Should be descriptive error explaining what went wrong

### Silent Error Swallowing in JSON Renderer
**File**: `client/src/commonMain/kotlin/com/lightningkite/kiteui/forms/JsonRenderer.kt`
- Errors in JSON parsing/serialization are silently swallowed
- User gets no feedback when JSON is malformed

### Bulk Operation Failures Silent
**File**: `admin/src/commonMain/kotlin/com/lightningkite/lightningserver/admin/CollectionAdminScreen.kt`
- CSV import errors not clearly communicated
- Bulk delete failures may not be visible to user

---

## P3 - Low (Testing / Documentation / Quality)

### Tests Needed

| File | What to Test |
|------|--------------|
| `default.kt` | Tests created but not verified (Gradle lock) |
| `annotationReaders.kt` | Extension properties for annotation reading |
| `helpers.kt` | FormSize class (pure data class) |
| `EnumFormRenderer.kt` | `TypeInfo.toDisplayName()` pure function |
| `credentials.kt` | 22 tests created but not verified |

**Blocker**: Gradle lock file conflict prevents test execution
```bash
./gradlew --stop && rm -f .gradle/8.14.3/fileHashes/fileHashes.lock
```

### Documentation Gaps
- Many files missing KDoc on public APIs
- Several files have vague/unclear documentation

### Code Quality
- `default.kt`: Uses exception for control flow (inefficient)
- `default.kt`: Vague filename ("default" doesn't describe purpose)
- `InlineFormRenderer.kt`: Uses `InternalSerializationApi` (unstable API)
- Multiple admin screens: Magic numbers for spacing/sizing
- `AuthComponent.kt`: Duplicate code patterns, state machine complexity

### Dead Code / Cleanup
- `HomeScreen.kt`: Contains dead code
- `AuthTestScreen.kt`: Commented-out code
- `ByFieldRenderer.kt`: Field importance logic commented out

---

## Verification Needed

Run tests once Gradle is unlocked:
```bash
./gradlew :client:jvmTest --tests "*.DefaultKtTest"
./gradlew :admin:jsTest --tests "*.CredentialsTest"
```

---

## Summary by Module

| Module | P0 | P1 | P2 | P3 |
|--------|----|----|----|----|
| admin/src/commonMain | 0 | 1 | 1 | 5 |
| admin/src/jsMain | 0 | 0 | 0 | 1 |
| client/src/commonMain | 0 | 1 | 2 | 8 |
| client/src/iosMain | 1 | 0 | 0 | 0 |
| **Total** | **1** | **2** | **3** | **14** |
