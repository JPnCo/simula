package jpnco.simula;

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
 *
 * @author Jean-Pascal Cozic
 *
 */
public interface Engine extends Runnable, Actor {

	final String LOG_EVENT = "LOG";
	final String PURGE_QUEUE_EVENT = "PURGE_QUEUE";
	final String START_EVENT = "START";
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
	 * Register and starts an actor
	 *
	 * @param actor the actor to register and start;
	 * @Return this actor
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
