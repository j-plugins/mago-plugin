package com.github.xepozz.mago.file

import com.github.xepozz.mago.MagoBundle
import com.github.xepozz.mago.MagoIcons
import com.intellij.openapi.fileTypes.LanguageFileType
import org.toml.lang.TomlLanguage
import java.io.Serializable

class MagoTomlFileType private constructor() : LanguageFileType(TomlLanguage), Serializable {
    companion object {
        @JvmField
        val INSTANCE = MagoTomlFileType()
    }

    override fun getName() = MagoTextFileType.NAME

    override fun getDescription() = MagoBundle.message("fileType.description")

    override fun getDefaultExtension() = "toml"

    override fun getIcon() = MagoIcons.MAGO
}
