package dev.jplugins.qualitytools.core.source

/**
 * Translates paths between the editor's view of the filesystem and what
 * the tool sees. The `Identity` mapper returns `canProcess == false`, so
 * `PathAwareArgRewriter` is a cheap no-op when no remote source is used.
 */
public interface PathMapper {
    public fun toRemote(localPath: String): String = localPath
    public fun toLocal(remotePath: String): String = remotePath

    /** Whether [toRemote] or [toLocal] would change [localPath]. Identity = false. */
    public fun canProcess(localPath: String): Boolean = false

    public companion object {
        public val Identity: PathMapper = object : PathMapper {}
    }
}
