import com.cosmix.app.plugins.CsxPlugin
import com.cosmix.app.plugins.CsxPluginAnnotation
package com.wowuncut

import android.content.Context

@CsxPluginAnnotation
class WowUncutPlugin : CsxPlugin() {
    override fun load(context: Context) {
        registerCsxApi(WowUncutProvider())
    }
}
