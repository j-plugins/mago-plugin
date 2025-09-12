package com.github.xepozz.mago.file

import com.github.xepozz.mago.MagoIcons
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.fileTypes.PlainTextLanguage
import java.io.Serializable

class MagoTextFileType private constructor() : LanguageFileType(PlainTextLanguage.INSTANCE), Serializable {
    override fun getName() = "Mago File"

    override fun getDescription() = "Mago configuration file"

    override fun getDefaultExtension() = "toml"

    override fun getIcon() = MagoIcons.MAGO

    companion object {
        @JvmStatic
        val INSTANCE = MagoTextFileType()
    }
}