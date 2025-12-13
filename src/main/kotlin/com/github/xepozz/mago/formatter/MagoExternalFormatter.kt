package com.github.xepozz.mago.formatter

import com.github.xepozz.mago.configuration.MagoProjectConfiguration
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.ExternalFormatProcessor
import com.jetbrains.php.lang.PhpFileType

class MagoExternalFormatter : ExternalFormatProcessor {
    override fun activeForFile(source: PsiFile): Boolean {
        val project = source.project
        if (project.isDisposed) return false

        val projectConfiguration = project.getService(MagoProjectConfiguration::class.java)
        if (!projectConfiguration.formatterEnabled) return false

        return source.fileType == PhpFileType.INSTANCE
    }

    override fun format(
        source: PsiFile,
        range: TextRange,
        canChangeWhiteSpacesOnly: Boolean,
        keepLineBreaks: Boolean,
        enableBulkUpdate: Boolean,
        cursorOffset: Int
    ): TextRange? {
        val virtualFile = source.originalFile.virtualFile
        if (virtualFile != null) {
            thisLogger().debug("Reformatting file: ${virtualFile.path}")
            ProgressManager.checkCanceled()
//            println("before: ${source.text}")
            MagoReformatFile(source.project).invoke(source.project, source)
            return null
        }
        return null
    }

    override fun indent(source: PsiFile, lineStartOffset: Int) = null

    override fun getId() = "Mago"
}