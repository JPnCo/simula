# Contract: Supervision Actor

**Branch**: `004-supervision-actor` | **Date**: 2026-09-29 | **Spec**: [spec.md](../spec.md) | **Data model**: [data-model.md](../data-model.md)

Phase 1 output of `/speckit.plan`.

## Purpose

A `SimulaSupervisor` observes the lifecycle of the actors and engines in a simulation and exposes a queryable state of what has started and stopped. It is a standard framework actor in `jpnco.simula.actors`, created by a developer with an engine reference and registered/started through the engine (like `Logger` and `Barrier`).

## Construction

- A supervision actor is constructed with a reference to the engine that runs it.
- Invalid construction (e.g., a null engine) is rejected with a clear error.

## Behavior

- On construction it subscribes to the lifecycle events:
  - `STARTED_ACTOR_EVENT` (per-actor startup);
  - `STOPPED_ACTOR_EVENT` (actor stop);
  - `STOPPED_ENGINE_EVENT` (child-engine stop).
- On each lifecycle notification it updates its internal state for the event's source component:
  - a startup notification marks the source as `STARTED`;
  - a stop notification marks the source as `STOPPED` (creating it as `STOPPED` if not already present).
- It logs the lifecycle events it observes via the framework logging facilities.
- It never posts to, stops, or otherwise modifies the observed components (non-intrusive).

## Public state access

- The supervision actor exposes the queryable state of observed components: for each observed component, its lifecycle status (`STARTED` or `STOPPED`).
- The exposed state is read-only from the caller's perspective.

## New lifecycle event

- A new event constant `STARTED_ACTOR_EVENT` is added to the `Engine` interface, symmetric to the existing `STOPPED_ACTOR_EVENT`.

## Emission contract (external actors)

Actors that do **not** use the framework's standard delegation MAY emit the startup notification themselves so they can be observed. To do so, when the actor begins its behavior, it signals an event on `STARTED_ACTOR_EVENT` whose source is itself. The same is documented for the stop notification (`STOPPED_ACTOR_EVENT`). This contract is advisory: a supervision actor never fails if a component does not emit a notification; it only records the events actually received.
