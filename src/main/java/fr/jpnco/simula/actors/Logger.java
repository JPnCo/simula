package fr.jpnco.simula.actors;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import fr.jpnco.simula.Actor;
import fr.jpnco.simula.Engine;
import fr.jpnco.simula.Event;
import fr.jpnco.simula.engine.ActorDelegate;
import fr.jpnco.simula.engine.EventImpl;
import fr.jpnco.simula.engine.IdBuilder;

/**
 * Logger actor is responsible to log the activity of all actors for a given
 * engine. There is one logger per instance of engine.
 * <p>
 * Only static methods <b>debug</b>, <b>warning</b>, <b>info</b> and
 * <b>error</b> has to be used by actors.
 *
 * @author Jean-Pascal Cozic
 *
 */
public final class Logger implements Actor {

	/**
	 * The severity levels of the logger, ordered from the most verbose (TRACE) to
	 * the least (PROBE). Each level carries a numeric threshold used to decide
	 * whether a message is logged.
	 */
	public enum Level {
		TRACE(0), DEBUG(1), WARNING(2), INFO(3), ERROR(4), PROBE(5);

		private final int level;

		/**
		 * Builds a level with its numeric threshold.
		 *
		 * @param level the numeric threshold of this level
		 */
		private Level(final int level) {
			this.level = level;
		}
	}

	/**
	 * Holds the activation state of a single level for a single source actor.
	 */
	private static class LevelActivation {

		/**
		 * The highest activated level threshold; defaults to ERROR.
		 */
		private int activated = Level.ERROR.level;

		/**
		 * Returns whether the given level is activated, i.e. its threshold is at
		 * least the stored activated threshold.
		 *
		 * @param level the level to test
		 * @return {@code true} if the level is activated
		 */
		boolean isActivated(final Level level) {
			return level.level >= activated;
		}

		/**
		 * Sets the activated level for this source.
		 *
		 * @param level       the level to set
		 * @param isActivated whether the level is activated
		 */
		void setActivated(final Level level, final boolean isActivated) {
			activated = level.level;
		}
	}

	private static Map<Actor, LevelActivation> levelActivations = new HashMap<>();

	private static Level forcedLevel = null;

	private static final String PREFIX_PROBE = "%d ";

	private static final String PREFIX = "%d %s %s ";

	private static final int TIMEOUT = 5;

	/**
	 * Logs a debug message for the given source actor.
	 *
	 * @param source     the actor that produces the log
	 * @param parameters the message format followed by its arguments
	 */
	public static void debug(final Actor source, final Object... parameters) {
		log(Level.DEBUG, source, parameters);
	}

	/**
	 * Logs an error message for the given source actor.
	 *
	 * @param source     the actor that produces the log
	 * @param parameters the message format followed by its arguments
	 */
	public static void error(final Actor source, final Object... parameters) {
		log(Level.ERROR, source, parameters);
	}

	/**
	 * Forces the whole logger to use the given level, overriding per-source
	 * activation.
	 *
	 * @param level the level to force
	 */
	public static void forceLevel(final Level level) {
		forcedLevel = level;
	}

	/**
	 * Logs an info message for the given source actor.
	 *
	 * @param source     the actor that produces the log
	 * @param parameters the message format followed by its arguments
	 */
	public static void info(final Actor source, final Object... parameters) {
		log(Level.INFO, source, parameters);
	}

	/**
	 * Returns whether logging is activated for the given source at the given
	 * level, either because the source is activated or because a level is forced.
	 *
	 * @param source the actor whose activation is queried
	 * @param level  the level to test
	 * @return {@code true} if the level is activated for the source
	 */
	public static boolean isActivated(final Actor source, final Level level) {
		if (forcedLevel != null) {
			return level.level >= forcedLevel.level;
		}
		LevelActivation activation = levelActivations.get(source);
		if (activation == null) {
			activation = new LevelActivation();
			levelActivations.put(source, activation);
		}
		return activation.isActivated(level);
	}

	/**
	 * Builds and signals a log event for the given source at the given level,
	 * unless the level is not activated. A {@link Logger} source is printed
	 * directly instead of signaling an event, to avoid recursion.
	 *
	 * @param level      the level of the message
	 * @param source     the actor that produces the log
	 * @param parameters the message format followed by its arguments
	 */
	private static void log(final Level level, final Actor source, final Object... parameters) {
		if (isActivated(source, level)) {
			if (!(source instanceof Logger)) {
				final Object[] levelAsArray = { level };
				final Object[] both = Stream.of(levelAsArray, parameters).flatMap(Stream::of).toArray(Object[]::new);
				final Event log = EventImpl.createEvent(Engine.LOG_EVENT, source, both);
				source.getEngine().signal(log);
			} else {
				final String format = (String) parameters[0];
				System.out.printf(String.format(PREFIX, source.getEngine().getTime(), level, source.getName()) + format,
						Arrays.copyOfRange(parameters, 1, parameters.length));
			}
		}
	}

