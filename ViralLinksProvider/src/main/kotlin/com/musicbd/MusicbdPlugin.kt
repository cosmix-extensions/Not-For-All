package com.musicbd
import com.cosmix.app.plugins.CsxPlugin
import com.cosmix.app.plugins.CsxPluginAnnotation


@CsxPluginAnnotation
class MusicbdPlugin : CsxPlugin() {
    override fun load() {
        registerCsxApi(MusicbdProvider())
    }
}
