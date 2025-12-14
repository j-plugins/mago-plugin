package com.github.xepozz.mago.config

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.registry.Registry
import org.toml.ide.experiments.TomlExperiments

class TomlSchemaActivator: ProjectActivity {
    override suspend fun execute(project: Project) {
        Registry.get(TomlExperiments.JSON_SCHEMA).setValue(true)
    }
}