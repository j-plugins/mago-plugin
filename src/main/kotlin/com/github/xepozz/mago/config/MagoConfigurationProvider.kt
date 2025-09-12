package com.github.xepozz.mago.config

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.jetbrains.php.tools.quality.QualityToolConfigurationProvider

abstract class MagoConfigurationProvider : QualityToolConfigurationProvider<MagoConfiguration>() {
    companion object {
        private val LOG: Logger = Logger.getInstance(MagoConfigurationProvider::class.java)
        private val EP_NAME: ExtensionPointName<MagoConfigurationProvider> =
            ExtensionPointName.create("com.github.xepozz.mago.magoConfigurationProvider")

        fun getInstances(): MagoConfigurationProvider? {
            val extensions: Array<MagoConfigurationProvider> = EP_NAME.extensions
            if (extensions.size > 1) {
                LOG.error("Several providers for remote Mago configuration was found")
            }
            return if (extensions.isEmpty()) null else extensions[0]
        }
    }
}
