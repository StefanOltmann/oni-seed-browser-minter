# AGENTS.md — Development Guidelines for oni-seed-browser-minter

## Project Overview

This is a **Kotlin Multiplatform** project with two modules:

- **`:server`** — Ktor/JVM server that stores ONI worldgen cluster data in SQLite
- **`:web`** — Compose for Web (wasmJs) frontend that generates clusters via WASM worldgen and uploads them to the server

## Build & Run Commands

```bash
# Compile web frontend
./gradlew :web:compileKotlinWasmJs

# Run web dev server (webpack-dev-server on port 8081)
./gradlew :web:wasmJsBrowserDevelopmentRun

# Compile server
./gradlew :server:compileKotlin

# Run server
./gradlew :server:run
```

## Code Style

### Comment Style

**Always use block-style comments** (`/* ... */` or `/** ... */`). Avoid line comments (`//`) except for `// region`/`// endregion` folding markers.

```kotlin
/*
 * This is the correct comment style.
 * Use for file headers and inline explanations.
 */

/**
 * KDoc for public API documentation.
 * Use for classes, functions, and properties.
 */
```

### One-Line IFs

**Never use curly braces for single-statement `if` bodies.** This applies to both standalone `if` blocks and `if`/`else` expressions where each branch is a single statement.

```kotlin
/* Correct — no braces for single statement */
if (condition)
    doSomething()

/* Correct — if/else expression, both branches single-statement */
val x = if (condition) valueA else valueB

/* Wrong — unnecessary braces */
if (condition) {
    doSomething()
}
```

Don't apply this if there is a multi-line else.

### Clean Code Principles

- **Single Responsibility**: Each class, composable, and function does one thing
- **File Organization**: Each class and composable gets its own file
- **Naming**: Descriptive names. No abbreviations unless universally understood
- **Functions**: Short, focused, no side effects where possible
- **No Magic Numbers**: Use named constants

## File Organization

### Service Layer (`service/`)

- `MinterState.kt` — All data classes (state models, log entries)
- `ClusterMinter.kt` — Core minter logic (generation + upload orchestration)
- `ClusterGenerator.wasmJs.kt` — WASM worldgen cluster generation (uses worker pool)
- `Worldgen.wasmJs.kt` — WASM worker communication with `WorldgenWorkerPool`
- `WebClient.kt` — HTTP client for uploads with proper status code handling

### UI Layer (`ui/`)

- `App.kt` — Main composable, wires everything together
- One file per composable or closely related composable group
- `theme/Theme.kt` — Colors, typography, field style utilities

### Worldgen (`worldgen/`)

- `Worldgen.kt` — Expect declarations for worldgen operations
- `WorldgenModels.kt` — Serializable data models from worldgen JSON
- `WorldgenMapDataConverter.kt` — Converts raw worldgen data to app models
- `CoordinateUtil.kt` — Coordinate string utilities
- `LruCache.kt` — Generic LRU cache

## Architecture Patterns

### State Management

State flows from `ClusterMinter` → `onStateUpdate` callback → Compose recomposition.

```kotlin
/* State is a data class — updated via copy() */
data class MinterState(
    val isRunning: Boolean = false,
    val workers: List<WorkerStatus> = emptyList(),
    ...
)
```

### Parallelism Model

Generation uses a **Channel-based work queue** with **multiple Web Workers** for true CPU parallelism:

1. A **producer coroutine** continuously feeds `(ClusterType, seed)` pairs into a `Channel`
2. **N worker coroutines** (controlled by parallelism setting) consume from the channel
3. Each coroutine worker is assigned a **Web Worker** from the `WorldgenWorkerPool`
4. Multiple Web Workers run `worldgen.generate()` simultaneously in separate threads
5. Workers independently generate and upload — no batching by seed

```
Producer → [Channel] → Coroutine Worker 0 → Web Worker 0 → generate → upload
                     → Coroutine Worker 1 → Web Worker 1 → generate → upload
                     → ...
                     → Coroutine Worker N → Web Worker N → generate → upload
```

**Configuration:**

- `parallelism` — Number of coroutine workers (concurrent tasks)
- `worldgenWorkers` — Number of Web Workers (CPU threads for generation)

### Error Handling

- All exceptions are caught and logged to the UI log panel
- Upload failures (HTTP errors, connection errors) are reported per-coordinate via `UploadResult` sealed class
- Generation failures are reported per-coordinate
- The minter continues processing after errors — no single failure stops the pipeline

### WebClient

Dedicated HTTP client for uploads with proper status code handling:

```kotlin
sealed class UploadResult {
    data class Success(val body: String) : UploadResult()
    data class Failure(val statusCode: Int, val message: String) : UploadResult()
    data class Error(val exception: Exception) : UploadResult()
}
```

The `WebClient` explicitly checks `response.status.isSuccess()` and reads `bodyAsText()` to detect all error conditions.

## Dependencies

- Kotlin 2.4.0, Compose Multiplatform 1.11.1, Ktor 3.5.0
- ONI worldgen: npm package `@tigin-backwards/oxygen-not-included-worldgen` (3.0.2)
- Model library: `de.stefan-oltmann:oni-seed-browser-model` (commit `be2cbff`)

## Key Constraints

- **True CPU parallelism** is achieved via `WorldgenWorkerPool` — multiple Web Workers run worldgen in parallel
- The worldgen worker (`worldgen.worker.mjs`) must not be modified — it's copied from the working reference project
- `.mjs` files are served as ES modules via webpack
- Each `WorldgenWorker` has its own ID space and callback map for request/response correlation
