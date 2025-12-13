package com.github.xepozz.mago.qualityTool

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.Project
import com.jetbrains.php.tools.quality.QualityToolBlackList

@State(name = "MagoBlackList", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
@Service(Service.Level.PROJECT)
class MagoBlackList : QualityToolBlackList() {
    companion object {
        fun getInstance(project: Project): MagoBlackList = project.getService(MagoBlackList::class.java)
    }
}
