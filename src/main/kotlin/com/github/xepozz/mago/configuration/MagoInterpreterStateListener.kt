package com.github.xepozz.mago.configuration

import com.intellij.openapi.project.Project
import com.jetbrains.php.config.interpreters.PhpInterpretersStateListener

class MagoInterpreterStateListener : PhpInterpretersStateListener {
    override fun onInterpretersUpdate(project: Project) {
        MagoConfigurationManager.getInstance(project).onInterpretersUpdate()
    }
}