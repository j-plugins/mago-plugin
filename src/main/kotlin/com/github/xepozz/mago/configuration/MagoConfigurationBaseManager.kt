package com.github.xepozz.mago.configuration

import com.github.xepozz.mago.qualityTool.MagoQualityToolType
import com.intellij.util.xmlb.XmlSerializer
import com.jetbrains.php.tools.quality.QualityToolConfigurationBaseManager
import org.jdom.Element

open class MagoConfigurationBaseManager : QualityToolConfigurationBaseManager<MagoConfiguration>() {
    override fun getQualityToolType() = MagoQualityToolType.INSTANCE

    override fun getOldStyleToolPathName() = MAGO_PATH

    override fun getConfigurationRootName() = MAGO_ROOT_NAME

    override fun loadLocal(element: Element?): MagoConfiguration? {
        if (element == null) return null

        return XmlSerializer.deserialize(element, MagoConfiguration::class.java)
    }

    companion object {
        const val MAGO = "Mago"
        const val MAGO_PATH = "MagoPath"
        const val MAGO_ROOT_NAME = "Mago_settings"
    }
}
