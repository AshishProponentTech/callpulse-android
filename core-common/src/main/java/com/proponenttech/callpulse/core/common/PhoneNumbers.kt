package com.proponenttech.callpulse.core.common

import android.content.Context
import io.michaelrocks.libphonenumber.android.NumberParseException
import io.michaelrocks.libphonenumber.android.PhoneNumberUtil

/**
 * §13.4 / §6.2.6 — E.164 normalisation, default region IN.
 *
 * The server re-normalises everything it receives anyway and wins on disagreement, so this is
 * a best effort on the device. What matters is that it NEVER produces a junk value: a hidden or
 * withheld number becomes null, because a garbage string in phone_e164 breaks the index and
 * every later lookup, which is worse than having no number at all.
 */
object PhoneNumbers {

    private const val DEFAULT_REGION = "IN"

    /** Values the platform uses for numbers it will not reveal. */
    private val HIDDEN_MARKERS = setOf("-1", "-2", "-3", "private", "unknown", "restricted", "anonymous")

    @Volatile
    private var util: PhoneNumberUtil? = null

    /** libphonenumber-android needs a Context to load its metadata; done once. */
    fun init(context: Context) {
        if (util == null) {
            synchronized(this) {
                if (util == null) {
                    util = PhoneNumberUtil.createInstance(context.applicationContext)
                }
            }
        }
    }

    fun toE164(raw: String?, region: String = DEFAULT_REGION): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty() || trimmed.lowercase() in HIDDEN_MARKERS) return null

        val instance = util ?: return null

        return try {
            val parsed = instance.parse(trimmed, region)
            if (instance.isValidNumber(parsed)) {
                instance.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164)
            } else {
                null
            }
        } catch (e: NumberParseException) {
            null
        }
    }

    /** True when the platform gave us no usable number at all (withheld / private caller). */
    fun isHidden(raw: String?): Boolean {
        val trimmed = raw?.trim().orEmpty()
        return trimmed.isEmpty() || trimmed.lowercase() in HIDDEN_MARKERS
    }
}
