package dev.jplugins.qualitytools.core.tool

/**
 * Recognised string constants for [QualityTool.resultReaderId] and
 * [ToolMode.resultReaderId]. Plugin authors are free to invent new
 * ids (and ship the matching `ResultReader`); these are the bundled
 * format names.
 */
public object OutputFormats {
    public const val CHECKSTYLE_XML: String = "checkstyle-xml"
    public const val JSON_LINES: String = "json-lines"
    public const val SARIF: String = "sarif"
    public const val LINE: String = "line"
    public const val UDIFF: String = "udiff"

    /** Sentinel: "use the tool-level default" — when set on a
     *  `ToolMode.resultReaderId` it has the same effect as `null`. */
    public const val INHERIT: String = "inherit"
}
