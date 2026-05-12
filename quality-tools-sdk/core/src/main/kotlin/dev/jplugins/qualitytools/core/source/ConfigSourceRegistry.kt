package dev.jplugins.qualitytools.core.source

import dev.jplugins.qualitytools.core.context.ThreadingPolicy

/**
 * Lookup over registered [ConfigSourceType]s. Interface lives in `:core`
 * so consumers (storage, tests) don't depend on the IntelliJ EP machinery.
 * `:ui` ships `EpConfigSourceRegistry`, which materialises the EP list
 * lazily and honours `aliasTypeIds` for deserialization.
 *
 * Required by phase 04: storage uses [findByTypeId] when loading a
 * `<profile>` element back into a `ConfigSource`.
 */
public interface ConfigSourceRegistry {
    /** Lookup by canonical [ConfigSourceType.typeId] or by any
     *  [ConfigSourceType.aliasTypeIds]. */
    @ThreadingPolicy("any")
    public fun findByTypeId(typeId: String): ConfigSourceType?

    /** Every registered type, regardless of availability in the
     *  current project. */
    @ThreadingPolicy("any")
    public fun all(): List<ConfigSourceType>

    /** Subset that returns true from [ConfigSourceType.isAvailable]
     *  for [ctx]. UI's "Add new source" enumerates this. */
    @ThreadingPolicy("any")
    public fun available(ctx: AvailabilityContext): List<ConfigSourceType>
}
