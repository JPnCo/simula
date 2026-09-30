package jpnco.simula;

import java.util.List;

import jpnco.simula.actors.Logger;
import jpnco.simula.actors.TimeSource;

/**
 * An Engine instance pilots a set of actors and potentially a set of child
 * engines. There is one engine that has no parent: the root engine.
 * <p>
 * Because an engine is an actor, it can subscribe and process some SIMULA
 * events. Stopping an engine implies stopping all child engines. So stopping
 * the root engine implies to stop all the simulation.
 * <p>
 * A unique TimeSource
 * <p>
 * {@link #STARTED_ACTOR_EVENT} is a per-actor startup notification, symmetric
 * to {@link #STOPPED_ACTOR_EVENT}. It is emitted by a component when it begins
 * its behavior, with the component itself as the event's source. The built-in
 * actors (via standard delegation), the engine, and the time source emit it for
 * themselves. An actor created by an external project that does not use the
 * standard delegation MAY emit it as well, by signaling an event on this topic
 * with itself as source when its behavior starts; a supervision actor never
 * fails when a component emits no such notification (FR-001, FR-007).
 *
 * @author Jean-Pascal Cozic
 *
 */
public interface Engine extends Runnable, Actor {

	final String LOG_EVENT = "LOG";
	final String PURGE_QUEUE_EVENT = "PURGE_QUEUE";
	final String START_EVENT = "START";
	/**
	 * Per-actor startup notification, symmetric to {@link #STOPPED_ACTOR_EVENT}.
	 * Its source is the component that started (FR-001, FR-007).
	 */
	final String STARTED_ACTOR_EVENT = "STARTED_ACTOR";
	final String STOP_EVENT = "STOP";
	final String STOP_ME_EVENT = "STOP_ME";
	final String STOPPED_ACTOR_EVENT = "STOPPED_ACTOR";
	final String STOPPED_ENGINE_EVENT = "STOPPED_ENGINE";
	final String TIME_EVENT = "TIME";
	final String REQUEST_ALARM_EVENT = "REQUEST_ALARM";
	final String CLEAR_ALARM_EVENT = "CLEAR_ALARM";

	/**
	 * Adds a child engine.
	 *
	 * @param child the child to add
	 */
	void addChild(Engine child);

	/**
	 * Returns the logger of this engine
	 *
	 * @return the logger of this engine
	 */
	Logger getLogger();

	/**
	 * Returns the parent of this engine. May be null if this engine is the root
	 * engine.
	 *
	 * @return the parent of this engine.
	 */
	Engine getParent();

	/**
	 * Returns the child engines of this engine as a non-modifiable, ordered
	 * collection, in the order in which they were added (FR-011).
	 *
	 * @return the child engines of this engine
	 */
	List<Engine> getChildren();

	/**
	 * Returns the actors of this engine as a non-modifiable, ordered collection
	 * (FR-012).
	 *
	 * @return the actors of this engine
	 */
	List<Actor> getActors();

	/**
	 * Returns current time
	 *
	 * @return current time
	 */
	int getTime();

	/**
	 * Returns the time factor of this engine. If this engine has a parent returns
	 * the timeFactor of the parent.
	 *
	 * @return the time factor of this engine.
	 */
	int getTimeFactor();

	/**
	 * Returns the time source of this engine
	 *
	 * @return the time source of this engine
	 */
	TimeSource getTimeSource();

	/**
	 * Register and starts an actor.
	 * <p>
	 * Registration is refused when the actor instance has already stopped on this engine
	 * (stopping is terminal: create a new instance to run the behavior again) or when an
	 * actor with the same id is currently registered (the running actor is left
	 * undisturbed). A refusal is reported as an engine error log and leaves the engine
	 * state unchanged: the actor is not registered, not subscribed, and no execution is
	 * started.
	 *
	 * @param actor the actor to register and start;
	 * @Return this actor
	 * @throws IllegalArgumentException if the actor is already stopped on this engine or is
	 *                                  currently registered (FR-001, FR-002, FR-003, FR-008)
	 */
	Actor registerAndStart(Actor actor);

	/**
	 * Signals an event. all registered actor will receive this event if they
	 * subscribed it
	 *
	 * @param event the event to signal
	 */
	void signal(Event event);

	/**
	 * Signals an event to the child engines.
	 *
	 * @param event the event to signal.
	 */
	void signalToChildren(Event event);

	/**
	 * Starts this engine. Starts children engines if any.
	 */
	void start();

	/**
	 * Stops this engine. Be careful, if there are a lot of actors and/or a lot of
	 * child engines, stopping must be quite long and some actors can continue to
	 * work during stopping.
	 */
	void stop();

	/**
	 * Subscribes a topic for a given actor
	 *
	 * @param actor the actor that must subscribe the topic
	 * @param topic the topic to subscribe
	 */
	void subscribe(Actor actor, String topic);

	/**
	 * Unregister an actor. Returns true, it there no loner any registered actor
	 *
	 * @param actor the actor to unregister.
	 * @Return true, it there no loner any registered actor.
	 *
	 */
	boolean unregister(Actor actor);

	/**
	 * Unsubscribes a topic for a given actor
	 *
	 * @param actor the actor that must unsubscribe the topic
	 * @param topic the topic to unsubscribe
	 */
	void unsubscribe(Actor actor, String topic);

}
