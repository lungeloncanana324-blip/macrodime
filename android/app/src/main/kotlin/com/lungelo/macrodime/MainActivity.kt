package com.lungelo.macrodime

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.lungelo.macrodime.billing.PreviewSubscriptionStore
import com.lungelo.macrodime.data.DemoData
import com.lungelo.macrodime.ui.MacroDimeRoot
import com.lungelo.macrodime.ui.theme.MacroDimeTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = (application as MacroDimeApplication).container

        // Screenshot data. Debug builds only: in a release build any app on the
        // phone could send this extra and overwrite the user's profile. See DemoData.
        val wantsDemo = BuildConfig.DEBUG && intent.getBooleanExtra(DemoData.EXTRA_DEMO, false)
        val initialTab = if (BuildConfig.DEBUG) intent.getIntExtra(DemoData.EXTRA_TAB, 0) else 0
        if (wantsDemo && savedInstanceState == null) {
            lifecycleScope.launch { runCatching { DemoData.install(container.repository) } }
        }
        // The demo is a subscriber's app, which is what the store screenshots show.
        if (wantsDemo) container.subscriptions = PreviewSubscriptionStore.subscriber()

        setContent {
            MacroDimeTheme {
                MacroDimeRoot(container = container, initialTab = initialTab)
            }
        }
    }
}
