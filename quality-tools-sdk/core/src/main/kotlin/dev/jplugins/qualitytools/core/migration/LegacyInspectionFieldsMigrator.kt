package dev.jplugins.qualitytools.core.migration

import dev.jplugins.qualitytools.core.context.QtLogger
import dev.jplugins.qualitytools.core.options.OptionSpec
import dev.jplugins.qualitytools.core.options.OptionsBag

/**
 * Tier-1 SDK patch G6.
 *
 * Five of the six legacy PHP quality-tool plugins ship an identical
 * `*SettingsTransferStartupActivity` class that copies N inline public
 * fields from a legacy `globalInspection` profile XML element into the
 * modern per-tool `*OptionsConfiguration` service. The shape is always:
 *
 *   if !marker.transferred:
 *     for each (legacyFieldName, modernField):
 *       modernField = legacy.<legacyFieldName>
 *     marker.transferred = true
 *
 * This class replaces those 5 startup activities. Plugin authors
 * declare a mapping table and call [migrateOnce] from their own
 * `postStartupActivity`. The marker is stored under the
 * `legacy_migrated_<toolId>` key inside the bag itself, so cross-
 * project migrations are independent and re-running is a no-op.
 *
 * The class deliberately does NOT depend on JDOM or any platform XML
 * type; the legacy element is consumed via [LegacyInspectionElement],
 * which `:ui` adapts from `org.jdom.Element`.
 */
public class LegacyInspectionFieldsMigrator(
    public val toolId: String,
    public val mappings: List<FieldMapping<*>>,
    public val logger: QtLogger = QtLogger.NoOp,
) {

    /**
     * Run the migration once for this `(toolId, bag)` pair. Returns true
     * if any fields were actually copied. The marker spec
     * [MIGRATED_MARKER_KEY] is set on success; subsequent calls return
     * `false` without touching the legacy element.
     *
     * Implementations of [legacy] return `null` when the field is
     * missing or unparseable — the corresponding modern field is left
     * at its default.
     */
    public fun migrateOnce(legacy: LegacyInspectionElement, bag: OptionsBag): Boolean {
        val markerKey = "$MIGRATED_MARKER_KEY.$toolId"
        if (bag.snapshot()[markerKey] == "true") {
            logger.log("debug", "Legacy migration for $toolId already done; skipping")
            return false
        }

        var anyCopied = false
        for (mapping in mappings) {
            val rawText = legacy.field(mapping.legacyFieldName)
            if (rawText == null) {
                logger.log(
                    "debug",
                    "Legacy field '${mapping.legacyFieldName}' missing for $toolId; using default",
                )
                continue
            }
            val applied = applyOne(mapping, rawText, bag)
            if (applied) anyCopied = true
        }

        bag[Marker(markerKey)] = "true"
        bag.commit()
        if (anyCopied) {
            logger.log("info", "Legacy fields migrated for $toolId")
        }
        return anyCopied
    }

    private fun <T : Any> applyOne(
        mapping: FieldMapping<T>,
        rawText: String,
        bag: OptionsBag,
    ): Boolean {
        val decoded = mapping.spec.decode(rawText)
        if (decoded == null) {
            logger.log(
                "warn",
                "Cannot decode legacy '${mapping.legacyFieldName}'='$rawText' for $toolId",
            )
            return false
        }
        bag[mapping.spec] = decoded
        return true
    }

    /** Read-only view of the legacy XML element. JDOM-free by design. */
    public interface LegacyInspectionElement {
        /** Return the textual value of a public field, or null when missing. */
        public fun field(name: String): String?
    }

    public data class FieldMapping<T : Any>(
        public val legacyFieldName: String,
        public val spec: OptionSpec<T>,
    )

    /** Internal `OptionSpec<String>` used to write the marker. Lives here to
     *  avoid leaking a top-level public spec. */
    private class Marker(override val key: String) : OptionSpec<String> {
        override val default: String = ""
        override val displayName: String = key
        override fun encode(value: String): String = value
        override fun decode(text: String): String = text
    }

    public companion object {
        public const val MIGRATED_MARKER_KEY: String = "_sdk.legacyMigrated"
    }
}
