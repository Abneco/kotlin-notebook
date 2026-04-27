# Contributing to Kotlin Notebook

Thank you for your interest in contributing to the Kotlin Notebook plugin!

## How to build locally

### Prerequisites

- JDK 25
- Git

### Getting the source code

```sh
git clone https://github.com/Kotlin/kotlin-notebook.git
```

### Building

To build the plugin distribution, run:

```sh
./gradlew build
```

### Running in a sandbox IDE

To launch an IntelliJ IDEA instance with the plugin installed:

```sh
./gradlew runIde
```

### Tests

Run the full test suite with:

```sh
./gradlew test
```

## Repository layout

The project is a multi-module Gradle build. The root project contains the plugin descriptor
(`plugin/resources/META-INF/plugin.xml`) and assembles all content modules into the final
plugin distribution.

Content modules live in subdirectories at the repo root:

```
core/              Core notebook editor and REPL connectivity
k1/                K1 (classic frontend) specific analysis
k2/                K2 (FIR frontend) specific analysis
debug/             Debugger integration
debug/renders/     Custom debugger renderers
plots/             Let's Plot visualisation support
tables/            DataFrame / table rendering
sql/               SQL language injection in cells
export/pdf/        PDF export
buildSystems/gradle/  Gradle project integration
liveTemplates/     Kotlin Notebook live templates
notekit/           Notekit API support
performancePlugin/ Performance testing commands
tests/unitTests/   Unit and integration tests
tests/perfTests/   Performance tests
```

## Code style

We follow the official [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html).

## Making pull requests

Please reference the relevant [YouTrack issue](https://youtrack.jetbrains.com/issues/KTNB) in your
PR title, e.g. `KTNB-794: Fix notebook serialization on Windows`.
