# Changelog

## Unreleased

### AuthComponent2

- **Added stable `debugName` values to key interactive elements** in `AuthComponent2`, making it possible for AI driver tests and automated test suites to reliably target them without fragile index-based paths:
  - `"primaryInput"` — the email/phone/username text input
  - `"cancelButton"` — the back/cancel button shown while proofs are in progress
  - `"loginButton"` — the final login button in the finalization screen
