package jpnco.simula.actors;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import jpnco.simula.Actor;
import jpnco.simula.Engine;
import jpnco.simula.Event;
import jpnco.simula.engine.ActorDelegate;
import jpnco.simula.engine.EventImpl;
import jpnco.simula.engine.IdBuilder;

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

	public enum Level {
		TRACE(0), DEBUG(1), WARNING(2), INFO(3), ERROR(4), PROBE(5);

		private final int level;

		private Level(final int level) {
			this.level = level;
		}
	}

	private static class LevelActivation {
		private int activated = Level.ERROR.level;

		boolean isActivated(final Level level) {
			return level.level >= activated;
		}

		void setActivated(final Level level, final boolean isActivated) {
			activated = level.level;
		}
	}

	private static Map<Actor, LevelActivation> levelActivations = new HashMap<>();

	private static Level forcedLevel = null;

	private static final String PREFIX_PROBE = "%d ";

	private static final String PREFIX = "%d %s %s ";

	private static final int TIMEOUT = 5;

	public static void debug(final Actor source, final Object... parameters) {
		log(Level.DEBUG, source, parameters);
	}

	public static void error(final Actor source, final Object... parameters) {
		log(Level.ERROR, source, parameters);
	}

	public static void forceLevel(final Level level) {
		forcedLevel = level;
	}

	public static void info(final Actor source, final Object... parameters) {
		log(Level.INFO, source, parameters);
	}

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

	public static void probe(final Actor source, final Object... parameters) {
		log(Level.PROBE, source, parameters);
	}

	public static void setActivated(final Actor source, final Level level, final boolean isActivated) {
		LevelActivation activation = levelActivations.get(source);
		if (activation == null) {
			activation = new LevelActivation();
			levelActivations.put(source, activation);
		}
		activation.setActivated(level, isActivated);
	}

	public static void trace(final Actor source, final Object... parameters) {
		log(Level.TRACE, source, parameters);
	}

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

	@Override
	public Actor getDelegate() {
		return delegate;
	}

	@Override
	public Integer getId() {
		return id;
	}

	@Override
	public int hashCode() {
		return Objects.hash(id);
	}

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
