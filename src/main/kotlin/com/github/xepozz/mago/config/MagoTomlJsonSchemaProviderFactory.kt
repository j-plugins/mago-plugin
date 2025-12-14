package com.github.xepozz.mago.config

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import com.jetbrains.jsonSchema.extension.SchemaType

class MagoTomlJsonSchemaProviderFactory : JsonSchemaProviderFactory {
    override fun getProviders(project: Project) = listOf(
        object : JsonSchemaFileProvider {
            override fun isAvailable(file: VirtualFile) = file.name == "mago.toml"

            override fun getName() = "Mago Toml"

            override fun getSchemaType() = SchemaType.embeddedSchema

            override fun getSchemaFile(): VirtualFile? {
                val schemaHolder = project.getService(MagoSchemaHolder::class.java)
                val schema = schemaHolder.getSchema()
                if (schema == null) {
                    schemaHolder.dumpSchema()
                }
                return schema
            }
        }
    )
}
