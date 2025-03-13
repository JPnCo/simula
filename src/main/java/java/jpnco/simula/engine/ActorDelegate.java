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

	public static Actor createDelayedDelegate(final Engine engine, final Actor delegator) {
		return new ActorDelegate(engine, delegator, new DelayQueue<Event>());
	}

	public static Actor createDelegate(final Engine engine, final Actor delegator) {
		return new ActorDelegate(engine, delegator, new LinkedBlockingQueue<>());
	}

	public static Actor createPrioritizedDelegate(final Engine engine, final Actor delegator) {
		return new ActorDelegate(engine, delegator, new PriorityBlockingQueue<Event>());
	}

	private final Actor delegator;
	private final BlockingQueue<Event> events;
	private final Engine engine;

	private ActorDelegate(final Engine engine, final Actor delegator, final BlockingQueue<Event> queue) {
		Objects.requireNonNull(engine);
		Objects.requireNonNull(delegator);
		events = queue;
		this.engine = engine;
		this.delegator = delegator;
		Thread.currentThread().setName(delegator.getSimpleName());
	}

	@Override
	public Actor getDelegate() {
		Logger.error(delegator, "No delegate for a simple actor - delegator is %s\n", delegator.getName());
		throw new UnsupportedOperationException();
	}

	@Override
	public Engine getEngine() {
		return engine;
	}

	@Override
	public Integer getId() {
		Logger.error(delegator, "No id for a simple actor - delegator is %s\n", delegator.getName());
		throw new UnsupportedOperationException();
	}

	public BlockingQueue<Event> getQueue() {
		return events;
	}

	@Override
	public void post(final Event event) {
		Objects.requireNonNull(event);
		while (!events.offer(event)) {
			// System.out.printf("%s No room in queue\n", getName());
			Thread.yield();
		}
	}

	@Override
	public void process(final Event event) {
		Logger.error(this, "No event to process for a simple actor");
		throw new UnsupportedOperationException();
	}

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

	private boolean runBeforeStart() {
		boolean stop = false;
		LOOP: while (true) {
			try {
				final Event event = events.poll(TIMEOUT, TimeUnit.SECONDS);
				// final Event event = events.poll();
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