	/**
	 * Logs a probe message for the given source actor.
	 *
	 * @param source     the actor that produces the log
	 * @param parameters the message format followed by its arguments
	 */
	public static void probe(final Actor source, final Object... parameters) {
		log(Level.PROBE, source, parameters);
	}

	/**
	 * Sets whether logging is activated for the given source at the given level.
	 *
	 * @param source      the actor whose activation is set
	 * @param level       the level to configure
	 * @param isActivated whether the level is activated
	 */
	public static void setActivated(final Actor source, final Level level, final boolean isActivated) {
		LevelActivation activation = levelActivations.get(source);
		if (activation == null) {
			activation = new LevelActivation();
			levelActivations.put(source, activation);
		}
		activation.setActivated(level, isActivated);
	}

	/**
	 * Logs a trace message for the given source actor.
	 *
	 * @param source     the actor that produces the log
	 * @param parameters the message format followed by its arguments
	 */
	public static void trace(final Actor source, final Object... parameters) {
		log(Level.TRACE, source, parameters);
	}

	/**
	 * Logs a warning message for the given source actor.
	 *
	 * @param source     the actor that produces the log
	 * @param parameters the message format followed by its arguments
	 */
	public static void warning(final Actor source, final Object... parameters) {
		log(Level.WARNING, source, parameters);
	}

	private final Actor delegate;

	private final Integer id;

	/**
	 * Builds a logger actor.
	 *
	 * @param engine the engine that run this actor.
	 */
	public Logger(final Engine engine) {
		delegate = ActorDelegate.createDelegate(engine, this);
		id = IdBuilder.nextId();
		subscribe(Engine.LOG_EVENT);
	}

	/**
	 * Returns whether this logger is equal to the given object. Two loggers are
	 * equal when they have the same id.
	 *
	 * @param obj the object to compare with this logger
	 * @return {@code true} if the object is a logger with the same id
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
		final Logger other = (Logger) obj;
		return Objects.equals(id, other.id);
	}

	/**
	 * Returns the delegate that runs the standard event loop of this actor.
	 *
	 * @return the delegate of this logger
	 */
	@Override
	public Actor getDelegate() {
		return delegate;
	}

	/**
	 * Returns the unique id of this logger.
	 *
	 * @return the id of this logger
	 */
	@Override
	public Integer getId() {
		return id;
	}

	/**
	 * Returns a hash code for this logger based on its id, consistent with
	 * {@link #equals(Object)}.
	 *
	 * @return the hash code of this logger
	 */
	@Override
	public int hashCode() {
		return Objects.hash(id);
	}

	/**
	 * Processes a subscribed event. A {@link Engine#LOG_EVENT} is printed to the
	 * standard output; a {@link Engine#PURGE_QUEUE_EVENT} purges the supplied
	 * event queue.
	 *
	 * @param event the event to process
	 */
	@SuppressWarnings("unchecked")
	@Override
	public void process(final Event event) {
		if (Engine.LOG_EVENT.equals(event.getTopic())) {
			final Object[] parameters = event.getParameters();
			final Level level = (Level) parameters[0];
			final String format = (String) parameters[1];
			if (Level.PROBE == level) {
				System.out.printf(String.format(PREFIX_PROBE, event.getTime()) + format,
						Arrays.copyOfRange(parameters, 2, parameters.length));
			} else {
				System.out.printf(String.format(PREFIX, event.getTime(), level, event.getSource().getName()) + format,
						Arrays.copyOfRange(parameters, 2, parameters.length));

			}
		} else if (Engine.PURGE_QUEUE_EVENT.equals(event.getTopic())) {
			purgeQueue((BlockingQueue<Event>) event.getParameters()[0]);
		}
	}

	/**
	 * Drains the given queue, processing each polled event, until the queue is
	 * empty.
	 *
	 * @param events the queue to purge
	 */
	public void purgeQueue(final BlockingQueue<Event> events) {
		Event event = null;
		try {
			event = events.poll(TIMEOUT, TimeUnit.SECONDS);
		} catch (final InterruptedException e) {
			e.printStackTrace();
			Thread.currentThread().interrupt();
		}
		while (event != null) {
			process(event);
			Thread.yield();
			try {
				event = events.poll(TIMEOUT, TimeUnit.SECONDS);
			} catch (final InterruptedException e) {
				e.printStackTrace();
				Thread.currentThread().interrupt();
			}
		}
	}

}
