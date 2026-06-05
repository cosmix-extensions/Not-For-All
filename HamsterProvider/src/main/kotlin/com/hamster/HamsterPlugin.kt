package com.hamster

import app.cosmix.gradle.CsxPluginAnnotation
import com.cosmix.app.plugins.CsxPlugin
import android.content.Context

@CsxPluginAnnotation
class HamsterPlugin : CsxPlugin() {
    override fun load() {
        registerCsxApi(HamsterProvider())
    }
}
