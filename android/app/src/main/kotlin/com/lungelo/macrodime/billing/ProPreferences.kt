/*
 * ProPreferences.kt
 * MacroDime
 *
 * What the app remembers about Pro between launches: the store's last answer,
 * so a subscriber's plan opens offline (for EntitlementPolicy.OFFLINE_GRACE_DAYS),
 * and the record of a purchase this phone started, which is the only place the
 * plan and the trial's end date are known, because Play does not report them to
 * the app. A handful of values, so plain SharedPreferences.
 */
package com.lungelo.macrodime.billing

import android.content.Context
import androidx.core.content.edit
import com.lungelo.macrodime.domain.Entitlement
import com.lungelo.macrodime.domain.ProPlan
import com.lungelo.macrodime.domain.PurchaseRecord

class ProPreferences(context: Context) {

    private val prefs = context.getSharedPreferences("macrodime_pro", Context.MODE_PRIVATE)

    var entitlement: Entitlement
        get() = Entitlement(
            isPro = prefs.getBoolean(PRO, false),
            plan = prefs.getString(PLAN, null)?.let(::planOf),
            verifiedAtMillis = prefs.getLong(VERIFIED_AT, 0),
            trialEndsAtMillis = prefs.getLong(TRIAL_ENDS_AT, 0).takeIf { it > 0 },
            willRenew = prefs.getBoolean(WILL_RENEW, true),
        )
        set(value) = prefs.edit {
            putBoolean(PRO, value.isPro)
            putString(PLAN, value.plan?.name)
            putLong(VERIFIED_AT, value.verifiedAtMillis)
            putLong(TRIAL_ENDS_AT, value.trialEndsAtMillis ?: 0)
            putBoolean(WILL_RENEW, value.willRenew)
        }

    var record: PurchaseRecord?
        get() {
            val token = prefs.getString(RECORD_TOKEN, null) ?: return null
            val plan = prefs.getString(RECORD_PLAN, null)?.let(::planOf) ?: return null
            return PurchaseRecord(token, plan, prefs.getLong(RECORD_TRIAL_ENDS_AT, 0).takeIf { it > 0 })
        }
        set(value) = prefs.edit {
            putString(RECORD_TOKEN, value?.token)
            putString(RECORD_PLAN, value?.plan?.name)
            putLong(RECORD_TRIAL_ENDS_AT, value?.trialEndsAtMillis ?: 0)
        }

    fun clear() = prefs.edit { clear() }

    private fun planOf(name: String): ProPlan? = ProPlan.entries.firstOrNull { it.name == name }

    private companion object {
        const val PRO = "pro"
        const val PLAN = "plan"
        const val VERIFIED_AT = "verifiedAt"
        const val TRIAL_ENDS_AT = "trialEndsAt"
        const val WILL_RENEW = "willRenew"
        const val RECORD_TOKEN = "recordToken"
        const val RECORD_PLAN = "recordPlan"
        const val RECORD_TRIAL_ENDS_AT = "recordTrialEndsAt"
    }
}
