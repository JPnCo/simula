# Data Model: Configurable Thread Execution

Entities relevant to the `002-configurable-threads` feature, derived from the feature spec's Key Entities and the existing framework. This is the in-memory domain model of the simulation framework; there is no persistence layer.

## Engine

The orchestrator that runs actors and child engines, owns a logger and (for the root) a time source, and manages topic subscriptions.

| Field | Type | Notes / Validation |
|-------|------|--------------------|
| `id` | Integer | Unique per engine (from `IdBuilder`); identity basis |
| `name` | String | Human-readable engine name |
| `executionMode` | ExecutionMode | NEW (FR-001/003); default `VIRTUAL` (FR-002); fixed at construction |
| `timeFactor` | int | Time factor for simulated seconds |
| `parent` | Engine | Null for root engine |
| `children` | Set\<Engine\> | Guarded by a reentrant lock (FR-010/012) |
| `actors` | Map\<Integer,Actor\> | Guarded by a reentrant lock |
| `subscribersByTopic` | Map\<String,Set\<Actor\>\> | Guarded by a reentrant lock |

**State transitions**: `created → running → stopped`. Start/stop cascade to children (FR-008). Locking must be reentrant (FR-012) and released on all paths (FR-011).

## ExecutionMode (NEW)

Selection of actor thread execution strategy.

| Value | Meaning |
|-------|---------|
| `VIRTUAL` | Actors run on virtual threads (default, FR-002) |
| `PLATFORM` | Actors run on classic platform threads |

**Validation**: mode must be one of the enum values; an invalid/unknown value is rejected with a clear error (FR-007). Mode is fixed when the engine is created (FR-003, clarification Q1).

## Actor

An independent execution unit started by the engine under the selected execution mode.

| Field | Type | Notes |
|-------|------|-------|
| `id` | Integer | Unique per actor (from `IdBuilder`) |
| `name` | String | Thread name used in both modes (FR-006) |
| `engine` | Engine | Owning engine |
| `delegate` | Actor | Delegation target for standard behavior |

## Lock (NEW)

An explicit reentrant mutual-exclusion mechanism replacing intrinsic `synchronized` monitors.

| Field | Type | Notes |
|-------|------|-------|
| `reentrant` | boolean | Must be reentrant (FR-012) |
| `released` | boolean | Must be released on all paths including exceptions (FR-011) |

**Locks introduced**:
- Per-engine `ReentrantLock` (recommended single lock per engine) guarding `children`, `actors`, and `subscribersByTopic`.
- Static `ReentrantLock` in `IdBuilder` guarding the shared counter.

**Lock-ordering rule**: if more than one lock is held per operation, acquire in a consistent global order (e.g., `actors` before `children`) to avoid deadlock; a single per-engine lock eliminates this risk (see research.md).

## Event

A fact signaled on a named topic at a given time. Unchanged by this feature; unaffected by execution mode (FR-005).

## TimeSource / Alarm / Logger

Unchanged by this feature except that they run as actors under the selected execution mode. The TimeSource's clock executor is a framework detail resolved in the design; it is not part of the configurable execution-mode contract (scope per clarification Q2).
