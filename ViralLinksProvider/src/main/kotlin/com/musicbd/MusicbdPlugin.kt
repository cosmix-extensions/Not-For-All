import com.cosmix.app.plugins.CsxPlugin
import com.cosmix.app.plugins.CsxPluginAnnotation
package com.musicbd


@CsxPluginAnnotation
class MusicbdPlugin : CsxPlugin() {
    override fun load() {
        registerCsxApi(MusicbdProvider())
    }
}
