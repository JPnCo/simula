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

**Note (moved)**: the `trafficlight` sample was extracted out of this project into the separate project **`C:\JPC\PERSO\SDD\SIMULA_SAMPLES`** (artifact `jpnco:simula-samples`). Its root package there is `jpnco.simula.samples.trafficlight`, split into `…trafficlight.actors` and `…trafficlight.states`. It is no longer part of the `simula` module, so the `simula` module-info and JaCoCo/exec/spotless pom wiring for it were removed; the 97% framework-bundle coverage gate now covers the whole `simula` module without an examples exclusion. The classes below document that sample's data model; the package prefix `…` = `jpnco.simula.samples.trafficlight`.

Actors are implemented with the delegate pattern (`ActorDelegate.createDelegate(engine, this)`) and expose the three required methods (`getDelegate()`, `getId()`, `process(Event)`). They interact only by broadcasting events on the topics of `Topics`; no actor holds a reference to another. They are demonstration code, not part of the framework contract. See `sample-architecture.md` for the full architecture of the sample.

## Direction (enum, `states`)

A cardinal direction of travel on the grid: `NORTH`, `SOUTH`, `EAST`, `WEST`, each with a `(rowDelta, colDelta)`.

| Field | Type | Notes |
|-------|------|-------|
| `rowDelta` | int | Row change for one step (N/S = ∓1, E/W = 0) |
| `colDelta` | int | Column change for one step (E/W = ±1, N/S = 0) |

**Behavior**: `rowDelta()`/`colDelta()` give the displacement of one segment; `isVertical()` reports whether the direction uses the north-south band of a light; `turnRight()`/`turnLeft()` rotate by 90°.

## Topics (constants, `actors`)

Event-topic string constants shared by the actors.

| Topic | Publisher → Consumer | Payload |
|-------|----------------------|---------|
| `NEXT_TRAFFIC_LIGHT_STATES` | Coordinator → lights | tick |
| `NEXT_VEHICLES_STATES` | Coordinator → vehicles | tick, previous-tick light table |
| `TRAFFIC_LIGHT_STATE` | each light → coordinator | `TrafficLightState` |
| `VEHICLES_STATE` | each vehicle → coordinator | `VehicleState` |
| `NEW_STATE` | coordinator → displays | `GridState` |

## CrossingTrafficLight (autonomous Actor, ×12, `actors`)

An actor representing the traffic light of one intersection. Each crossing owns its own timing: green and orange durations and a phase offset are fixed at construction, so different intersections may have different periods.

| Field | Type | Notes |
|-------|------|-------|
| `id` | Integer | Light identity |
| `row`, `col` | int | Intersection coordinates |
| `greenDuration` | int | Green seconds, drawn in [20, 30] (deterministic) |
| `orangeDuration` | int | Orange seconds (3) |
| `halfCycle` / `cycle` | int | `green + orange` / `2 × halfCycle` |
| `phaseOffset` | int | Stagger across the grid |

**Behavior**: subscribes `NEXT_TRAFFIC_LIGHT_STATES`; on it, computes its band states for the tick and broadcasts `TRAFFIC_LIGHT_STATE` with a `TrafficLightState`.

## Vehicle (autonomous Actor, ×12, `actors`)

An autonomous actor representing one vehicle. It contains all of its own movement behavior (light respect and direction choice) and holds its own seeded randomness.

| Field | Type | Notes |
|-------|------|-------|
| `id` | Integer | Vehicle identity |
| `speed` | double | Fixed speed in m/s (chosen in [15, 45] km/h) |
| `random` | Random | Own seed (`RANDOM_SEED + id`) for turn decisions |
| `row`, `col` | int | Origin intersection of the current segment |
| `direction` | Direction | Current travel direction |
| `distanceInSegment` | double | Metres travelled into the current segment (0..SEGMENT_LENGTH) |

**Behavior**: subscribes `NEXT_VEHICLES_STATES`; on it, receives the previous tick's light table and advances itself by `speed × 1s`; it stops at `LIGHT_POSITION` when the light of the intersection it approaches is not green for its approach band, and waits for green; when it crosses it chooses its next direction (straight, right or left — never a U-turn). It broadcasts `VEHICLES_STATE` with a `VehicleState` carrying its new position and the cells it entered.

