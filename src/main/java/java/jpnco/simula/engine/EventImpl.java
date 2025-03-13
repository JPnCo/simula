package jpnco.simula.engine;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

import jpnco.simula.Actor;
import jpnco.simula.Event;

public final class EventImpl implements Event {

	/**
	 * Creates and returns a delayed event.
	 *
	 * @param kind       the kind of event
	 * @param source     the actor that signal this event
	 * @param delay      the delay to wait before to process this event
	 * @param parameters the specific parameters of this event
	 * @return the created delayed event
	 */
	public static Event createDelayedEvent(final String kind, final Actor source, final long delay,
			final Object... parameters) {
		if (delay < 0) {
			throw new UnsupportedOperationException();
		}
		return new EventImpl(kind, delay, source, parameters);
	}

	/**
	 * Creates and returns an event.
	 *
	 * @param kind       the kind of event
	 * @param source     the actor that signal this event
	 * @param parameters the specific parameters of this event
	 * @return the created event
	 */
	public static Event createEvent(final String kind, final Actor source, final Object... parameters) {
		return new EventImpl(kind, source, parameters);
	}

	/**
	 * Creates and returns a priority event.
	 *
	 * @param kind       the kind of event
	 * @param source     the actor that signal this event
	 * @param priority   the priority of this event
	 * @param parameters the specific parameters of this event
	 * @return the created priority event
	 */
	public static Event createPriorityEvent(final String kind, final Actor source, final int priority,
			final Object... parameters) {
		if (priority < 0) {
			throw new UnsupportedOperationException();
		}
		return new EventImpl(kind, priority, source, parameters);
	}

	private final String kind;
	private final Object[] parameters;
	private Actor source;
	private final int time;
	private int priority;
	private long delay;
	private boolean isPrioritized;
	private boolean isDelayed;
	private long startTime;

	/**
	 * Builds an event
	 *
	 * @param kind       the kind of event
	 * @param source     the actor that signal this event
	 * @param parameters the specific parameters of this event
	 */
	private EventImpl(final String kind, final Actor source, final Object... parameters) {
		Objects.requireNonNull(kind);
		Objects.requireNonNull(source);
		isDelayed = false;
		isPrioritized = false;
		this.kind = kind;
		this.source = source;
		time = source.getEngine().getTime();
		priority = 0;
		delay = 0;
		this.parameters = Arrays.copyOf(parameters, parameters.length);
	}

	/**
	 * Builds a priority event.
	 *
	 * @param kind       the kind of event
	 * @param source     the actor that signal this event
	 * @param priority   the priority of this event
	 * @param parameters the specific parameters of this event
	 * @return the created priority event
	 */
	private EventImpl(final String kind, final int priority, final Actor source, final Object... parameters) {
		this(kind, source, parameters);
		this.priority = priority;
		isPrioritized = true;
	}

	/**
	 * Builds a delayed event.
	 *
	 * @param kind       the kind of event
	 * @param source     the actor that signal this event
	 * @param delay      the delay of this event in milliseconds
	 * @param parameters the specific parameters of this event
	 * @return the created delayed event
	 */
	public EventImpl(final String kind, final long delay, final Actor source, final Object[] parameters) {
		this(kind, source, parameters);
		this.delay = delay;
		isDelayed = true;
		startTime = System.currentTimeMillis() + delay;
	}

	@Override
	public int compareTo(final Delayed o) {
		Objects.requireNonNull(o);
		if (isPrioritized()) {
			final Event event = (Event) o;
			return getPriority() - event.getPriority();
		}
		if (isDelayed()) {
			return (int) (getDelay(TimeUnit.MICROSECONDS) - o.getDelay(TimeUnit.MICROSECONDS));
		}
		return 0;
	}

	@Override
	public Event duplicate(final Actor newSource) {
		Objects.requireNonNull(newSource);
		EventImpl clone = null;
		try {
			clone = (EventImpl) super.clone();
		} catch (final CloneNotSupportedException e) {
			e.printStackTrace();
			System.exit(-1);
		}
		clone.source = newSource;
		return clone;
	}

	@Override
	public long getDelay(final TimeUnit unit) {
		Objects.requireNonNull(unit);
		final long diff = startTime - System.currentTimeMillis();
		return unit.convert(diff, TimeUnit.MILLISECONDS);
	}

	@Override
	public Object[] getParameters() {
		return Arrays.copyOf(parameters, parameters.length);
	}

	@Override
	public int getPriority() {
		return priority;
	}

	@Override
	public Actor getSource() {
		return source;
	}

	@Override
	public int getTime() {
		return time;
	}

	@Override
	public String getTopic() {
		return kind;
	}

	@Override
	public boolean isDelayed() {
		return isDelayed;
	}

	@Override
	public boolean isPrioritized() {
		return isPrioritized;
	}

	@Override
	public String toString() {
		final StringBuffer buf = new StringBuffer();
		buf.append("[Event topic=").append(getTopic());
		if (isDelayed()) {
			buf.append(", delay=").append(delay);
		}
		if (isPrioritized()) {
			buf.append(", priority=").append(priority);
		}
		buf.append("]");
		return buf.toString();
	}
}
