package com.wow
import com.cosmix.app.plugins.CsxPlugin
import com.cosmix.app.plugins.CsxPluginAnnotation


@CsxPluginAnnotation
class WowPlugin : CsxPlugin() {
    override fun load() {
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerCsxApi(WowProvider())
    }
}
