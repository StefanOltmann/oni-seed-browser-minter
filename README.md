# ONI Seed Browser Minter

A Kotlin Multiplatform application that generates [Oxygen Not Included](https://www.klei.com/games/oxygen-not-included) worldgen clusters in-browser via WebAssembly and uploads them to a server for storage and retrieval.

## Architecture

```
┌─────────────────────────────────────┐
│  :web (wasmJs/Compose for Web)      │
│                                     │
│  ┌───────────────────────────────┐  │
│  │  ClusterMinter                │  │  ← Channel-based work queue
│  │  ├─ Producer coroutine        │  │    feeds (ClusterType, seed) pairs
│  │  └─ N Coroutine workers       │  │    consume from channel
│  └───────────┬───────────────────┘  │
│              │                      │
│  ┌───────────▼───────────────────┐  │
│  │  WorldgenWorkerPool           │  │  ← Multiple Web Workers
│  │  ├─ Web Worker 0 ─────────────│──│──→ worldgen.generate() (thread 0)
│  │  ├─ Web Worker 1 ─────────────│──│──→ worldgen.generate() (thread 1)
│  │  ├─ Web Worker 2 ─────────────│──│──→ worldgen.generate() (thread 2)
│  │  └─ Web Worker N ─────────────│──│──→ worldgen.generate() (thread N)
│  └───────────────────────────────┘  │
│              │ HTTP POST            │
│  ┌───────────▼───────────────────┐  │
│  │  WebClient                    │  │  ← Proper status code handling
│  └───────────────────────────────┘  │
└──────────────┼──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│  :server (Ktor/JVM + SQLite)        │
│  Stores cluster data                │
└─────────────────────────────────────┘
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

| Setting           | Default            | Description                                    |
|-------------------|--------------------|------------------------------------------------|
| Server URL        | `http://localhost:8080` | Backend server address                     |
| Start Seed        | `0`                | First seed to process (increments continuously) |
| Parallelism       | `15`               | Number of concurrent worker coroutines         |
| Worldgen Workers  | `4`                | Number of Web Workers (CPU threads for generation) |
| Cluster Filter    | *(empty)*          | Optional prefix filter (e.g. `V-SNDST-C`)     |

## How It Works

1. **Producer** continuously feeds `(ClusterType, seed)` pairs into a `Channel`
2. **N coroutine workers** consume from the channel independently — no per-seed waiting
3. Each coroutine worker is assigned a **Web Worker** from the pool
4. Multiple Web Workers run `worldgen.generate()` simultaneously in separate threads
5. Generated clusters are uploaded to the server via HTTP POST
6. Results (success/error) are displayed in real-time in the UI

**True CPU parallelism** is achieved via multiple Web Workers, not just coroutine concurrency.

## Dependencies

- Kotlin 2.4.0
- Compose Multiplatform 1.11.1
- Ktor 3.5.0
- ONI Worldgen: `@tigin-backwards/oxygen-not-included-worldgen` 3.0.2
- Model Library: `de.stefan-oltmann:oni-seed-browser-model` (commit `be2cbff`)

## License

See source files for license details.
