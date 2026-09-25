package jpnco.simula.engine;

import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;

import jpnco.simula.Actor;
import jpnco.simula.Engine;
import jpnco.simula.Event;
import jpnco.simula.actors.Logger;

/**
 * This class implements the "standard" behavior of an actor and is intended to
 * act as a delegate. So an implementation of an actor can only provide specific
 * behavior.
 *
 * @author Jean-Pascal Cozic
 *
 */
public final class ActorDelegate implements Actor {

	private static final int TIMEOUT = 30;

	/**
	 * Creates a delegate backed by a delay queue, so that delayed events are
	 * processed in order of their remaining delay.
	 *
	 * @param engine     the engine that runs the delegating actor
	 * @param delegator  the actor that delegates its behavior
	 * @return the created delayed delegate
	 */
	public static Actor createDelayedDelegate(final Engine engine, final Actor delegator) {
		return new ActorDelegate(engine, delegator, new DelayQueue<Event>());
	}

	/**
	 * Creates a standard delegate backed by a blocking queue, processing events
	 * in order of arrival.
	 *
	 * @param engine     the engine that runs the delegating actor
	 * @param delegator  the actor that delegates its behavior
	 * @return the created delegate
	 */
	public static Actor createDelegate(final Engine engine, final Actor delegator) {
		return new ActorDelegate(engine, delegator, new LinkedBlockingQueue<>());
	}

	/**
	 * Creates a delegate backed by a priority queue, so that prioritized events
	 * are processed in order of their priority.
	 *
	 * @param engine     the engine that runs the delegating actor
	 * @param delegator  the actor that delegates its behavior
	 * @return the created prioritized delegate
	 */
	public static Actor createPrioritizedDelegate(final Engine engine, final Actor delegator) {
		return new ActorDelegate(engine, delegator, new PriorityBlockingQueue<Event>());
	}

	private final Actor delegator;

	private final BlockingQueue<Event> events;

	private final Engine engine;

	/**
	 * Builds a delegate for the given actor on the given engine, using the
	 * supplied event queue.
	 *
	 * @param engine     the engine that runs the delegating actor
	 * @param delegator  the actor that delegates its behavior
	 * @param queue      the queue used to store pending events
	 */
	private ActorDelegate(final Engine engine, final Actor delegator, final BlockingQueue<Event> queue) {
		Objects.requireNonNull(engine);
		Objects.requireNonNull(delegator);
		events = queue;
		this.engine = engine;
		this.delegator = delegator;
		Thread.currentThread().setName(delegator.getSimpleName());
	}

