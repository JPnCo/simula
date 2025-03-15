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
	 * @param topic      the topic of event
	 * @param delay      the delay to wait before to process this event
	 * @param source     the actor that signal this event
	 * @param parameters the specific parameters of this event
	 * @return the created delayed event
	 */
	public static Event createDelayedEvent(final String topic, final long delay, final Actor source,
			final Object... parameters) {
		if (delay < 0) {
			throw new UnsupportedOperationException();
		}
		return new EventImpl(topic, delay, source, parameters);
	}

	/**
	 * Creates and returns an event.
	 *
	 * @param topic      the topic of event
	 * @param source     the actor that signal this event
	 * @param parameters the specific parameters of this event
	 * @return the created event
	 */
	public static Event createEvent(final String topic, final Actor source, final Object... parameters) {
		return new EventImpl(topic, source, parameters);
	}

	/**
	 * Creates and returns a priority event.
	 *
	 * @param topic      the topic of event
	 * @param priority   the priority of this event
	 * @param source     the actor that signal this event
	 * @param parameters the specific parameters of this event
	 * @return the created priority event
	 */
	public static Event createPriorityEvent(final String topic, final int priority, final Actor source,
			final Object... parameters) {
		if (priority < 0) {
			throw new UnsupportedOperationException();
		}
		return new EventImpl(topic, priority, source, parameters);
	}

	private final String topic;

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
	 * @param topic      the topic of event
	 * @param source     the actor that signal this event
	 * @param parameters the specific parameters of this event
	 */
	private EventImpl(final String topic, final Actor source, final Object... parameters) {
		Objects.requireNonNull(topic);
		Objects.requireNonNull(source);
		isDelayed = false;
		isPrioritized = false;
		this.topic = topic;
		this.source = source;
		time = source.getEngine().getTime();
		priority = 0;
		delay = 0;
		this.parameters = Arrays.copyOf(parameters, parameters.length);
	}

	/**
	 * Builds a priority event.
	 *
	 * @param topic      the topic of event
	 * @param source     the actor that signal this event
	 * @param priority   the priority of this event
	 * @param parameters the specific parameters of this event
	 * @return the created priority event
	 */
	private EventImpl(final String topic, final int priority, final Actor source, final Object... parameters) {
		this(topic, source, parameters);
		this.priority = priority;
		isPrioritized = true;
	}

	/**
	 * Builds a delayed event.
	 *
	 * @param topic      the topic of event
	 * @param source     the actor that signal this event
	 * @param delay      the delay of this event in milliseconds
	 * @param parameters the specific parameters of this event
	 * @return the created delayed event
	 */
	public EventImpl(final String topic, final long delay, final Actor source, final Object[] parameters) {
		this(topic, source, parameters);
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
		final EventImpl other = (EventImpl) obj;
		return delay == other.delay && isDelayed == other.isDelayed && isPrioritized == other.isPrioritized
				&& Arrays.deepEquals(parameters, other.parameters) && priority == other.priority
				&& Objects.equals(source, other.source) && time == other.time && Objects.equals(topic, other.topic);
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
		return topic;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.deepHashCode(parameters);
		result = prime * result + Objects.hash(delay, isDelayed, isPrioritized, priority, source, time, topic);
		return result;
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
