# Contributing to the MAIG Android SDK

Thank you for taking the time to contribute! This document covers everything you need to get started.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [How to Contribute](#how-to-contribute)
- [Development Setup](#development-setup)
- [Running Tests](#running-tests)
- [Submitting a Pull Request](#submitting-a-pull-request)
- [Style Guide](#style-guide)
- [Releasing](#releasing)

---

## Code of Conduct

This project follows our [Code of Conduct](CODE_OF_CONDUCT.md). By participating, you agree to uphold it.

---

## Getting Started

1. **Fork** the repository on GitHub.
2. **Clone** your fork locally:
   ```bash
   git clone https://github.com/<your-username>/maig-android-sdk.git
   cd maig-android-sdk
   ```
3. Create a **feature branch** from `main`:
   ```bash
   git checkout -b feature/my-improvement
   ```

---

## How to Contribute

| Type | What to do first |
|------|-----------------|
| Bug fix | Search existing issues; open one if none exists |
| New feature | Open a discussion issue before writing code |
| Documentation | Just open a PR |
| Tests | Always welcome — open a PR directly |

---

## Development Setup

### Prerequisites

- **JDK 17+** (`java -version`)
- **Gradle 8.10+** or use the included wrapper

### Bootstrap the Gradle wrapper (first time only)

The `gradle-wrapper.jar` binary is not committed to the repo. Generate it with:

```bash
gradle wrapper --gradle-version 8.10
```

If you do not have Gradle installed, download it from [gradle.org](https://gradle.org/install/) or use [SDKMAN](https://sdkman.io/):

```bash
sdk install gradle 8.10
gradle wrapper --gradle-version 8.10
```

After that, always use `./gradlew` (or `gradlew.bat` on Windows) so everyone uses the same Gradle version.

### Build

```bash
./gradlew :sdk:assemble
```

---

## Running Tests

```bash
./gradlew :sdk:test
```

HTML reports are written to `sdk/build/reports/tests/test/index.html`.

### Test conventions

- Every public API change must include test coverage.
- Use the `MockNetworkClient` / `StreamMockNetworkClient` helpers in `src/test/` — **do not** make live network calls in tests.
- Coroutine tests must use `runTest { }` from `kotlinx-coroutines-test`.

---

## Submitting a Pull Request

1. Make sure all tests pass: `./gradlew :sdk:test`
2. Keep commits focused; squash fixup commits before submitting.
3. Fill in the PR template (title, description, test plan).
4. Reference any related issues with `Closes #123`.
5. A maintainer will review within a few business days.

---

## Style Guide

- Follow the official [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html).
- Keep public API surface small — internal implementation details go in the `internal` package.
- Document every public class and function with KDoc.
- Do not add dependencies without discussing in an issue first.

---

## Releasing

> This section is for maintainers.

1. Update `version` in `sdk/build.gradle.kts`.
2. Add a new section to `CHANGELOG.md` under `[Unreleased]`.
3. Commit: `git commit -m "chore: release vX.Y.Z"`.
4. Tag: `git tag vX.Y.Z && git push origin vX.Y.Z`.
5. CI will publish the artifact to Maven Central automatically.
