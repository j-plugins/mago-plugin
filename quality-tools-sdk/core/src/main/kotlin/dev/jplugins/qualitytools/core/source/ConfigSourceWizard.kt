package dev.jplugins.qualitytools.core.source

import dev.jplugins.qualitytools.core.context.ThreadingPolicy

/**
 * UI-agnostic wizard interface. `:ui`'s `ConfigSourceWizardHost` shows it
 * as a modal dialog; tests construct fakes directly.
 *
 * Preconditions:
 *  - `ConfigSourceType.isAvailable(ctx) == true` must hold before
 *    `ConfigSourceType.createWizard(ctx)` is invoked.
 */
public interface ConfigSourceWizard {
    /**
     * Returns `Result.success(source)` on OK, `Result.failure(CancellationException(...))`
     * on user-cancel, `Result.failure(other)` on actual error. Implementations
     * MUST NOT throw; encode failures via Result.
     */
    @ThreadingPolicy("edt")
    public fun show(): Result<ConfigSource>
}
