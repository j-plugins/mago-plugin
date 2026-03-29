package com.github.xepozz.mago.configuration

class MagoWorkspaceMapping {
    var workspace: String = ""
    var configFile: String = ""

    constructor()
    constructor(workspace: String, configFile: String) {
        this.workspace = workspace
        this.configFile = configFile
    }
}
