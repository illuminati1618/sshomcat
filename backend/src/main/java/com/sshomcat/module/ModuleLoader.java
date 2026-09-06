package com.sshomcat.module;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads optional add-on modules from a directory of JAR files at startup. Not part of the core
 * bridge -- SSHomcat runs correctly with zero modules loaded (a missing/empty/unset
 * {@code modules.directory} is not an error, just "no modules"). See the separate
 * sshomcat-modules repo for actual module implementations, and docs/modules.md for the contract
 * they implement.
 *
 * <p>Every {@code .jar} directly under {@code modules.directory} is added to a single
 * {@link URLClassLoader} (modules are trusted, admin-supplied code -- this is not a sandbox,
 * same trust model as any other JAR an operator chooses to deploy). {@link ServiceLoader} then
 * looks for {@code META-INF/services/com.sshomcat.module.SshomcatModule} entries across all of
 * them. A module that fails to instantiate or throws out of {@link SshomcatModule#init()} is
 * logged and skipped -- one bad module JAR must never stop the whole webapp from starting.
 */
public final class ModuleLoader {

    private static final Logger LOG = Logger.getLogger(ModuleLoader.class.getName());

    private final List<SshomcatModule> modules;

    private ModuleLoader(List<SshomcatModule> modules) {
        this.modules = modules;
    }

    public static ModuleLoader loadFrom(String directoryPath) {
        if (directoryPath == null || directoryPath.isBlank()) {
            return new ModuleLoader(Collections.emptyList());
        }

        Path dir = Path.of(directoryPath);
        if (!Files.isDirectory(dir)) {
            LOG.info(() -> "modules.directory '" + directoryPath + "' does not exist -- no modules loaded.");
            return new ModuleLoader(Collections.emptyList());
        }

        List<URL> jarUrls = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.jar")) {
            for (Path jar : stream) {
                jarUrls.add(jar.toUri().toURL());
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING,
                    "Failed to list modules.directory '" + directoryPath + "' -- no modules loaded.", e);
            return new ModuleLoader(Collections.emptyList());
        }

        if (jarUrls.isEmpty()) {
            LOG.info(() -> "modules.directory '" + directoryPath + "' contains no .jar files -- no modules loaded.");
            return new ModuleLoader(Collections.emptyList());
        }

        URLClassLoader loader = new URLClassLoader(
                jarUrls.toArray(new URL[0]), ModuleLoader.class.getClassLoader());

        List<SshomcatModule> loaded = new ArrayList<>();
        for (SshomcatModule module : ServiceLoader.load(SshomcatModule.class, loader)) {
            try {
                module.init();
                loaded.add(module);
                LOG.info(() -> "Loaded module: " + module.name());
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING,
                        "Module failed to initialize -- skipping: " + module.getClass().getName(), e);
            }
        }
        return new ModuleLoader(List.copyOf(loaded));
    }

    public List<SshomcatModule> modules() {
        return modules;
    }

    /** Returns loaded modules that implement {@code type}, preserving load order. */
    public <T extends SshomcatModule> List<T> modulesOfType(Class<T> type) {
        List<T> result = new ArrayList<>();
        for (SshomcatModule m : modules) {
            if (type.isInstance(m)) {
                result.add(type.cast(m));
            }
        }
        return result;
    }
}
