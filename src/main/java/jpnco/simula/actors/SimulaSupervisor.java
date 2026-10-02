package jpnco.simula.actors;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import jpnco.simula.Actor;
import jpnco.simula.Engine;
import jpnco.simula.Event;
import jpnco.simula.engine.ActorDelegate;
import jpnco.simula.engine.IdBuilder;

/**
 * A SimulaSupervisor observes the lifecycle of the actors and engines in a simulation and exposes a
 * queryable state of the components it has observed (FR-002).
 *
 * <p>It subscribes to the per-actor startup event {@link Engine#STARTED_ACTOR_EVENT} and to the
 * stop events {@link Engine#STOPPED_ACTOR_EVENT} and {@link Engine#STOPPED_ENGINE_EVENT}. On each
 * notification it records the source component and its lifecycle status (FR-003, FR-004, FR-005). A
 * stop notification for a component not previously observed is recorded directly as {@link
 * Status#STOPPED} without error (FR-010). The recorded state always follows the sequence of
 * notifications received (FR-009).
 *
 * <p>The supervision actor is non-intrusive: it observes by subscription only and never posts to,
 * stops, or modifies the observed components (FR-008).
 *
 * @author Jean-Pascal Cozic
 */
public final class SimulaSupervisor implements Actor {

  /** Error message when the engine is null (FR-002). */
  private static final String INVALID_ENGINE = "engine must not be null";

  /** Error message when a listener throws during notification (FR-005). */
  private static final String LISTENER_FAILURE = "supervision listener failed: %s\n";

  /** The lifecycle status of an observed component (FR-006). */
  public enum Status {
    /** The component has started. */
    STARTED,
    /** The component has stopped. */
    STOPPED
  }

  private final Actor delegate;
  private final Integer id;
  private final Engine engine;

  /**
   * Records the observed status per component. A concurrent map because process() may be called by
   * any notifying thread (FR-007) while getStates() is iterated by observers (FR-006).
   */
  private final Map<Actor, Status> states = new ConcurrentHashMap<>();

  /**
   * The registered supervision listeners. A copy-on-write list because listeners may be added and
   * removed by any thread at any time, including from inside a notification, while notifications
   * iterate without a lock (FR-006).
   */
  private final CopyOnWriteArrayList<SupervisionListener> listeners = new CopyOnWriteArrayList<>();

  /**
   * Builds a supervision actor for the given engine, subscribing to the lifecycle events.
   *
   * @param engine the engine that runs this actor (FR-002)
   * @throws NullPointerException if the engine is null
   */
  public SimulaSupervisor(final Engine engine) {
    this.engine = Objects.requireNonNull(engine, INVALID_ENGINE);
    delegate = ActorDelegate.createDelegate(engine, this);
    id = IdBuilder.nextId();
    subscribe(Engine.STARTED_ACTOR_EVENT);
    subscribe(Engine.STOPPED_ACTOR_EVENT);
    subscribe(Engine.STOPPED_ENGINE_EVENT);
  }

  /**
   * Registers a supervision listener, notified synchronously on every recorded status transition
   * (FR-003). Safe from any thread, including from inside a notification (FR-006). Duplicate
   * detection is equals-based: registering the same listener again is a no-op (FR-001).
   *
   * @param listener the listener to register
   * @throws NullPointerException if the listener is null (FR-001)
   */
  public void addSupervisionListener(final SupervisionListener listener) {
    listeners.addIfAbsent(Objects.requireNonNull(listener));
  }

  /**
   * Unregisters a supervision listener, taking effect for every subsequent record; removing a
   * listener that is not registered is a silent no-op (FR-007). Safe from any thread, including
   * from inside a notification (FR-006); whether an in-flight notification still reaches a
   * just-removed listener is unspecified.
   *
   * @param listener the listener to unregister
   * @throws NullPointerException if the listener is null (FR-007)
   */
  public void removeSupervisionListener(final SupervisionListener listener) {
    listeners.remove(Objects.requireNonNull(listener));
  }

  /**
   * Returns whether this supervision actor is equal to the given object. Two supervision actors are
   * equal when they have the same id.
   *
   * @param obj the object to compare with this supervision actor
   * @return {@code true} if the object is a supervision actor with the same id
   */
  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    final SimulaSupervisor other = (SimulaSupervisor) obj;
    return Objects.equals(id, other.id);
  }

  /**
   * Returns the delegate that runs the standard event loop of this actor.
   *
   * @return the delegate of this supervision actor
   */
  @Override
  public Actor getDelegate() {
    return delegate;
  }

  /**
   * Returns the engine that runs this supervision actor.
   *
   * @return the engine of this supervision actor
   */
  @Override
  public Engine getEngine() {
    return engine;
  }

  /**
   * Returns the unique id of this supervision actor.
   *
   * @return the id of this supervision actor
   */
  @Override
  public Integer getId() {
    return id;
  }

  /**
   * Returns a read-only view of the observed components and their lifecycle status (FR-006).
   *
   * @return an unmodifiable map from each observed component to its status
   */
  public Map<Actor, Status> getStates() {
    return Collections.unmodifiableMap(states);
  }

  /**
   * Returns a hash code for this supervision actor based on its id, consistent with {@link
   * #equals(Object)}.
   *
   * @return the hash code of this supervision actor
   */
  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  /**
   * Processes a subscribed lifecycle event, recording the source component's status (FR-003,
   * FR-004, FR-005, FR-009, FR-010). A startup event marks the source as {@link Status#STARTED}; a
   * stop event marks it as {@link Status#STOPPED}. Any other event is ignored.
   *
   * @param event the event to process
   */
  @Override
  public void process(final Event event) {
    Objects.requireNonNull(event);
    final Actor source = event.getSource();
    switch (event.getTopic()) {
      case Engine.STARTED_ACTOR_EVENT:
        record(source, Status.STARTED);
        Logger.debug(this, "observed %s start\n", source.getName());
        break;
      case Engine.STOPPED_ACTOR_EVENT:
      case Engine.STOPPED_ENGINE_EVENT:
        record(source, Status.STOPPED);
        Logger.debug(this, "observed %s stop\n", source.getName());
        break;
      default:
        break;
    }
  }

  /**
   * Records the given status for the given component (FR-006, FR-009, FR-010) and, when the
   * recorded status actually changes, notifies the registered listeners after the update (FR-003,
   * FR-004).
   *
   * @param component the component to record
   * @param status the status to record
   */
  private void record(final Actor component, final Status status) {
    final Status previous = states.put(component, status);
    if (previous != status) {
      notifyListeners(component, previous, status);
    }
  }

  /**
   * Notifies every registered listener of a recorded transition (FR-003). No-op when no listener is
   * registered (FR-008). A listener that throws is isolated: its {@link RuntimeException} is caught
   * and logged, and the recording and the other listeners are unaffected (FR-005).
   *
   * @param component the component whose status changed
   * @param previous the status recorded before the change, {@code null} if never recorded
   * @param current the newly recorded status
   */
  private void notifyListeners(final Actor component, final Status previous, final Status current) {
    if (listeners.isEmpty()) {
      return;
    }
    for (final SupervisionListener listener : listeners) {
      try {
        listener.statusChanged(component, previous, current);
      } catch (final RuntimeException exception) {
        Logger.error(this, LISTENER_FAILURE, exception);
      }
    }
  }
}
