import com.cosmix.app.plugins.CsxPlugin
import com.cosmix.app.plugins.CsxPluginAnnotation
package com.mlsbd


@CsxPluginAnnotation
class MlsbdPlugin : CsxPlugin() {
    override fun load() {
        registerCsxApi(MlsbdProvider())
    }
}
