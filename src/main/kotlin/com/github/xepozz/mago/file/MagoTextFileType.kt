package com.github.xepozz.mago.file

import com.github.xepozz.mago.MagoBundle
import com.github.xepozz.mago.MagoIcons
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.fileTypes.PlainTextLanguage
import java.io.Serializable

class MagoTextFileType private constructor() : LanguageFileType(PlainTextLanguage.INSTANCE), Serializable {
    companion object {
        @JvmField
        val INSTANCE = MagoTextFileType()

        /** Must match plugin.xml fileType name (bundle key fileType.name default) and MagoBundle.fileType.name. */
        const val NAME = "Mago File"
    }

    override fun getName() = NAME

    override fun getDescription() = MagoBundle.message("fileType.description")

    override fun getDefaultExtension() = "toml"

    override fun getIcon() = MagoIcons.MAGO
}
