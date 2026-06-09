package com.mat6tube

import com.cosmix.app.plugins.CsxPlugin
import com.cosmix.app.plugins.CsxPluginAnnotation
import android.content.Context

@CsxPluginAnnotation
class Mat6tubePlugin : CsxPlugin() {
    override fun load() {
        registerCsxApi(Mat6tubeProvider())
    }
}
