package jpnco.simula;

import jpnco.simula.actors.Logger;
import jpnco.simula.engine.EventImpl;

/**
 * An actor is a piece of independent behavior executed by a thread. Actors subscribes to topics and
 * process each event relative to this topic that it receive. To interact with an other actor, an
 * actor can only post an event to a given actor or signal events that are posted to all actors that
 * subscribed this event.
 *
 * <p>An actor is summarily a thread that processes events posted in its queue.
 *
 * <p>There are some SIMULA events that are defined in Engine interface.
 * <li>START_EVENT: an actor must wait this event to start its business behavior. Usually, this
 *     event is posted on the root engine that forwards it to its actors and its child engines.
 * <li>STARTED_ACTOR_EVENT: an actor signals this event (with itself as source) when it begins its
 *     behavior. The built-in actors, the engine and the time source emit it for themselves. An
 *     actor that does not use the standard delegation MAY emit it when its behavior starts, so that
 *     a supervision actor can observe it (FR-001, FR-007).
 * <li>STOP_EVENT: when receiving this event, an actor must stop its behavior.
 * <li>STOP_ME_EVENT: an actor posts this event to "kill" himself ;
 * <li>STOPPED_ACTOR_EVENT: when an actor stops it must post this event to signal to its engine to
 *     unregister it.
 * <li>STOPPED_ENGINE_EVENT: when a engine stops, it must post this event to signal to its parent
 *     engine to unregister it.
 * <li>TIME_EVENT: this event is signaled each simulated second. <br>
 *     <br>
 *
 *     <p>A lot of methods are defaulted based on a delegation pattern. So to share the common
 *     behavior, an actor implementation has just to provide three methods:
 * <li>getDelegate()
 * <li>getId()
 * <li>process(Event event) <br>
 *     <br>
 *
 * @author Jean-Pascal Cozic
 */
public interface Actor extends Runnable, Comparable<Actor> {

  final Integer INVALID_ID = -1;

  /**
   * This method must be called during the START event process by a delegate actor. It allows to
   * customize starting process for a peculiar actor.
   */
  default void afterStart() {}

  /**
   * This method must be called during the STOP event process by a delegate actor. It allows to
   * customize stopping process for a peculiar actor.
   */
  default void beforeStop() {}

  /**
   * Compares this actor with another actor based on their ids in descending order. This method is
   * used to order actors, for example in a sorted set, so that actors with the highest id come
   * first.
   *
   * @param o the actor to be compared
   * @return a negative integer, zero, or a positive integer as this actor is greater than, equal
   *     to, or less than the specified actor
   */
  @Override
  default int compareTo(final Actor o) {
    return -getId().compareTo(o.getId());
  }

  /**
   * Returns the delegate of this actor. May be null, if this actor has no delegate.
   *
   * @return the delegate of this actor
   */
  Actor getDelegate();

  /**
   * Returns the engine that run this actor
   *
   * @return the engine that run this actor
   */
  default Engine getEngine() {
    return getDelegate().getEngine();
  }

  /**
   * Returns the id of this actor
   *
   * @return the id of this actor
   */
  Integer getId();

  /**
   * Returns the name of this actor.
   *
   * @return the name of this actor.
   */
  default String getName() {
    return String.format("%s:%s", getClass().getSimpleName(), getEngine().getName());
  }

  /**
   * Returns the simple name of this actor.
   *
   * @return the simple name of this actor.
   */
  default String getSimpleName() {
    return getClass().getSimpleName();
  }

  /**
   * Returns whether this actor has reached its terminal stopped state. Stopping is terminal: an
   * actor that completed its run loop can never run again, and its engine refuses to register it
   * again (FR-004). Actors using the standard delegation automatically report the state of their
   * delegate. Custom delegations SHOULD override this method (directly or through their delegator).
   * An override MUST return {@code true} only once the run loop of the actor has terminated, and
   * MUST keep returning {@code true} afterwards (the state is terminal). When a custom delegation
   * does not override, the engine still refuses to re-register the instance through its weak memory
   * of the instances it unregistered after a stop, so the no-restart guarantee holds for every
   * actor style (FR-005).
   *
   * @return {@code true} if this actor's run loop has completed its stop, {@code false} otherwise
   */
  default boolean isStopped() {
    final Actor delegate = getDelegate();
    return delegate != null && delegate.isStopped();
  }

  /**
   * Post an event in the queue of this actor
   *
   * @param event the event to post
   */
  default void post(final Event event) {
    getDelegate().post(event);
  }

  /**
   * Process a subscribed event
   *
   * @param event the event to process
   */
  void process(Event event);

  /** Purge events without process those events */
  default void purgeEvents() {
    getDelegate().purgeEvents();
  }

  /**
   * Runs this actor by delegating to its delegate. If the delegate throws a {@link Throwable}, the
   * actor is unregistered from its engine before the error is rethrown.
   */
  @Override
  default void run() {
    Logger.debug(this, "Running actor by delegation\n");
    try {
      getDelegate().run();
    } catch (final Throwable exc) {
      System.out.printf(
          "%s is dead because of %s\n", getSimpleName(), exc.getClass().getCanonicalName());
      getEngine().unregister(this);
      Logger.error(this, "is dead because of %s\n", exc.getClass().getCanonicalName());
      throw exc;
    }
  }

  /** Stops myself. This method is called by an actor to stop itself. */
  default void stopMe() {
    getEngine().signal(EventImpl.createEvent(Engine.STOP_ME_EVENT, this, getEngine().getTime()));
  }

  /**
   * Subscribes to a given topic. The actor will receive all events relative to this topic.
   *
   * @param topic the topic to subscribe
   */
  default void subscribe(final String topic) {
    getEngine().subscribe(this, topic);
  }
}
