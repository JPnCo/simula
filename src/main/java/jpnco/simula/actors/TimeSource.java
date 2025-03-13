package jpnco.simula.actors;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import jpnco.simula.Actor;
import jpnco.simula.Engine;
import jpnco.simula.Event;
import jpnco.simula.engine.EventImpl;
import jpnco.simula.engine.IdBuilder;

/**
 *
 * This actor is responsible to signal Time event. There is one and only one
 * TimeSource per platform. The time is given by the number of seconds since the
 * beginning of the simulation (i.e. when the Engine.start() method is called on
 * the platform engine.
 * <p>
 * A time event is signaled each simulated second. A simulated second is given
 * by the value of TIME_FACTOR. For example, if the time factor is 1, a
 * simulated second lasts 1 second, if the time factor is 10, a simulated second
 * lasts 10 seconds. The time factor must be used to expand time and to be able
 * to process simulation that needs more than one second to execute.
 * <p>
 * The value must be tuned according to the workload of the simulation.
 *
 * TODO the time factor should be a parameter and not hard coded.
 *
 * @author Jean-Pascal Cozic
 *
 */
public final class TimeSource implements Actor {

	private static class Alarm {
		private final String topic;
		private final int timeToFire;

		Alarm(final String topic, final int timeToFire) {
			this.topic = topic;
			this.timeToFire = timeToFire;
		}

		public int getTimeToFire() {
			return timeToFire;
		}

		public String getTopic() {
			return topic;
		}
	}

	public final int TIME_FACTOR;
	private final int TIMEOUT;
	private int currentTime = 0;
	// private final BlockingQueue<Event> events;
	private final LinkedBlockingQueue<Event> events;
	private final Map<String, Alarm> alarms = new HashMap<>();
	private final Integer id;
	private final Engine engine;

	public TimeSource(final Engine engine, final int timeFactor) {
		TIME_FACTOR = timeFactor;
		TIMEOUT = 1 * TIME_FACTOR;
		id = IdBuilder.nextId();
		this.engine = engine;
		Logger.debug(this, "Building\n");
		// events = new ArrayBlockingQueue<>(10, true);
		events = new LinkedBlockingQueue<>();
		subscribe(Engine.START_EVENT);
		subscribe(Engine.STOP_EVENT);
	}

	private void fire(final Alarm alarm) {
		final Event fire = EventImpl.createEvent(alarm.getTopic(), this);
		getEngine().signal(fire);
	}

	@Override
	public Actor getDelegate() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Engine getEngine() {
		return engine;
	}

	@Override
	public Integer getId() {
		return id;
	}

	/**
	 * Returns the current time.
	 *
	 * @return the current time.
	 */
	public int getTime() {
		return currentTime;
	}

	@Override
	public void post(final Event event) {
		while (!events.offer(event)) {
			// System.out.printf("%s No room in queue\n", getName());
			Thread.yield();
		}
	}

	@Override
	public void process(final Event event) {
		switch (event.getTopic()) {
		case Engine.REQUEST_ALARM_EVENT:
			processRequestAlarm(event);
			break;
		case Engine.CLEAR_ALARM_EVENT:
			processClearAlarm(event);
			break;
		default:
			Logger.error(this, "%s must not process any events - Unexpected event %s\n", getClass().getSimpleName(),
					event.getTopic());
		}
	}

	private void processClearAlarm(final Event event) {
		alarms.remove(event.getParameters()[0]);
	}

	private void processRequestAlarm(final Event event) {
		final String topic = (String) event.getParameters()[0];
		alarms.put(topic, new Alarm(topic, (int) event.getParameters()[1]));
	}

	@Override
	public void purgeEvents() {
		events.clear();
	}

	@Override
	public void run() {
		Logger.debug(this, "Running TIME_FACTOR is %d\n", TIME_FACTOR);
		try {
			if (!runBeforeStart()) {
				runAfterStart();
			}
		} catch (final Throwable exc) {
			System.out.printf("%s is dead because of %s\n", getSimpleName(), exc.getClass().getCanonicalName());
			getEngine().unregister(this);
			Logger.error(this, "is dead because of %s\n", exc.getClass().getCanonicalName());
			throw exc;
		}
		Logger.debug(this, "is stopped\n");
	}

	private void runAfterStart() {
		while (true) {
			try {
				final Event event = events.poll(TIMEOUT, TimeUnit.SECONDS);
				if (event != null) {
					if (Engine.STOP_EVENT.equals(event.getTopic())) {
						Logger.debug(this, "STOP requested by %s\n", event.getSource().getName());
						break;
					}
					process(event);
				} else {
					Logger.trace(this, "Time is %d\n", currentTime + 1);
					final Event timeEvent = EventImpl.createEvent(Engine.TIME_EVENT, this, ++currentTime);
					final Map<Boolean, List<Alarm>> m = alarms.values().stream()
							.collect(Collectors.partitioningBy(t -> currentTime == t.getTimeToFire()));
					m.get(true).stream().forEach(a -> {
						fire(a);
						alarms.remove(a.getTopic());
					});
					engine.signal(timeEvent);
				}
			} catch (final InterruptedException e) {
				e.printStackTrace();
			}
		}
		engine.signal(EventImpl.createEvent(Engine.STOPPED_ACTOR_EVENT, this));
	}

	private boolean runBeforeStart() {
		boolean stop = false;
		LOOP: while (true) {
			try {
				final Event event = events.poll(TIMEOUT, TimeUnit.MILLISECONDS);
				if (event != null) {
					switch (event.getTopic()) {
					case Engine.START_EVENT:
						Logger.debug(this, "Starting\n");
						break LOOP;
					case Engine.STOP_EVENT:
						beforeStop();
						Logger.debug(this, "STOP requested by %s\n", event.getSource().getName(),
								event.getSource().getName());
						stop = true;
						break LOOP;
					case Engine.STOP_ME_EVENT:
						if (this == event.getSource()) {
							stop = true;
							break LOOP;
						}
						break;
					default:
					}
				}
			} catch (final InterruptedException e) {
				e.printStackTrace();
			}
		}
		return stop;
	}
}
