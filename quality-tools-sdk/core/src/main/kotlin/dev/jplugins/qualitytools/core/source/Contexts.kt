package dev.jplugins.qualitytools.core.source

import dev.jplugins.qualitytools.core.context.CancellationToken
import dev.jplugins.qualitytools.core.context.QtLogger

/** Carried into [ConfigSource.resolve]. Project, cancellation, logger; no platform types. */
public interface ResolveContext {
    public val projectId: String
    public val basePath: String?
    public val cancellation: CancellationToken
    public val logger: QtLogger

    /** Free-form attribute bag — kept open so `:php` can add `phpInterpreterId` etc. */
    public fun attribute(key: String): String? = null
}

/** Carried into [ConfigSourceType.isAvailable]. */
public interface AvailabilityContext {
    public val projectId: String
    public fun hasPlugin(pluginId: String): Boolean
}

/** Carried into [ConfigSourceType.createWizard]. */
public interface WizardContext {
    public val projectId: String
    public val basePath: String?
}

/** Carried into [ConfigSourceType.watch]. */
public interface WatchContext {
    public val projectId: String
    public val basePath: String?
}
