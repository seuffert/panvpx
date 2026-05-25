# Contributing to panvpx

Thank you for your interest in contributing! This guide covers everything you need to get started.

## Prerequisites

- JDK 25 or higher (`java --version`)
- System `libvpx` installed (see [README.md — Requirements](README.md#requirements))
- `git`

## Getting started

```bash
git clone https://gitlab.com/org.seuffert/panvpx.git
cd panvpx
./gradlew build
```

`BUILD SUCCESSFUL` confirms that your environment is set up correctly. The `build` task runs
compilation, tests, and all static analysis checks.

## Making changes

1. Fork the repository on GitLab.
2. Create a feature branch: `git checkout -b feat/my-feature`.
3. Make your changes following the conventions below.
4. Run `./gradlew build --rerun-tasks` to verify the full build (compile + tests + static analysis).
5. Commit, push your branch, and open a Merge Request.

## Code conventions

### Formatting

Code is automatically formatted with **Google Java Format (AOSP variant)**. Run
`./gradlew spotlessApply` to auto-format before committing. The CI build will fail if
formatting is not applied.

### Nullability

Annotate parameters, fields, and return values with `@Nullable` (from
[JSpecify](https://jspecify.dev/)) where a `null` value is permitted.
[NullAway](https://github.com/uber/NullAway) enforces null-safety at compile time across the
`org.seuffert.panvpx` package.

### `final` everywhere

Declare local variables and method parameters `final` wherever they are not reassigned.
PMD enforces this via `LocalVariableCouldBeFinal` and `MethodArgumentCouldBeFinal`.

### Accessor naming

Boolean accessor methods must follow the `isXxx()` naming convention (not `getXxx()`).

### Memory management

Follow the patterns documented in [AGENTS.md](AGENTS.md):

- Per-call native allocations: `Arena.ofConfined()` in a `try-with-resources` block.
- Long-lived encoder/decoder arenas: `Arena.ofShared()` as a `private final` field, closed in `close()`.
- Never use `Arena.global()` in library code.

### Javadoc

All `public` and `protected` members in `org.seuffert.panvpx` (and sub-packages, excluding
`org.seuffert.panvpx.ffi`) must have Javadoc. Checkstyle enforces this.

### Tests

New functionality must be accompanied by JUnit 5 unit tests placed under
`lib/src/test/java/org/seuffert/panvpx/`.

## Static analysis

Five Gradle plugins enforce quality on every build:

| Tool | What it checks |
|---|---|
| **Spotless** | Code formatting (Google Java Format AOSP) |
| **ErrorProne + NullAway** | Compiler-integrated bug patterns and null-safety |
| **Checkstyle** | Style, import order, Javadoc completeness |
| **SpotBugs** | Bytecode-level bug-pattern detection |
| **PMD** | Source-level static analysis rules |

Do not disable or weaken these checks. If a rule is genuinely inapplicable to a specific
piece of code, add a named `<exclude>` to the relevant config file under `config/` with a
comment explaining why.

## Commit messages

Use the [Conventional Commits](https://www.conventionalcommits.org/) style:

```
feat(vp8): add two-pass encoding support
fix(core): correct Arena lifecycle in VpxImage.fromMemorySegment
test(vp8): add flush integration test
docs: update README usage examples
chore: bump checkstyle to 13.5.0
```

## Reporting issues

Open an issue on the
[GitLab issue tracker](https://gitlab.com/org.seuffert/panvpx/-/issues). Please include:

- Your OS and JDK version (`java --version`)
- The `libvpx` version (`pkg-config --modversion vpx` on Linux, `brew info libvpx` on macOS)
- A minimal reproducible example

## License

By contributing, you agree that your contributions will be licensed under the
[Apache License 2.0](LICENSE).