	/**
	 * Returns whether this delegate is equal to the given object. Two delegates
	 * are equal when they have the same delegating actor and engine.
	 *
	 * @param obj the object to compare with this delegate
	 * @return {@code true} if the object is an equal delegate
	 */
	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final ActorDelegate other = (ActorDelegate) obj;
		return Objects.equals(delegator, other.delegator) && Objects.equals(engine, other.engine);
	}

	/**
	 * A delegate has no delegate of its own.
	 *
	 * @return always throws {@link UnsupportedOperationException}
	 */
	@Override
	public Actor getDelegate() {
		Logger.error(delegator, "Delegate has no delegate - delegator is %s\n", delegator.getName());
		throw new UnsupportedOperationException();
	}

	/**
	 * Returns the engine that runs the delegating actor.
	 *
	 * @return the engine of this delegate
	 */
	@Override
	public Engine getEngine() {
		return engine;
	}

	/**
	 * A delegate has no id of its own.
	 *
	 * @return always throws {@link UnsupportedOperationException}
	 */
	@Override
	public Integer getId() {
		Logger.error(delegator, "No id for a simple actor - delegator is %s\n", delegator.getName());
		throw new UnsupportedOperationException();
	}

	/**
	 * Returns the event queue of this delegate.
	 *
	 * @return the queue of pending events
	 */
	public BlockingQueue<Event> getQueue() {
		return events;
	}

	/**
	 * Returns a hash code for this delegate based on its delegating actor and
	 * engine, consistent with {@link #equals(Object)}.
	 *
	 * @return the hash code of this delegate
	 */
	@Override
	public int hashCode() {
		return Objects.hash(delegator, engine);
	}

	/**
	 * Offers the event to this delegate's queue, yielding until the event is
	 * accepted.
	 *
	 * @param event the event to post
	 */
	@Override
	public void post(final Event event) {
		Objects.requireNonNull(event);
		while (!events.offer(event)) {
			// System.out.printf("%s No room in queue\n", getName());
			Thread.yield();
		}
	}

	/**
	 * A delegate does not process events itself; the delegating actor does.
	 *
	 * @param event the event to process
	 */
	@Override
	public void process(final Event event) {
		Logger.error(this, "No event to process for a simple actor");
		throw new UnsupportedOperationException();
	}

	/**
	 * Clears the queue of pending events of this delegate.
	 */
	@Override
	public void purgeEvents() {
		try {
			events.clear();
		} catch (final UnsupportedOperationException exc) {
			System.out.println("============================ ERROR ============================");
			System.out.println("============================ ERROR ============================");
			System.out.println("============================ ERROR ============================");
		}
	}

	/**
	 * Runs the standard actor loop: subscribes the delegating actor to the START,
	 * STOP and STOP_ME events, then processes events until a stop is requested. A
	 * {@link Logger} actor starts its main loop as soon as possible.
	 */
	@Override
	public void run() {
		try {
			Logger.debug(this, "Running delegate for %s\n", delegator.getName());
			delegator.subscribe(Engine.START_EVENT);
			delegator.subscribe(Engine.STOP_EVENT);
			delegator.subscribe(Engine.STOP_ME_EVENT);
			if (!(delegator instanceof Logger)) {
				if (!runBeforeStart()) {
					runAfterStart();
				}
			} else {
				// Logger starts as soon as possible
				runAfterStart();
			}
		} catch (final Throwable exc) {
			exc.printStackTrace();
			Logger.error(delegator, "Stopping actor %s %s because of %s(message=%s)\n",
					delegator.getClass().getSimpleName(), delegator.getName(), exc.getClass().getSimpleName(),
					exc.getMessage());
			engine.signal(EventImpl.createEvent(Engine.STOPPED_ACTOR_EVENT, delegator));
			Logger.debug(this, "is stopped\n");
		}
	}

	/**
	 * Processes events after the START event has been received, until a STOP or
	 * STOP_ME event is encountered, then signals that the actor is stopped.
	 */
	private void runAfterStart() {
		LOOP: while (true) {
			try {
				final Event event = events.poll(TIMEOUT, TimeUnit.SECONDS);
				if (event != null) {
					switch (event.getTopic()) {
					case Engine.STOP_EVENT:
						delegator.beforeStop();
						Logger.debug(delegator, "STOP requested by %s\n", event.getSource().getName(),
								event.getSource().getName());
						if (delegator instanceof Logger) {
							((Logger) delegator).purgeQueue(events);
						}
						break LOOP;
					case Engine.STOP_ME_EVENT:
						if (delegator == event.getSource()) {
							break LOOP;
						}
						break;
					default:
						delegator.process(event);
					}
				} else {
					// System.out.printf("RAS: %s is still alive\n", delegator.getName());
				}
			} catch (final InterruptedException e) {
				e.printStackTrace();
				Thread.currentThread().interrupt();
			}
		}
		Logger.trace(delegator, "RAS: Stopping actor %s\n", delegator.getSimpleName());
		engine.signal(EventImpl.createEvent(Engine.STOPPED_ACTOR_EVENT, delegator));
		Logger.debug(this, "is stopped\n");
	}

	/**
	 * Processes events until the START event is received or a stop is requested.
	 *
	 * @return {@code true} if a stop was requested before the START event,
	 *         {@code false} otherwise
	 */
	private boolean runBeforeStart() {
		boolean stop = false;
		LOOP: while (true) {
			try {
				final Event event = events.poll(TIMEOUT, TimeUnit.SECONDS);
				if (event != null) {
					switch (event.getTopic()) {
					case Engine.START_EVENT:
						Logger.trace(delegator, "Starting\n");
						delegator.afterStart();
						break LOOP;
					case Engine.STOP_EVENT:
						delegator.beforeStop();
						Logger.debug(delegator, "STOP requested by %s\n", event.getSource().getName(),
								event.getSource().getName());
						stop = true;
						break LOOP;
					case Engine.STOP_ME_EVENT:
						if (delegator == event.getSource()) {
							stop = true;
							break LOOP;
						}
						break;
					default:
						delegator.process(event);
					}
				} else {
					// System.out.printf("RBS: %s is still alive\n", delegator.getName());
				}
			} catch (final InterruptedException e) {
				e.printStackTrace();
				Thread.currentThread().interrupt();
			}
		}
		if (stop) {
			Logger.trace(delegator, "RBS: Stopping actor %s\n", delegator.getSimpleName());
			engine.signal(EventImpl.createEvent(Engine.STOPPED_ACTOR_EVENT, delegator));
			Logger.debug(this, "is stopped\n");
		}
		return stop;
	}

}
