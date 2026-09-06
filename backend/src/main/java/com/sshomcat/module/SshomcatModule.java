package com.sshomcat.module;

/**
 * Contract for an optional add-on module, loaded from a JAR under {@code modules.directory} at
 * startup (see {@link ModuleLoader} and docs/modules.md for the full contract, discovery
 * mechanism, and how to package one).
 *
 * <p>SSHomcat runs correctly with zero modules loaded -- this interface exists purely so
 * additional behavior (e.g. {@link TargetResolver}) can be dropped in without touching this
 * repo's own source. Modules that need shared state read it from {@link com.sshomcat.AppServices}
 * directly, same as every other class in this codebase -- see that class's own javadoc for why
 * (plain static holder, no DI container).
 */
public interface SshomcatModule {

    /** Short, human-readable name for log output -- not used for lookup/identity. */
    String name();

    /**
     * Called once, right after the module is discovered, before the webapp finishes starting.
     * Throwing here means "this module failed to load": {@link ModuleLoader} logs it and skips
     * the module -- one bad module must never stop the whole webapp from starting.
     */
    default void init() {
    }
}
