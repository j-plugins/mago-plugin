package dev.jplugins.qualitytools.core.source.local

import dev.jplugins.qualitytools.core.context.ThreadingPolicy
import dev.jplugins.qualitytools.core.source.AvailabilityContext
import dev.jplugins.qualitytools.core.source.ConfigSource
import dev.jplugins.qualitytools.core.source.ConfigSourceType
import dev.jplugins.qualitytools.core.source.ConfigSourceWizard
import dev.jplugins.qualitytools.core.source.SerializedSourceElement
import dev.jplugins.qualitytools.core.source.WizardContext

/**
 * Bundled `ConfigSourceType` for [LocalBinarySource]. Always available.
 *
 * Hand-rolls serialize/deserialize against [SerializedSourceElement]
 * rather than depending on a reflection codec, so `:core` stays
 * JDOM-free. Round-trip is exercised by
 * `LocalBinarySourceTypeTest.kt`.
 *
 * Authors of bespoke source types use this class as a copy-paste
 * starting point.
 */
public class LocalBinarySourceType : ConfigSourceType {
    override val typeId: String = LocalBinarySource.TYPE_ID
    override val displayName: String = "Local binary"

    @ThreadingPolicy("any")
    override fun isAvailable(ctx: AvailabilityContext): Boolean = true

    @ThreadingPolicy("edt")
    override fun createWizard(ctx: WizardContext): ConfigSourceWizard? = null

    @ThreadingPolicy("any")
    override fun deserialize(element: SerializedSourceElement): ConfigSource {
        val instanceId = element.attributes[ATTR_INSTANCE_ID]
            ?: error("LocalBinarySource missing $ATTR_INSTANCE_ID attribute")
        val path = element.attributes[ATTR_PATH].orEmpty()
        val displayName = element.attributes[ATTR_DISPLAY_NAME] ?: path
        val cachedDetectedVersion = element.attributes[ATTR_DETECTED_VERSION]
        val env = element
            .childrenNamed(CHILD_ENV)
            .mapNotNull { e -> e.attributes[ATTR_KEY]?.let { it to e.attributes[ATTR_VALUE].orEmpty() } }
            .toMap()
        return LocalBinarySource(
            instanceId = instanceId,
            path = path,
            displayName = displayName,
            extraEnv = env,
            cachedDetectedVersion = cachedDetectedVersion,
        )
    }

    @ThreadingPolicy("any")
    override fun serialize(source: ConfigSource): SerializedSourceElement {
        require(source is LocalBinarySource) {
            "LocalBinarySourceType.serialize received a ${source::class.simpleName}"
        }
        val attrs = buildMap {
            put(ATTR_INSTANCE_ID, source.instanceId)
            put(ATTR_PATH, source.path)
            if (source.displayName != source.path) {
                put(ATTR_DISPLAY_NAME, source.displayName)
            }
            source.cachedDetectedVersion?.let { put(ATTR_DETECTED_VERSION, it) }
        }
        val envChildren = source.extraEnv.map { (k, v) ->
            ImmutableSerializedElement(
                name = CHILD_ENV,
                attributes = mapOf(ATTR_KEY to k, ATTR_VALUE to v),
            )
        }
        return ImmutableSerializedElement(
            name = ROOT,
            attributes = attrs,
            children = envChildren,
        )
    }

    public companion object {
        public const val ROOT: String = "source"
        public const val ATTR_INSTANCE_ID: String = "instanceId"
        public const val ATTR_PATH: String = "path"
        public const val ATTR_DISPLAY_NAME: String = "displayName"
        public const val ATTR_DETECTED_VERSION: String = "detectedVersion"
        public const val CHILD_ENV: String = "env"
        public const val ATTR_KEY: String = "key"
        public const val ATTR_VALUE: String = "value"
    }

    /** Plain-data `SerializedSourceElement` we can construct in tests. */
    public data class ImmutableSerializedElement(
        override val name: String,
        override val attributes: Map<String, String> = emptyMap(),
        override val text: String? = null,
        override val children: List<SerializedSourceElement> = emptyList(),
    ) : SerializedSourceElement
}
