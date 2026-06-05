package com.hamster

import com.cosmix.app.plugins.CsxPlugin
import com.cosmix.app.plugins.CsxPluginAnnotation
import android.content.Context

@CsxPluginAnnotation
class HamsterPlugin : CsxPlugin() {
    override fun load() {
        registerCsxApi(HamsterProvider())
    }
}
