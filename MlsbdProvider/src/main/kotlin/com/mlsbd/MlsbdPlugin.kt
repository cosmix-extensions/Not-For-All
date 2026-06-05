package com.mlsbd
import com.cosmix.app.plugins.CsxPlugin
import com.cosmix.app.plugins.CsxPluginAnnotation


@CsxPluginAnnotation
class MlsbdPlugin : CsxPlugin() {
    override fun load() {
        registerCsxApi(MlsbdProvider())
    }
}
