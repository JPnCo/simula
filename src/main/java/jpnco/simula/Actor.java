package jpnco.simula;

import jpnco.simula.actors.Logger;
import jpnco.simula.engine.EventImpl;

/**
 * An actor is a piece of independent behavior (typically it is run by a
 * thread). Actors subscribes to topics and process each event relative to this
 * topic that it receive. To interact with an other actor, an actor can only
 * post events.
 * <p>
 * There are some SIMULA events that are defined in Engine interface.
 * <li>START_EVENT: an actor must wait this event to start its business
 * behavior. Usually, this event is posted on the root engine that forwards it
 * to its actors and its child engines.
 * <li>STOP_EVENT: when receiving this event, an actor must stop its behavior.
 * <li>STOP_ME_EVENT: an actor posts this event to "kill" himself ;
 * <li>STOPPED_ACTOR_EVENT: when an actor stops it must post this event to
 * signal to its engine to unregister it.
 * <li>STOPPED_ENGINE_EVENT: when a engine stops, it must post this event to
 * signal to its parent engine to unregister it.
 * <li>TIME_EVENT: this event is signaled each simulated second. <br>
 * <br>
 * <p>
 * A lot of methods are defaulted based on a delegation pattern. So to share the
 * common behavior, an actor implementation has just to provide the three
 * methods:
 * <li>getDelegate()
 * <li>getId()
 * <li>process(Event event) <br>
 * <br>
 * The simula.engine.SimpleActor is designed to implements the delegate pattern
 * for an actor.<br>
 * <br>
 *
 * @author Jean-Pascal Cozic
 *
 */
public interface Actor extends Runnable, Comparable<Actor> {

	final Integer INVALID_ID = -1;

	/**
	 * This method must be called during the START event is process by a delegate
	 * actor.
	 */
	default void afterStart() {
	}

	/**
	 * This method must be called during the STOP event is process by a delegate
	 * actor.
	 */
	default void beforeStop() {
	}

	@Override
	default int compareTo(final Actor o) {
		return -getId().compareTo(o.getId());
	}

	/**
	 * Returns the delegate of this actor. May be null, if this actor has no
	 * delegate.
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
		return String.format("%s", getClass().getSimpleName());
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

	/**
	 * Purge events without process those events
	 */
	default void purgeEvents() {
		getDelegate().purgeEvents();
	}

	@Override
	default void run() {
		Logger.debug(this, "Running actor by delegation\n");
		try {
			getDelegate().run();
		} catch (final Throwable exc) {
			System.out.printf("%s is dead because of %s\n", getSimpleName(), exc.getClass().getCanonicalName());
			getEngine().unregister(this);
			Logger.error(this, "is dead because of %s\n", exc.getClass().getCanonicalName());
			throw exc;
		}
	}

	/**
	 * Stops this actor
	 */
	default void stopMe() {
		getEngine().signal(EventImpl.createEvent(Engine.STOP_ME_EVENT, this, getEngine().getTime()));
	}

	/**
	 * Subscribes to a given topic. The actor will receive all events relative to
	 * this topic.
	 *
	 * @param topic the topic to subscribe
	 */
	default void subscribe(final String topic) {
		getEngine().subscribe(this, topic);
	}
}
