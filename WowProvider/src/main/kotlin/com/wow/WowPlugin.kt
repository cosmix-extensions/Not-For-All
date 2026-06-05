import com.cosmix.app.plugins.CsxPlugin
import com.cosmix.app.plugins.CsxPluginAnnotation
package com.wow


@CsxPluginAnnotation
class WowPlugin : CsxPlugin() {
    override fun load() {
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerCsxApi(WowProvider())
    }
}
