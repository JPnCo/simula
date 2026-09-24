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

---

# Addendum: Traffic-Light Example Entities

Illustrative actors for the `jpnco.simula.examples.trafficlight` sample. All are implemented with the delegate pattern (`ActorDelegate.createDelegate(engine, this)`) and expose the three required methods (`getDelegate()`, `getId()`, `process(Event)`). They are demonstration code, not part of the framework contract, and are excluded from the coverage gate.

## TrafficLight (per intersection)

Cycles a traffic light through states on a timer. State machine: `RED → GREEN → ORANGE → RED`.

| Field | Type | Notes |
|-------|------|-------|
| `state` | TrafficLight.State | Current light state (enum RED/GREEN/ORANGE) |
| `engine` | Engine | Owning intersection (child) engine |
| `id` | Integer | From `IdBuilder` |

**State transitions**: driven by `TimeSource` alarms (`REQUEST_ALARM` with a period). On each alarm fire the light advances to the next state and signals a `LIGHT_CHANGED` event to its intersection.

## VehicleSensor (per intersection)

Counts vehicles passing through an intersection.

| Field | Type | Notes |
|-------|------|-------|
| `count` | int | Number of vehicles detected |
| `engine` | Engine | Owning intersection (child) engine |
| `id` | Integer | From `IdBuilder` |

**Behavior**: subscribes to a `VEHICLE` topic; on each `VEHICLE` event it increments `count` and signals a `VEHICLE_COUNT` event to the monitor.

## IntersectionController (per intersection, runs on child engine)

Owns the local state of one intersection and is the subscription hub for its actors.

| Field | Type | Notes |
|-------|------|-------|
| `name` | String | Intersection label (e.g. "intersection-1") |
| `lightState` | TrafficLight.State | Last known light state |
| `vehicleCount` | int | Aggregated vehicle count |
| `engine` | Engine | Owning child engine |
| `id` | Integer | From `IdBuilder` |

**Behavior**: subscribes to `LIGHT_CHANGED` and `VEHICLE_COUNT`; forwards summary events to the root monitor.

## TrafficMonitor (runs on root engine)

Aggregates state from both intersections and prints a global status on each `TIME_EVENT`.

| Field | Type | Notes |
|-------|------|-------|
| `states` | Map<String, IntersectionController> | Keyed by intersection name |
| `engine` | Engine | Root engine |
| `id` | Integer | From `IdBuilder` |

**Behavior**: subscribes to intersection summary topics; on `TIME_EVENT` prints the aggregated light states and vehicle counts.

## Message topics (named constants)

- `VEHICLE` — a vehicle detected at an intersection (sensor → controller).
- `LIGHT_CHANGED` — light state changed (light → controller).
- `LIGHT_CHANGED_<intersection>` — forwarded to monitor (controller → monitor).
- `VEHICLE_COUNT_<intersection>` — count forwarded to monitor (controller → monitor).
