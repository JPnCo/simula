# simula

A **discrete-event simulation framework** for Java 25 based on an actor model. It lets you describe a system as a set of autonomous actors (each running on its own thread) that communicate by subscribing to named topics and exchanging events, while a simulated clock advances time.

This document is the **user manual** for the framework.

## Table of contents

- [Prerequisites](#prerequisites)
- [Installation](#installation)
- [Concepts](#concepts)
- [Quick start](#quick-start)
- [Writing an actor](#writing-an-actor)
- [Events](#events)
- [Simulated time](#simulated-time)
- [Alarms](#alarms)
- [Built-in actors](#built-in-actors)
- [Logging](#logging)
- [Engine hierarchy](#engine-hierarchy)
- [Thread execution mode](#thread-execution-mode)
- [Best practices](#best-practices)
- [API reference](#api-reference)

## Prerequisites

- **Java 25** (the project is compiled with `maven.compiler.source/target = 25`). The virtual-thread mode requires Java 21+.
- **Maven 3.9+**

## Installation

Build and install the artifact into the local Maven repository:

```bash
mvn -o clean install
```

Offline environment: the test dependencies (`junit-jupiter-api` 5.14.0, `mockito` 5.22.0) must be present in `~/.m2`.

The artifact is declared as `fr.jpnco.simula:simula-core:0.0.1-SNAPSHOT`.

### Maven dependency

To use the framework in your own project, declare the dependency:

```xml
<dependency>
    <groupId>fr.jpnco.simula</groupId>
    <artifactId>simula-core</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

## Concepts

The framework rests on four central notions:

- **Engine**: the orchestrator. It registers and starts actors, manages topic subscriptions, owns a logger and (for the root engine) a unique time source.
- **Actor**: an independent unit of behavior executed by a thread. An actor subscribes to topics and processes the events it receives.
- **Event**: a fact signaled on a named topic at a given instant, with optional parameters. There are three kinds of events: standard, prioritized, and delayed.
- **TimeSource**: the unique clock of the simulation, present on the root engine. It advances simulated time and signals a time event each simulated second.

Actors communicate **only** through events: they post events to a target actor, or signal events that are delivered to every actor subscribed to the event's topic.

## Quick start

```java
import fr.jpnco.simula.Engine;
import fr.jpnco.simula.engine.EngineImpl;

Engine engine = new EngineImpl("my-simulation", 2);
engine.start();
// ... your simulation ...
engine.stop();
```

The `new EngineImpl(String title, int timeFactor)` constructor creates a **root engine** with the default execution mode (virtual threads). The simulation starts with `start()`, and stopping the root engine stops the whole simulation (cascade shutdown).

## Writing an actor

An actor is implemented by implementing the `Actor` interface. Thanks to the **delegation pattern**, you only need to provide three things:

1. `getDelegate()` — returns the standard delegate;
2. `getId()` — returns a unique identifier;
3. `process(Event event)` — processes received events.

```java
import fr.jpnco.simula.Actor;
import fr.jpnco.simula.Event;
import fr.jpnco.simula.actors.Logger;
import fr.jpnco.simula.engine.ActorDelegate;
import fr.jpnco.simula.engine.IdBuilder;

public class MyActor implements Actor {

    private final Actor delegate;
    private final Integer id;

    public MyActor() {
        delegate = ActorDelegate.createDelegate(myEngine, this);
        id = IdBuilder.nextId();
        subscribe("MY_TOPIC");
    }

    @Override
    public Actor getDelegate() {
        return delegate;
    }

    @Override
    public Integer getId() {
        return id;
    }

    @Override
    public void process(final Event event) {
        // Process the event here
        Logger.info(this, "Received %s at %d\n", event.getTopic(), event.getTime());
    }
}
```

Register and start the actor on the engine:

```java
MyActor actor = new MyActor();
engine.registerAndStart(actor);
```

### Lifecycle hooks

An actor can override two default methods:

- `afterStart()` — called at startup, when the actor receives the `START` event;
- `beforeStop()` — called at shutdown, when the actor receives the `STOP` event.

### Stopping an actor

An actor can stop itself by calling `stopMe()`.

Stopping is **terminal**: once an actor's run loop has completed, the engine unregisters it, its pending event queue is purged, and it can never run again. Re-registering the same instance (with `register`/`registerAndStart`) is refused with an `IllegalArgumentException` and an engine error log; the engine state is left untouched. To run the same behavior again, create a **new actor instance**. An actor reports its terminal state through `isStopped()` (`false` before the run, `true` once the run loop has completed, whatever the exit path).

## Events

### Signaling an event

To broadcast an event to all subscribers of its topic:

```java
import fr.jpnco.simula.Event;
import fr.jpnco.simula.engine.EventImpl;

Event event = EventImpl.createEvent("MY_TOPIC", sourceActor, "param1", 42);
engine.signal(event);
```

### Subscribing / unsubscribing

```java
actor.subscribe("MY_TOPIC");                // via the actor
engine.subscribe(actor, "MY_TOPIC");
engine.unsubscribe(actor, "MY_TOPIC");
```

Only actors subscribed to an event's topic receive and process that event.

### The three kinds of events

| Kind | Factory | Processing order |
|------|---------|------------------|
| Standard | `EventImpl.createEvent(topic, source, params...)` | Signaling order |
| Prioritized | `EventImpl.createPriorityEvent(topic, priority, source, params...)` | By priority (ascending) |
| Delayed | `EventImpl.createDelayedEvent(topic, delayMillis, source, params...)` | Reverse order of remaining delay |

Negative values are rejected: a prioritized event with a negative priority, or a delayed event with a negative delay, throws `UnsupportedOperationException`.

### Choosing the right delegate

The standard delegate of an actor can be chosen according to the kind of events to process:

- `ActorDelegate.createDelegate(engine, actor)` — standard queue (default);
- `ActorDelegate.createPrioritizedDelegate(engine, actor)` — priority queue;
- `ActorDelegate.createDelayedDelegate(engine, actor)` — delay queue.

## Simulated time

The unique time source (`TimeSource`) is created automatically on the root engine.

- **Current time**: `engine.getTime()` returns the number of simulated seconds since the start.
- **Time event**: each simulated second, an event with topic `Engine.TIME_EVENT` is signaled to all subscribers.
- **Time factor**: `engine.getTimeFactor()` gives the real duration of a simulated second. For example, a factor of `1` yields ~1 real second per simulated second; a larger factor stretches the real duration, useful for heavy simulations.

> Note: the time factor is a construction parameter (see [Thread execution mode](#thread-execution-mode)). In the current state of the code, it is wired to `0` by default in the common constructors.

## Alarms

The time source can schedule alarms. A **requesting** actor signals an `Engine.REQUEST_ALARM_EVENT` with the parameters:

1. the topic of the event to trigger;
2. the firing time (real time);
3. the period (optional) for a periodic alarm.

```java
// Alarms (conceptual posting example)
Event oneShot = EventImpl.createEvent(
    Engine.REQUEST_ALARM_EVENT, sourceActor,
    "ALARM_TOPIC", 10);                 // once, at t=10
Event periodic = EventImpl.createEvent(
    Engine.REQUEST_ALARM_EVENT, sourceActor,
    "ALARM_TOPIC", 10, 5);              // every 5 units
engine.signal(oneShot);
```

A **Poisson** alarm replaces the fixed period by a rate (mean firings per unit of simulated time): each firing re-arms after a random wait drawn from the exponential distribution of parameter `rate` — whole units, at least one. An optional seed makes the sequence reproducible:

```java
Event poisson = EventImpl.createEvent(
    Engine.REQUEST_ALARM_EVENT, sourceActor,
    "ALARM_TOPIC", 10, TimeSource.POISSON, 2.0);      // rate 2, unseeded
Event seeded = EventImpl.createEvent(
    Engine.REQUEST_ALARM_EVENT, sourceActor,
    "ALARM_TOPIC", 10, TimeSource.POISSON, 2.0, 42L); // reproducible
```

A Poisson alarm re-arms until cleared; a request with a non-positive rate or a malformed seed is refused with an error log and registers nothing.

The alarm fires by signaling an event on the associated topic. Until it fires, it can be **cancelled** via an `Engine.CLEAR_ALARM_EVENT` whose first parameter is the alarm's topic.

Constraints:

- only one alarm per topic at a time;
- a one-shot alarm is consumed after firing; a periodic alarm re-arms;
- times and periods are expressed in real time; do not apply the time factor to them.

## Built-in actors

The framework ships several ready-to-use actors in `fr.jpnco.simula.actors`.

### Logger

A logger is created automatically for each engine (accessible via `engine.getLogger()`). It subscribes to the `LOG` topic and prints messages to standard output. Use only the static methods:

```java
Logger.trace(source, "format %d\n", value);
Logger.debug(source, "format %s\n", value);
Logger.info(source, "message %s\n", value);
Logger.warning(source, "careful\n");
Logger.error(source, "error %s\n", value);
Logger.probe(source, "probe %d\n", value);
```

Levels (most verbose to least): `TRACE`, `DEBUG`, `WARNING`, `INFO`, `ERROR`, `PROBE`.

- `Logger.setActivated(actor, level, bool)` — enables/disables a level for a given actor.
- `Logger.forceLevel(level)` — forces a global level, overriding per-actor activation.
- `Logger.isActivated(actor, level)` — queries activation.

### TimeSource

The simulation clock, created on the root engine. It subscribes to the `START`/`STOP` events, advances time, and manages alarms. Accessible via `engine.getTimeSource()`.

### Barrier

A **synchronization** actor that coordinates a set of participants. Participants signal their readiness on a topic; once the count reaches the configured participant number, the barrier signals the "complete" topic.

```java
import fr.jpnco.simula.actors.Barrier;
import fr.jpnco.simula.actors.BarrierMode;

Barrier barrier = new Barrier(engine, 3, "READY", "ALL_READY", BarrierMode.SINGLE_USE, false);
engine.registerAndStart(barrier);
```

Constructor parameters:

- `engine` — the engine that runs the barrier;
- `participants` — the number of "ready" signals to reach (must be positive);
- `readyTopic` — the topic on which participants signal readiness (non-blank);
- `completeTopic` — the topic signaled when all are ready (non-blank);
- `mode` — behavior after firing: `SINGLE_USE` (fires once then unsubscribes) or `CYCLIC` (re-arms for subsequent cycles);
- `distinct` — if `true`, each distinct participant (identified by the event source) counts only once.

The constructor throws `IllegalArgumentException` if `participants` is non-positive, if a topic is blank/null, or if `mode` is null.

### SimulaSupervisor

A **read-only observer** of the simulation lifecycle. It subscribes to the actor-start and stop events of its engine and records the status (`STARTED`/`STOPPED`) of every component it observes, queryable at any time via `getStates()`.

```java
import fr.jpnco.simula.actors.SimulaSupervisor;

SimulaSupervisor supervisor = new SimulaSupervisor(engine);
engine.registerAndStart(supervisor);
// ...
supervisor.getStates(); // { actorA=STARTED, actorB=STOPPED, ... }
```

Listeners can be attached to be pushed every status transition instead of polling:

```java
supervisor.addSupervisionListener(
    (component, previous, current) ->
        System.out.println(component.getName() + ": " + previous + " -> " + current));
```

A supervisor created **after** some actors already started still sees them: at construction it seeds all actors already registered on its engine as `STARTED`. Supervision is **per engine**: a supervisor observes its own engine's actors and its direct child engines (start and stop), never the actors registered on child engines — create one supervisor per engine for full coverage.

A listener is invoked synchronously in the thread that recorded the transition (which may be any thread), once per actual change, after the new status is visible in `getStates()`. Implementations must be thread-safe and should not block; a throwing listener is isolated and logged, never affecting the recorded state or the other listeners. `removeSupervisionListener` detaches a listener; `null` is rejected with `NullPointerException` and double registration is a no-op.

## Logging

See [Built-in actors → Logger](#logger). The root engine's logger captures the activity of all actors and prints it to standard output, filtered by the active levels.

## Engine hierarchy

An engine can have **child engines**. The root engine has no parent. Stopping an engine stops all its children; stopping the root engine therefore stops the whole simulation.

```java
Engine parent = new EngineImpl("parent", 2);
Engine child = new EngineImpl("child", parent); // parent.addChild is called
parent.start();
parent.stop(); // stops parent and child
```

The `START`, `STOP`, and `TIME` events propagate from the parent engine to its children (`signalToChildren`). The time source is unique: child engines read time from their parent (`getTime()` walks up the hierarchy).

## Thread execution mode

The execution mode determines the type of thread used to run actors.

| Mode | Threads | Usage |
|------|---------|-------|
| `ExecutionMode.VIRTUAL` (default) | Virtual threads | Many actors, low per-actor cost |
| `ExecutionMode.PLATFORM` | Classic platform threads | Compatibility / deterministic behavior |

Choose the mode at construction:

```java
import fr.jpnco.simula.engine.ExecutionMode;

// Virtual mode (default)
Engine v = new EngineImpl("virtual", 2);

// Classic mode
Engine p = new EngineImpl("classic", 2, ExecutionMode.PLATFORM);
```

A mode can also be resolved by name, case-insensitively: `ExecutionMode.fromName("platform")` → `PLATFORM`. A null or unknown name throws `IllegalArgumentException`.

> Virtual mode requires Java 21+. On an older runtime, the framework raises a clear error rather than silently degrading.

> **Never use `synchronized` with virtual threads.** A `synchronized` block pins its virtual thread to its carrier thread for the whole duration of the lock hold, cancelling the scalability benefit of virtual threads and potentially blocking every other actor sharing that carrier. Use `java.util.concurrent.locks.ReentrantLock` (or other `java.util.concurrent` synchronizers) to guard shared state in your actors.

## Best practices

- **Always call `start()`** on the root engine to trigger the `START` events.
- **Stop cleanly** with `stop()` on the root engine for a cascade shutdown.
- **Register every actor** with `registerAndStart()`; the engine starts it on a thread suited to the execution mode. Registering **after** `start()` is supported: the engine posts `START` to the new actor at registration, so it starts and is supervised like the others.
- **Subscribe actors to the topics** they must receive; only use `signal()` for topics the actor has subscribed to.
- **Never assume a thread is exclusive**: actors run in parallel; the engine's shared state is guarded by reentrant locks.
- **Never use `synchronized`** in your actors: with virtual threads it pins the carrier thread and destroys scalability; guard shared state with `java.util.concurrent.locks.ReentrantLock` instead.
- **Never re-register a stopped actor**: stopping is terminal; create a new instance to run the behavior again (a re-registration attempt raises `IllegalArgumentException`).

## API reference

The public interfaces live in the `fr.jpnco.simula` package:

- `Actor` — `getId()`, `getEngine()`, `getDelegate()`, `getName()`, `getSimpleName()`, `isStopped()`, `process(Event)`, `post(Event)`, `subscribe(String)`, `stopMe()`, `purgeEvents()`, `run()`, `afterStart()`, `beforeStop()`, `compareTo(Actor)`.
- `Engine extends Actor` — `start()`, `stop()`, `registerAndStart(Actor)`, `signal(Event)`, `signalToChildren(Event)`, `subscribe(Actor, String)`, `unsubscribe(Actor, String)`, `unregister(Actor)`, `addChild(Engine)`, `getParent()`, `getTime()`, `getTimeFactor()`, `getTimeSource()`, `getLogger()`.
- `Event` — `getTopic()`, `getSource()`, `getTime()`, `getParameters()`, `getPriority()`, `isPrioritized()`, `isDelayed()`, `duplicate(Actor)`.

The predefined topic constants are exposed on `Engine`: `START_EVENT`, `STOP_EVENT`, `STOP_ME_EVENT`, `STOPPED_ACTOR_EVENT`, `STOPPED_ENGINE_EVENT`, `TIME_EVENT`, `LOG_EVENT`, `PURGE_QUEUE_EVENT`, `REQUEST_ALARM_EVENT`, `CLEAR_ALARM_EVENT`.

Implementations and factories live in `fr.jpnco.simula.engine` (`EngineImpl`, `EventImpl`, `ActorDelegate`, `ExecutionMode`, `IdBuilder`), and the built-in actors in `fr.jpnco.simula.actors` (`Logger`, `TimeSource`, `Barrier`, `BarrierMode`, `SimulaSupervisor` with its `Status` enum, `SupervisionListener`). `SimulaSupervisor` adds `getStates()`, `addSupervisionListener(SupervisionListener)` and `removeSupervisionListener(SupervisionListener)`.

---

For internal design (components, data model, lifecycle, concurrency), see [`architecture.md`](architecture.md).
