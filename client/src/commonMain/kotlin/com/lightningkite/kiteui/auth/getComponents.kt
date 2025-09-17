package com.lightningkite.kiteui.auth

import com.lightningkite.lightningserver.auth.AuthEndpoints


fun AuthEndpoints.components(
    subjectType: String = subjects.keys.single()
): List<ProofComponent> = listOfNotNull(
    smsProof?.let { SmsProofComponent(it) },
    emailProof?.let { EmailProofComponent(it) },
    oneTimePasswordProof?.let { TotpProofComponent(it, subjectType) },
    passwordProof?.let { PasswordProofComponent(it, subjectType) },
    backupCodeProof?.let { BackupCodeProofComponent(it, subjectType) },
    webAuthNProof?.let { WebAuthNProofComponent(it, subjectType, webAuthNIncludePasskeyUI) },
)