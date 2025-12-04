# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is the **Lightning Server / Kite UI Combo Library** - a Kotlin Multiplatform project that implements form generation, live querying, pre-built auth components, caching, and an automatically generated admin interface for Lightning Server applications.

## Project Structure

Three main modules:
- **client** - Multiplatform library (Android, iOS, JS, JVM) providing forms, auth components, and caching
- **admin** - JS-only admin panel application built on top of the client
- **server-client-utils** - JVM-only utilities for server-side SDK generation

## Build Commands

### Core Build Tasks
```bash
# Build all modules
./gradlew build

# Build specific modules
./gradlew :client:build
./gradlew :admin:build
./gradlew :server-client-utils:build

# Clean build
./gradlew clean build
```

### Testing
```bash
# Run all tests
./gradlew allTests

# Run platform-specific tests
./gradlew :client:jvmTest           # JVM tests only
./gradlew :client:jsTest            # JS tests (browser)
./gradlew :client:iosX64Test        # iOS simulator tests
./gradlew :client:iosSimulatorArm64Test

# Run specific test class
./gradlew :client:jvmTest --tests "*.SortToConditionTest"
./gradlew :client:jvmTest --tests "*.CacheReadableTest"
```

### Admin Panel (Web)
```bash
# Build production web bundle (admin)
./gradlew :admin:viteBuild

# No dev server is configured - build and open the output manually
```

### Publishing
```bash
# Assemble source jars and documentation
./gradlew :client:androidReleaseSourcesJar
./gradlew :client:allMetadataJar
```

## Architecture

### Form Generation System (Renderer Pattern)

The core of the library is a pluggable **form rendering system** controlled by `FormModule`:

- **FormModule**: Central registry managing form/view renderers
- **FormRenderer.Generator**: Creates editable forms for `Mutable<T>` data
- **ViewRenderer.Generator**: Creates read-only views for `Readable<T>` data
- **Selection**: Renderers are chosen by type, annotations (`@AdminHidden`, `@Multiline`, etc.), and size requirements with priority-based selection
- **Built-in Renderers**: Comprehensive coverage of primitives, dates, collections, enums, nested objects, foreign keys, file uploads, etc.
- **Extensibility**: Register custom renderers via `FormModule.defaultRegistry.register(...)`

Forms are **cached** to handle recursive/nested data structures efficiently.

### ModelCache - Live Data Management

`ModelCache` provides intelligent client-side caching with real-time synchronization:

- **Multi-layer**: Combines local cache, batched requests, and optional WebSocket updates
- **Smart Polling**: Automatically fetches based on `maximumAge` and `pullFrequency` parameters
- **WebSocket Integration**: When available, uses `SharedCollectionUpdatesSocket` for real-time updates to reduce polling
- **Reactive Tracking**:
  - `ModelCacheItemReadable<T>` - Single item tracking by ID
  - `ModelCacheLimitReadable<T>` - Collection tracking with queries
- **Update Pipeline**: Single `newData` signal processes all updates (queries, mutations, socket events, deletions)
- **List Reconstruction**: `ListReconstructionCalculator` intelligently merges partial query results

### Authentication System

Modular **proof-based authentication** supporting multiple methods:

- **Proof Components**: Email, SMS, Password, TOTP, Backup Codes, WebAuthN/Passkeys
- **AuthComponent**: Main login flow with progressive proof gathering
- **ReAuthComponent**: Re-authentication for elevated permissions
- **Known Device**: Optional persistent device recognition
- **Session Management**: Configurable session lengths with refresh tokens

Related files: `client/src/commonMain/kotlin/com/lightningkite/kiteui/auth/`

### Admin Panel Architecture

Schema-driven auto-generated admin interface:

- **Schema-Driven**: Fetches `LightningServerKSchema` from server at runtime
- **ExternalLightningServer**: Bridges server schema to client-side `ModelCache` instances
- **Collection Screens**: Auto-generated CRUD with filtering, sorting, bulk import/export (CSV), customizable columns
- **Permissions-Aware**: Respects `ModelPermissions` from server
- **Real-time**: Built on `ModelCache` for live data updates

Related files: `admin/src/commonMain/kotlin/com/lightningkite/lightningserver/admin/`

### Lightning Server Integration

- **Typed Endpoints**: Client interfaces mirror server REST endpoints via code generation
- **ClientModelRestEndpoints**: Standard REST operations (query, insert, modify, delete, bulk)
- **ClientModelRestUpdatesWebsocket**: Optional WebSocket for live updates
- **SDK Generation**: Server can generate typed client SDKs with caching wrappers (`CachingSdk`)

### Kite UI Integration

- **ViewWriter DSL**: Declarative UI using Kite UI's view writer pattern
- **Reactive Bindings**: Deep integration with `Signal`, `Readable`, `Mutable`, `MutableReactive`
- **Navigation**: Page-based routing with query parameters
- **Platform Abstractions**: File upload, clipboard, downloads via `ExternalServices`

## Important Conventions

1. **Serialization-Driven**: Heavy use of `kotlinx.serialization` - most data models must be `@Serializable`
2. **Annotation Metadata**: UI behavior driven by annotations like:
   - `@AdminHidden` - Hide field in admin
   - `@Multiline` - Render text as textarea
   - `@MaxLength(n)` - Enforce max length
   - `@References(OtherModel::class)` - Foreign key rendering
3. **Reactive Context**: Most UI operations happen within `ReactiveContext` for automatic dependency tracking
4. **Resource Management**: `ResourceUse` pattern for cleanup when UI components unmount
5. **Lens Pattern**: Bidirectional transformations via `MutableReactive.lens` for mapping between data models and UI state
6. **Signal-based State**: Mutable state wrapped in `Signal<T>` for reactive updates

## Key Files

- `client/src/commonMain/kotlin/com/lightningkite/kiteui/forms/FormModule.kt` - Form registry
- `client/src/commonMain/kotlin/com/lightningkite/lightningserver/db/ModelCache.kt` - Caching system
- `client/src/commonMain/kotlin/com/lightningkite/kiteui/auth/` - Auth components
- `admin/src/commonMain/kotlin/com/lightningkite/lightningserver/admin/CollectionAdminScreen.kt` - Main admin CRUD screen

## Development Notes

- Use `./local/` directory for temporary files (not `/tmp`)
- Project uses custom Maven repo at `https://lightningkite-maven.s3.us-west-2.amazonaws.com`
- KSP (Kotlin Symbol Processing) generates field metadata in `build/generated/ksp/`
- iOS example project located at `example-app-ios/KiteUI Example App`
