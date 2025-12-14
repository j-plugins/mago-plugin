package com.github.xepozz.mago.config

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaEnabler

class MagoTomlJsonSchemaEnabler : JsonSchemaEnabler {
    override fun isEnabledForFile(file: VirtualFile, project: Project?) = file.name == "mago.toml"

    override fun shouldShowSwitcherWidget(file: VirtualFile) = file.name == "mago.toml"
}
