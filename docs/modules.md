# Modules

SSHomcat supports optional add-on modules, loaded from a directory of JAR files at startup. This
is entirely separate from the core webapp's own code -- with `modules.directory` unset (the
default), SSHomcat behaves exactly as if this feature didn't exist. See
[roadmap.md](roadmap.md) M3 for the feature this exists to support ("more than one configured
target -- still no arbitrary client-supplied targets, a small server-side allowlist, not an open
relay").

## How discovery works

1. Set `modules.directory=/path/to/dir` in `sshomcat.properties` (see
   `sshomcat.properties.example`).
2. Drop one or more module JARs directly in that directory (not nested in subfolders).
3. Each JAR needs a `META-INF/services/com.sshomcat.module.SshomcatModule` file listing the
   fully-qualified class name(s) of its `SshomcatModule` implementation(s) -- standard
   `java.util.ServiceLoader` discovery, nothing SSHomcat-specific.
4. At startup, `AppContextListener` -> `AppServices.init()` -> `ModuleLoader.loadFrom(...)` adds
   every JAR in that directory to one classloader, discovers every `SshomcatModule` via
   `ServiceLoader`, and calls `init()` on each. A module that throws out of `init()` (or fails to
   instantiate) is logged and skipped -- it never stops the rest of the webapp from starting.

Modules are trusted, admin-supplied code, not sandboxed in any way -- the same trust model as
choosing to deploy any other JAR. A module's `init()` (and anything it does afterward) can call
into `com.sshomcat.AppServices` directly for config/shared services, same as the rest of this
codebase does -- there's no separate dependency-injection context to learn.

## The `SshomcatModule` contract

```java
public interface SshomcatModule {
    String name();
    default void init() { }
}
```

That's the entire base contract. On its own it does nothing observable -- `name()` is used for
log output, `init()` is a startup hook. Actual behavior comes from implementing one of the
more specific interfaces below (more may be added later; this file will grow with them).

## `TargetResolver`

```java
public interface TargetResolver extends SshomcatModule {
    Optional<Target> resolveTarget(String username);
    record Target(String host, int port) { }
}
```

Lets a module pick a different outbound SSH target per authenticating user, instead of the one
fixed `target.host`/`target.port` in `sshomcat.properties`. `SshBridgeEndpoint` tries every loaded
`TargetResolver`, in load order, and uses the first present result; if none resolve anything for
a given user, it falls back to the configured default target exactly as if no resolver modules
were loaded at all.

**This does not weaken [security.md](security.md)'s constraint #1.** `resolveTarget` only ever
receives a username -- never a client-supplied host or port. What it returns has to come from
data the module itself owns server-side (a properties file, a small embedded database, whatever
it implements); there is no code path anywhere in core SSHomcat that lets a browser influence
which host a `TargetResolver` picks. "Fixed, server-side configuration" still holds -- this
interface just lets "fixed" mean "one of several curated options" instead of "exactly one."

Keep this in mind before building anything that touches target selection: the difference
between this feature and a client-controlled-target (SSRF) vulnerability is entirely *where the
target value comes from*. A `TargetResolver` implementation that reads its mapping
from anything client-influenced (a query parameter, a header, a field on the `auth` message
itself) would collapse that distinction and reintroduce exactly the SSRF risk this doc's own
design deliberately avoids.

## Packaging a module

A module is an ordinary JAR with:

- One or more classes implementing `SshomcatModule` (or a more specific interface like
  `TargetResolver`), each with a public no-arg constructor (`ServiceLoader` requirement).
- `META-INF/services/com.sshomcat.module.SshomcatModule`, one fully-qualified class name per
  line, one line per implementation in the JAR.
- Whatever else the module needs bundled (its own small dependencies, resource files) -- there is
  no shared classpath between modules; each JAR is self-contained.

No SSHomcat-specific build plugin or archetype is required -- any JDK/Maven/Gradle setup that
produces a plain JAR with that services file works.

Compile against the `module-api` project in this repo (`com.sshomcat:module-api`, installed to
your local Maven repository by `mvn install` from the repo root), with `provided` scope: the
backend WAR already bundles it, so a module JAR should not carry its own copy. The easiest
starting point is to copy `modules/motd-banner/` and add it to the root `pom.xml`'s `<modules>`.

## Bundled modules

Both live under `modules/` and build with the rest of the repo; each produces a JAR in its own
`target/` directory (also attached to every GitHub release).

### `multi-target-router`

A `TargetResolver` that picks the outbound SSH target per authenticated username from an
admin-curated allowlist file -- the "more than one configured target" item from
[roadmap.md](roadmap.md) M3.

- Allowlist path: `-DmultiTargetRouter.allowlistFile=<path>` (a JVM system property, e.g. via
  `CATALINA_OPTS`), default `/opt/sshomcat/modules/multi-target-router.properties`.
- Format: a Java properties file, one `username=host:port` line per user. Malformed lines are
  logged and skipped.
- Users with no entry get `Optional.empty()`, i.e. fall through to the default `target.host` /
  `target.port`. A missing or unreadable file leaves the module resolving nothing.
- The file is read once at startup; restart the webapp to pick up changes.

### `motd-banner`

Logs a one-line banner at startup: `-DmotdBanner.message=<text>` if set, otherwise one of a few
built-in defaults. No other behavior -- it exists as the smallest complete module to copy from.
