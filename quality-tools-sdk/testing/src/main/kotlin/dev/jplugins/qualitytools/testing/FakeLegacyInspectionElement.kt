package dev.jplugins.qualitytools.testing

import dev.jplugins.qualitytools.core.migration.LegacyInspectionFieldsMigrator.LegacyInspectionElement

/** Plain-map fake for testing `LegacyInspectionFieldsMigrator` without JDOM. */
public class FakeLegacyInspectionElement(
    private val fields: Map<String, String>,
) : LegacyInspectionElement {
    override fun field(name: String): String? = fields[name]
}
