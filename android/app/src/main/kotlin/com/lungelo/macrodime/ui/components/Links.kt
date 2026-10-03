/*
 * Links.kt
 * MacroDime
 */
package com.lungelo.macrodime.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/**
 * Hands a web address to the browser, or a Play Store link to the Play Store.
 * MacroDime itself never connects to anything: it has no internet permission,
 * so the page loads elsewhere, not in the app.
 */
fun openExternal(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // Nothing installed to open it. The same addresses are in the store listing.
    }
}
