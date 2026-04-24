package com.lightningkite.kiteui.auth

import com.lightningkite.lightningserver.auth.AuthEndpoints

/**
 * Extension function to get all available proof components from authentication endpoints.
 *
 * This function examines the server-configured authentication endpoints and creates
 * corresponding ProofComponent instances for each enabled authentication method.
 *
 * The order of components in the returned list determines their display order in the UI:
 * 1. SMS (text message codes)
 * 2. Email (email verification codes)
 * 3. TOTP (time-based one-time passwords from authenticator apps)
 * 4. Password
 * 5. Backup codes
 * 6. WebAuthN (passkeys/security keys)
 *
 * @param subjectType The type of subject being authenticated (e.g., "user", "admin").
 *                    Defaults to the single subject type if only one exists.
 * @return List of available ProofComponent instances, in display order, excluding null entries
 */
public fun AuthEndpoints.components(
    subjectType: String = subjects.keys.single()
): List<ProofComponent> = listOfNotNull(
    smsProof?.let { SmsProofComponent(it) },
    emailProof?.let { EmailProofComponent(it) },
    passwordProof?.let { PasswordProofComponent(it, subjectType) },
    oneTimePasswordProof?.let { TotpProofComponent(it, subjectType) },
    backupCodeProof?.let { BackupCodeProofComponent(it, subjectType) },
    webAuthNProof?.let { WebAuthNProofComponent(it, subjectType, webAuthNIncludePasskeyUI) },
)

/*
 * API IMPROVEMENT RECOMMENDATIONS:
 *
 * 1. COMPONENT ORDER - The order is hardcoded and may not fit all use cases
 *    - Current order: SMS, Email, TOTP, Password, Backup, WebAuthN
 *    - Consider making order configurable via parameter or configuration
 *    - Different apps may want different prioritization (e.g., Password first)
 *
 * 2. FILTERING CAPABILITY - No way to exclude certain methods even if enabled on server
 *    - Some apps may want to hide certain proof methods in specific contexts
 *    - Add optional filter parameter: filter: (ProofComponent) -> Boolean = { true }
 *
 * 3. EXTENSIBILITY - No way to add custom proof components
 *    - Third-party integrations (OAuth, biometrics) not supported
 *    - Consider: additionalComponents: List<ProofComponent> = emptyList() parameter
 *
 * 4. SUBJECT TYPE SAFETY - subjects.keys.single() will crash if multiple subjects exist
 *    - Better error message or require explicit subject type when multiple exist
 *    - Consider: require(subjects.size == 1) { "Must specify subjectType when multiple subjects exist" }
 *
 * 5. COMPONENT CONFIGURATION - Some components need subjectType, others don't
 *    - Inconsistent constructor patterns across components
 *    - Consider standardizing all components to accept same parameters
 *
 * 6. NULL SAFETY - Using listOfNotNull means order matters even for nulls
 *    - If a component is null, it doesn't affect position of others
 *    - This is correct but not obvious; add clarifying comment
 *
 * 7. CACHING - This function is called repeatedly but returns same results
 *    - Consider caching at AuthEndpoints level
 *    - Or document that callers should cache if calling frequently
 */