## TrafficCoordinator (orchestrator Actor, `actors`)

The actor that owns the fleet, the crossing counters and the light table, and assembles the snapshots.

| Field | Type | Notes |
|-------|------|-------|
| `lights` | List\<CrossingTrafficLight\> | 12 light actors |
| `vehicles` | List\<Vehicle\> | The fixed fleet (INITIAL_VEHICLES = 12) |
| `crossings` | int[][] | Per-cell crossing counts (owned by the coordinator) |
| `currentLights` | LightState[][][] | Light table being assembled for the tick |
| `lastLights` | volatile LightState[][][] | Previous completed tick's table, given to vehicles |
| `lightsReceived` / `vehiclesReceived` | int | Collection counters |
| `state` | volatile GridState | Latest immutable snapshot |
| `done` | CountDownLatch | Released once the run completes |
| `simTime` / `totalCrossings` | int | Current simulated time / final total |

**Behavior**: subscribes `TIME_EVENT`, `TRAFFIC_LIGHT_STATE`, `VEHICLES_STATE`. `seed()` creates and registers the 12 lights and 12 vehicles (deterministic seeds). On `TIME_EVENT` it resets its counters, resets the light table to all-green (corners stay green), and broadcasts `NEXT_TRAFFIC_LIGHT_STATES` and `NEXT_VEHICLES_STATES` (with `lastLights`). It collects the reports and, once all 12 lights and all 12 vehicles have reported, assembles a `GridState`, broadcasts `NEW_STATE`, and stops the engine (releasing `done`) once the configured `durationSeconds` is reached.

## GridState (immutable snapshot, `states`)

An immutable snapshot of the grid at one instant, broadcast on `NEW_STATE`, so displays can read it safely from another thread.

| Field | Type | Notes |
|-------|------|-------|
| `simTime` | int | Simulated time |
| `vehicles` | List\<VehicleView\> | Immutable vehicle views |
| `northSouth` / `eastWest` | LightState[][] | Per-intersection band states |
| `crossings` | int[][] | Per-cell crossing counts |

**Behavior**: exposes the vehicle list, per-cell light state (`lightState(row, col, vertical)`) and crossing counts.

## VehicleView (immutable, `states`)

An immutable view of one vehicle's position and direction at an instant: `id`, `row`, `col`, `direction`, `distanceInSegment`.

## VehicleState (immutable report, `states`)

A vehicle's report broadcast on `VEHICLES_STATE`: `tick`, `id`, `row`, `col`, `direction`, `distanceInSegment`, and the list of cells entered during the tick (for the crossing counters).

## TrafficLightState (immutable report, `states`)

A light's report broadcast on `TRAFFIC_LIGHT_STATE`: `tick`, `row`, `col`, `ns`, `ew`.

## LightState (enum, `states`)

`GREEN`, `ORANGE`, `RED`.

## TrafficMonitor (console display Actor, `actors`)

Subscribes `NEW_STATE` and prints the grid to `System.out`: one cell per intersection, where the light marker is `|` when north-south is green and `-` when east-west is green, with the number of vehicles occupying the cell.

## TrafficLightGui (Swing/Java2D display Actor, `actors`)

Subscribes `NEW_STATE`, stores the latest `GridState`, and a `Timer` on the Event Dispatch Thread repaints the panel. Each cell draws its two road bands (north-south and east-west) and a short green segment (about `GREEN_SEGMENT_METERS` = 20 m) on the band whose traffic light is green; every vehicle is drawn as a dot at its current position along its segment. The status line shows the simulated time, the vehicle count and the total crossings so far.

## TrafficLightDemo (entry point, root package)

Parses CLI args (`classic` → `ExecutionMode.PLATFORM`, `gui` → Swing), builds the root engine, creates and seeds the coordinator, creates the display actor (monitor or GUI), starts the engine, awaits completion and prints the outcome (`vehicles=…, crossings=…`). Not an actor.
