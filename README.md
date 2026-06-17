# ONI Seed Browser Minter

A Kotlin Multiplatform application that generates [Oxygen Not Included](https://www.klei.com/games/oxygen-not-included) worldgen clusters in-browser via WebAssembly and uploads them to a server for storage and retrieval.

## Architecture

```
┌─────────────────────────────────┐
│  :web (wasmJs/Compose for Web)  │
│                                 │
│  ┌───────────────────────────┐  │
│  │  ClusterMinter            │  │  ← Channel-based work queue
│  │  ├─ Producer coroutine    │  │    feeds (ClusterType, seed) pairs
│  │  └─ N Worker coroutines   │  │    workers consume independently
│  └───────────┬───────────────┘  │
│              │                  │
│  ┌───────────▼───────────────┐  │
│  │  ClusterGenerator         │  │  ← WASM worldgen via Web Worker
│  └───────────┬───────────────┘  │
│              │ HTTP POST        │
└──────────────┼──────────────────┘
               │
┌──────────────▼──────────────────┐
│  :server (Ktor/JVM + SQLite)    │
│  Stores cluster data            │
└─────────────────────────────────┘
```

## Modules

- **`:server`** — Ktor server with SQLite storage via Exposed
- **`:web`** — Compose for Web (wasmJs) frontend with WASM worldgen

## Build & Run

```bash
# Compile web frontend
./gradlew :web:compileKotlinWasmJs

# Run web dev server (webpack-dev-server, port 8081)
./gradlew :web:wasmJsBrowserDevelopmentRun

# Compile server
./gradlew :server:compileKotlin

# Run server (default port 8080)
./gradlew :server:run
```

## Configuration

The web frontend provides these settings:

| Setting      | Default            | Description                                    |
|--------------|--------------------|------------------------------------------------|
| Server URL   | `http://localhost:8080` | Backend server address                     |
| Start Seed   | `0`                | First seed to process (increments continuously) |
| Parallelism  | `15`               | Number of concurrent worker coroutines         |
| Cluster Filter | *(empty)*        | Optional prefix filter (e.g. `V-SNDST-C`)     |

## How It Works

1. **Producer** continuously feeds `(ClusterType, seed)` pairs into a `Channel`
2. **N workers** consume from the channel independently — no per-seed waiting
3. Each worker calls the WASM worldgen Web Worker via `ClusterGenerator`
4. Generated clusters are uploaded to the server via HTTP POST
5. Results (success/error) are displayed in real-time in the UI

The single WASM Web Worker processes generation requests sequentially, but uploads overlap with the next generation.

## Dependencies

- Kotlin 2.4.0
- Compose Multiplatform 1.11.1
- Ktor 3.5.0
- ONI Worldgen: `@tigin-backwards/oxygen-not-included-worldgen` 3.0.2
- Model Library: `de.stefan-oltmann:oni-seed-browser-model` (commit `be2cbff`)

## License

See source files for license details.
