package dev.jplugins.qualitytools.core.source

/**
 * Opaque tree of (name, attributes, text, children). `:core` consumes
 * and produces this shape; the JDOM/XML adapter lives in `:ui` so `:core`
 * has no `org.jdom` import.
 */
public interface SerializedSourceElement {
    public val name: String
    public val attributes: Map<String, String>
    public val text: String?
    public val children: List<SerializedSourceElement>

    public fun child(name: String): SerializedSourceElement? =
        children.firstOrNull { it.name == name }

    public fun childrenNamed(name: String): List<SerializedSourceElement> =
        children.filter { it.name == name }
}

/** Builder used by `ConfigSourceType.serialize`. */
public interface SerializedSourceElementBuilder {
    public fun attr(name: String, value: String): SerializedSourceElementBuilder
    public fun text(value: String): SerializedSourceElementBuilder
    public fun child(
        name: String,
        build: SerializedSourceElementBuilder.() -> Unit = {},
    ): SerializedSourceElementBuilder

    public fun build(): SerializedSourceElement
}
