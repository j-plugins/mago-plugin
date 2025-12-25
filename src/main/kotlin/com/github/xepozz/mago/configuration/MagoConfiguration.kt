package com.github.xepozz.mago.configuration

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Transient
import com.jetbrains.php.PhpBundle
import com.jetbrains.php.tools.quality.QualityToolConfiguration

open class MagoConfiguration : QualityToolConfiguration() {
    private var myMagoPath = ""
    private var myTimeoutMs = 30000
    private var myMaxMessagesPerFile = 100

    var customParameters = ""
        @Attribute("custom_parameters")
        get

    override fun compareTo(other: QualityToolConfiguration?): Int {
        if (other !is MagoConfiguration) {
            return 1
        }
        if (this.getPresentableName(null) == PhpBundle.message("label.system.php")) {
            return -1
        }
        if (other.getPresentableName(null) == PhpBundle.message("label.system.php")) {
            return 1
        }

        return StringUtil.compare(getPresentableName(null), other.getPresentableName(null), false)
    }

    override fun getId() = PhpBundle.message("local")

    override fun getPresentableName(project: Project?): String = id

    override fun getInterpreterId(): String? = null

    @Attribute("timeout")
    override fun getTimeout(): Int = myTimeoutMs

    override fun setTimeout(timeout: Int) {
        myTimeoutMs = timeout
    }

    @Transient
    override fun getToolPath() = myMagoPath

    override fun setToolPath(toolPath: String) {
        myMagoPath = toolPath
    }

    @Attribute("tool_path")
    fun getSerializedToolPath(): String = serialize(myMagoPath).removeSuffix(".bat")

    fun setSerializedToolPath(configurationFilePath: String?) {
        val deserializePath = deserialize(configurationFilePath).removeSuffix(".bat")

        myMagoPath = deserializePath
    }

    @Attribute("max_messages_per_file")
    override fun getMaxMessagesPerFile(): Int = myMaxMessagesPerFile

    override fun clone(): QualityToolConfiguration {
        return clone(MagoConfiguration())
    }

    fun clone(settings: MagoConfiguration): MagoConfiguration {
        return settings.also {
            it.myMagoPath = myMagoPath
            it.myMaxMessagesPerFile = myMaxMessagesPerFile
            it.myTimeoutMs = myTimeoutMs
        }
    }
}
