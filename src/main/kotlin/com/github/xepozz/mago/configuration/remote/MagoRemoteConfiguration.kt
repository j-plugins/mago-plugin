package com.github.xepozz.mago.configuration.remote

import com.github.xepozz.mago.configuration.MagoConfiguration
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Tag
import com.jetbrains.php.PhpBundle
import com.jetbrains.php.config.interpreters.PhpInterpretersManagerImpl
import com.jetbrains.php.config.interpreters.PhpSdkDependentConfiguration
import com.jetbrains.php.remote.PhpRemoteSdkBundle
import com.jetbrains.php.tools.quality.QualityToolConfiguration

@Tag("mago_by_interpreter")
class MagoRemoteConfiguration : MagoConfiguration(), PhpSdkDependentConfiguration {
    private var myInterpreterId: String? = null

    @Attribute("interpreter_id")
    override fun getInterpreterId(): String? = myInterpreterId

    override fun setInterpreterId(interpreterId: String) {
        myInterpreterId = interpreterId
    }

    override fun getPresentableName(project: Project?): String =
        when {
            isCreatedAsDefaultInterpreterConfiguration -> PhpBundle.message("quality.tools.label.by.default.project.interpreter")
            else -> getDefaultName(PhpInterpretersManagerImpl.getInstance(project).findInterpreterName(interpreterId))
        }

    override fun getId(): String =
        when {
            isCreatedAsDefaultInterpreterConfiguration -> "DEFAULT_INTERPRETER"
            interpreterId.isNullOrEmpty() -> PhpRemoteSdkBundle.message("label.undefined.interpreter")
            else -> interpreterId!!
        }

    private fun getDefaultName(interpreterName: String?) = when {
        interpreterName.isNullOrEmpty() -> PhpRemoteSdkBundle.message("label.undefined.interpreter")
        else -> interpreterName
    }

    override fun clone(): QualityToolConfiguration = super.clone(
        MagoRemoteConfiguration().also {
            it.myInterpreterId = myInterpreterId
        }
    )

    override fun isLocal() = false

    override fun serialize(path: String?): String? = path

    override fun deserialize(path: String?): String? = path
}
