# SIMULA Architecture

This document describes the current architecture of the simula discrete-event simulation framework. It reflects the system as implemented today and contains no historical information.

## Overview

SIMULA is a Java 23 library that provides an actor-based discrete-event simulation framework. A root **Engine** orchestrates actors and child engines. Each actor subscribes to named topics and processes the events posted to those topics. A single **TimeSource** advances simulated time and signals a time event each simulated second. Events may be standard, prioritized, or delayed.

Actors execute on threads; the engine selects the thread strategy (virtual by default, or classic platform threads) via an `ExecutionMode` configuration. Shared engine state is guarded by explicit reentrant locks.

## Component Model

```mermaid
classDiagram
    class Engine {
        <<interface>>
        +addChild(Engine)
        +getLogger()
        +getTime()
        +getTimeFactor()
        +getTimeSource()
        +registerAndStart(Actor)
        +signal(Event)
        +signalToChildren(Event)
        +start()
        +stop()
        +subscribe(Actor, String)
        +unsubscribe(Actor, String)
        +unregister(Actor) boolean
    }
    class Actor {
        <<interface>>
        +getId() Integer
        +getName() String
        +getEngine() Engine
        +post(Event)
        +process(Event)
        +run()
        +subscribe(String)
        +stopMe()
    }
    class Event {
        <<interface>>
        +getTopic() String
        +getSource() Actor
        +getTime() int
        +getParameters() Object[]
        +getPriority() int
        +isDelayed() boolean
        +isPrioritized() boolean
    }
    class EngineImpl {
        -actors Map~Integer, Actor~
        -children Set~Engine~
        -subscribersByTopic Map~String, Set~Actor~~
        -lock ReentrantLock
    }
    class ExecutionMode {
        <<enum>>
        VIRTUAL
        PLATFORM
    }
    class TimeSource {
        -currentTime int
        -alarms Map~String, Alarm~
    }
    class Barrier {
        -participants int
        -readyTopic String
        -completeTopic String
        -mode BarrierMode
        -distinct boolean
        -count int
    }
    class BarrierMode {
        <<enum>>
        SINGLE_USE
        CYCLIC
    }
    class Logger
    class ActorDelegate
    class IdBuilder

    Engine <|.. EngineImpl
    EngineImpl *-- ExecutionMode : executionMode
    EngineImpl *-- TimeSource : root
    EngineImpl *-- Logger
    EngineImpl o-- Actor : registers
    EngineImpl o-- Engine : children
    Actor <|.. EngineImpl
    Actor <|.. TimeSource
    Actor <|.. Logger
    Actor <|.. Barrier
    Actor <|.. ActorDelegate
    Barrier *-- BarrierMode : mode
    Engine <|.. TimeSource
    ActorDelegate o-- Event : processes
    IdBuilder ..> EngineImpl : nextId
    IdBuilder ..> TimeSource : nextId
    IdBuilder ..> Barrier : nextId
```

## Data Model

```mermaid
classDiagram
    class Engine {
        id : Integer
        name : String
        executionMode : ExecutionMode
        timeFactor : int
    }
    class Actor {
        id : Integer
        name : String
    }
    class Event {
        topic : String
        source : Actor
        time : int
        parameters : Object[]
        priority : int
        delay : long
    }
    class TimeSource {
        currentTime : int
    }
    class Alarm {
        topic : String
        timeToFire : int
        period : int
    }
    class ExecutionMode {
        VIRTUAL
        PLATFORM
    }
    Engine "1" *-- "1" TimeSource : root owns
    Engine "1" *-- "0..*" Actor : registers
    Engine "1" o-- "0..*" Engine : children
    Engine "1" *-- "1" ExecutionMode : mode
    TimeSource "1" *-- "0..*" Alarm : schedules
    Actor "1" ..> "0..*" Event : posts/processes
```

## Lifecycle

```mermaid
sequenceDiagram
    participant Dev as Developer
    participant E as Engine
    participant TS as TimeSource
    participant A as Actor
    Dev->>E: new Engine(name, mode)
    E->>E: register logger, time source
    E->>A: start (registerAndStart)
    E->>A: thread start (virtual or classic)
    Dev->>E: start()
    E->>A: START event
    A->>A: afterStart()
    loop each simulated second
        TS->>TS: advance time
        TS->>E: TIME event
        E->>A: TIME event
    end
    Dev->>E: stop()
    E->>A: STOP event
    A->>A: beforeStop()
    A->>E: STOPPED_ACTOR
    E->>E: unregister actor
    E->>Dev: engine stopped
```

## Concurrency

Shared engine state (registered actors, child engines, and topic subscriptions) is guarded by a per-engine `ReentrantLock`. The id builder uses a static `ReentrantLock` to produce unique ids across threads. Event delivery is asynchronous through per-actor queues (standard, priority, or delay).

```mermaid
flowchart LR
    Engine[Engine] -->|ReentrantLock| State[Shared State]
    State --> Actors[Actors]
    State --> Children[Children]
    State --> Subs[Subscribers]
    IdBuilder[IdBuilder] -->|static ReentrantLock| Ids[Unique Ids]
```

## Key Decisions

- **Execution mode**: actors run on virtual threads by default; classic platform threads are selected explicitly via an `ExecutionMode` constructor parameter (FR-001..FR-004).
- **Locking**: all `synchronized` monitors are replaced with explicit `ReentrantLock` for uniform, explicit concurrency semantics (FR-010..FR-012).
- **Unique ids**: a central `IdBuilder` assigns unique integer ids to engines, actors, and loggers.

## Requirements Traceability

- ExecutionMode: FR-001, FR-002, FR-003, FR-007
- Engine thread strategy: FR-004, FR-006, FR-008
- Behavioral equivalence: FR-005, FR-013
- Concurrency/locking: FR-010, FR-011, FR-012
