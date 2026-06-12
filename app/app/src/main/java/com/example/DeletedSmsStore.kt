package com.example

import android.content.Context

object DeletedSmsStore {
    private const val PREFS_NAME = "spend_radar_prefs"
    private const val DELETED_SMS_KEY = "deleted_sms"
    private const val DELETED_SMS_FINGERPRINTS_KEY = "deleted_sms_fingerprints"

    fun rememberDeleted(context: Context, smsBody: String) {
        if (smsBody.isBlank()) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val deletedBodies = prefs.getStringSet(DELETED_SMS_KEY, emptySet()).orEmpty().toMutableSet()
        val deletedFingerprints = prefs.getStringSet(DELETED_SMS_FINGERPRINTS_KEY, emptySet()).orEmpty().toMutableSet()

        deletedBodies.add(smsBody)
        smsBody.toSmsFingerprint()?.let { deletedFingerprints.add(it) }

        prefs.edit()
            .putStringSet(DELETED_SMS_KEY, deletedBodies)
            .putStringSet(DELETED_SMS_FINGERPRINTS_KEY, deletedFingerprints)
            .apply()
    }

    fun isDeleted(context: Context, smsBody: String): Boolean {
        if (smsBody.isBlank()) return false

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val deletedBodies = prefs.getStringSet(DELETED_SMS_KEY, emptySet()).orEmpty()
        if (smsBody in deletedBodies) return true

        val deletedFingerprints = prefs.getStringSet(DELETED_SMS_FINGERPRINTS_KEY, emptySet()).orEmpty() +
            deletedBodies.mapNotNull { it.toSmsFingerprint() }
        val smsFingerprint = smsBody.toSmsFingerprint()
        return smsFingerprint != null && smsFingerprint in deletedFingerprints
    }
}
