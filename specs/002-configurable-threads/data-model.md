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

Illustrative classes for the `jpnco.simula.examples.trafficlight` sample. The coordinating actor is implemented with the delegate pattern (`ActorDelegate.createDelegate(engine, this)`) and exposes the three required methods (`getDelegate()`, `getId()`, `process(Event)`). They are demonstration code, not part of the framework contract, and are excluded from the coverage gate.

## Direction (enum)

A cardinal direction of travel on the grid: `NORTH`, `SOUTH`, `EAST`, `WEST`, each with a `(rowDelta, colDelta)`.

| Field | Type | Notes |
|-------|------|-------|
| `rowDelta` | int | Row change for one step (N/S = ∓1, E/W = 0) |
| `colDelta` | int | Column change for one step (E/W = ±1, N/S = 0) |

**Behavior**: `rowDelta()`/`colDelta()` give the displacement of one segment; `isVertical()` reports whether the direction uses the north-south band of a light.

## Vehicle

A vehicle travelling the grid. It holds an identity, a current cell (the origin of the segment it is on), a direction, a fixed speed, and the distance already travelled into the current segment. It persists for the whole demo and loops forever around the toroidal grid, which is what makes the network a closed circuit.

| Field | Type | Notes |
|-------|------|-------|
| `id` | int | Vehicle identity |
| `speed` | double | Fixed speed in m/s (chosen in [15, 45] km/h) |
| `row`, `col` | int | Origin intersection of the current segment |
| `direction` | Direction | Current travel direction |
| `distanceInSegment` | double | Metres travelled into the current segment (0..SEGMENT_LENGTH) |

**Behavior**: `advance(metres)` adds travelled distance; `enterNextSegment(nextRow, nextCol, newDirection)` moves the vehicle onto the next segment and resets the travelled distance; `stopAtBoundary(segmentLength)` clamps it to the end of the segment so it waits at the intersection.

## TrafficCoordinator (runs on root engine)

The single actor that owns the grid and advances the whole fleet on each `TIME_EVENT`. Maintains a `GRID_SIZE` × `GRID_SIZE` grid with a traffic light at each intersection.

| Field | Type | Notes |
|-------|------|-------|
| `vehicles` | List\<Vehicle\> | The fixed fleet (INITIAL_VEHICLES = 25) |
| `crossings` | int[][] | Per-cell crossing counts |
| `state` | volatile GridState | Latest immutable snapshot, published after every step (SC-003) |
| `display` | GridDisplay | Sink that receives each snapshot (console or GUI) |
| `done` | CountDownLatch | Released once the run completes |
| `random` | Random | Seeded (RANDOM_SEED) for reproducibility (SC-003) |

**Behavior**: on each `TIME_EVENT` it increments the simulated time, advances every vehicle, rebuilds the snapshot, forwards it to the `display`, and stops the engine (releasing `done`) once the configured `durationSeconds` is reached. Movement is continuous and realistic: each segment is `SEGMENT_LENGTH` (250 m) and a vehicle advances `speed × 1s` per step; it enters the next segment only when the light of the next intersection is green for its direction, otherwise it stops at the boundary and waits. It may turn randomly at an intersection (TURN_PROBABILITY). The light of an intersection alternates between letting north-south and east-west traffic flow, staggered by row and column (`isNorthSouthGreen(row, col, time)`).

## GridState (immutable snapshot)

An immutable snapshot of the grid at one instant, so displays can read it safely from another thread.

| Field | Type | Notes |
|-------|------|-------|
| `simTime` | int | Simulated time |
| `vehicles` | List\<VehicleView\> | Immutable vehicle views |
| `northSouthGreen` | boolean[][] | Green direction per intersection |
| `crossings` | int[][] | Per-cell crossing counts |

**Behavior**: exposes the vehicle list, per-cell light state and crossing counts.

## VehicleView (immutable)

An immutable view of one vehicle's position and direction at an instant: `id`, `row`, `col`, `direction`, `distanceInSegment`.

## GridDisplay (interface)

A sink that receives each `GridState` snapshot. The console mode uses `TrafficMonitor`; the GUI mode supplies a no-op display while the Swing window renders on its event dispatch thread.

## TrafficMonitor (console GridDisplay)

Prints the grid each simulated second to `System.out`: one cell per intersection, where the light marker is `|` when north-south is green and `-` when east-west is green, with the number of vehicles occupying the cell.

## TrafficLightGui (Swing/Java2D GridDisplay)

A Swing window that visualizes the grid in real time. A `Timer` polls the coordinator snapshot on the Event Dispatch Thread and repaints. Each cell draws its two road bands (north-south and east-west) and a short green segment (about `GREEN_SEGMENT_METERS` = 20 m) on the band whose traffic light is green; every vehicle is drawn as a dot at its current position along its segment. The status line shows the simulated time, the vehicle count and the total crossings so far.
