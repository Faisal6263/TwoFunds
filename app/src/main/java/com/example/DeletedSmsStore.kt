package com.example

import android.content.Context

object DeletedSmsStore {
    private const val PREFS_NAME = "spend_radar_prefs"
    private const val DELETED_SMS_KEY = "deleted_sms"
    private const val DELETED_SMS_FINGERPRINTS_KEY = "deleted_sms_fingerprints"
    private const val DELETED_IDENTITIES_KEY = "deleted_sms_identities_v2"

    fun rememberDeleted(context: Context, smsBody: String, dateInMillis: Long) {
        if (smsBody.isBlank()) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val identities = prefs.getStringSet(DELETED_IDENTITIES_KEY, emptySet()).orEmpty().toMutableSet()
        identities.add(identity(smsBody, dateInMillis))
        prefs.edit().putStringSet(DELETED_IDENTITIES_KEY, identities).apply()
    }

    fun isDeleted(context: Context, smsBody: String, dateInMillis: Long): Boolean {
        if (smsBody.isBlank()) return false

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (identity(smsBody, dateInMillis) in prefs.getStringSet(DELETED_IDENTITIES_KEY, emptySet()).orEmpty()) return true
        // Retain legacy tombstones: their missing timestamps cannot safely be reconstructed.
        val deletedBodies = prefs.getStringSet(DELETED_SMS_KEY, emptySet()).orEmpty()
        if (smsBody in deletedBodies) return true

        val deletedFingerprints = prefs.getStringSet(DELETED_SMS_FINGERPRINTS_KEY, emptySet()).orEmpty() +
            deletedBodies.mapNotNull { it.toSmsFingerprint() }
        val smsFingerprint = smsBody.toSmsFingerprint()
        return smsFingerprint != null && smsFingerprint in deletedFingerprints
    }

    private fun identity(body: String, date: Long): String {
        val normalized = body.trim().replace(Regex("\\s+"), " ")
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
        return "$date:" + digest.joinToString("") { "%02x".format(it) }
    }
}
