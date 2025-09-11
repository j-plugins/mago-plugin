package com.github.xepozz.mago.file

import com.github.xepozz.mago.MagoIcons
import com.intellij.openapi.fileTypes.LanguageFileType
import org.toml.lang.TomlLanguage

class MagoTomlFileType private constructor() : LanguageFileType(TomlLanguage), java.io.Serializable {
    override fun getName() = "Mago File"

    override fun getDescription() = "Mago configuration file"

    override fun getDefaultExtension() = "toml"

    override fun getIcon() = MagoIcons.MAGO

    companion object {
        @JvmStatic
        val INSTANCE = MagoTomlFileType()
    }
}