package jpnco.simula.actors;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import jpnco.simula.Actor;
import jpnco.simula.Engine;
import jpnco.simula.Event;
import jpnco.simula.engine.EventImpl;
import jpnco.simula.engine.IdBuilder;

/**
 *
 * This actor is responsible to signal Time event and to process alarms. There
 * is one and only one TimeSource per platform. The time is given by the number
 * of seconds since the beginning of the simulation (i.e. when the
 * Engine.start() method is called on the platform engine.
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
 * <p>
 * It's possible to request an alarm to be fired at a given time. For this,
 * requestor actor must signal an event with topic REQUEST_ALARM with two or
 * three parameters :
 * <ul>
 * <li>the topic used to fire this alarm ;
 * <li>the time to fire this alarm ;
 * <li>the period of this alarm (optional).
 * </ul>
 * Time and period are in real time. Do not use time factor to define their
 * values.
 * <p>
 * The alarm is signaled by an event with the topic associated to the alarm.
 * <p>
 * Until an alarm is fired, it can be cleared.
 *
 * @author Jean-Pascal Cozic
 *
 */
public final class TimeSource implements Actor {

	/**
	 * This class represents an alarm that is set to fire at a given time. Fire an
	 * alarm consists to signal an event with the topic associated to this alarm.
	 * <p>
	 * Be careful, there is only one alarm per topic.
	 */
	private static class Alarm {
		private static final int NO_PERIOD = -1;
		private final String topic;
		private int timeToFire;
		private final int period;

		/**
		 * Constructor
		 *
		 * @param topic      the topic of the event to signal
		 * @param timeToFire the time to fire
		 */
		Alarm(final String topic, final int timeToFire) {
			this(topic, timeToFire, NO_PERIOD);
		}

		Alarm(final String topic, final int timeToFire, final int period) {
			this.topic = topic;
			this.timeToFire = timeToFire;
			this.period = period;
		}

		/**
		 * Returns the time to fire of this alarm
		 *
		 * @return the time to fire of this alarm
		 */
		public int getTimeToFire() {
			return timeToFire;
		}

		/**
		 * Returns the topic of this alarm
		 *
		 * @return the the topic of this alarm
		 */
		public String getTopic() {
			return topic;
		}

		public boolean isPeriodic() {
			return period != Alarm.NO_PERIOD;
		}

		public void update() {
			timeToFire += period;

		}
	}

	/**
	 * This class represents the clock of the TimeSource. It is responsible to
	 * update the time of the TimeSource each simulated second.
	 */
	private static class Clock implements Runnable {

		private int currentTime = 0;
		private final ScheduledExecutorService scheduler;
		private TimeSource timeSource;

		Clock() {
			scheduler = Executors.newScheduledThreadPool(1);
		}

		@Override
		public void run() {
			timeSource.setTime(++currentTime);
		}

		/**
		 * Starts the clock
		 *
		 * @param timeSource the timeSource that owns this clock
		 * @param period     the period of this clock
		 */
		void start(final TimeSource timeSource, final int period) {
			this.timeSource = timeSource;
			scheduler.scheduleAtFixedRate(this, period, period, TimeUnit.SECONDS);
		}

		/**
		 * Stops this clock
		 */
		void stop() {
			scheduler.shutdown();
		}
	}

	public final int TIME_FACTOR;
	private final int TIMEOUT;
	private int currentTime = 0;
	private final LinkedBlockingQueue<Event> events;
	private final Map<String, Alarm> alarms = new HashMap<>();
	private final Integer id;
	private final Engine engine;
	private final Clock clock;

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
		clock = new Clock();
		clock.start(this, TIMEOUT);
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
		final TimeSource other = (TimeSource) obj;
		return Objects.equals(id, other.id);
	}

	/**
	 * Fire an alarm
	 *
	 * @param alarm the alarm to fire
	 */
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
	public int hashCode() {
		return Objects.hash(id);
	}

	@Override
	public void post(final Event event) {
		while (!events.offer(event)) {
			// System.out.printf("%s No room in queue\n", getName());
			Thread.yield();
		}
	}

	/**
	 * Process an event. The event must be a request to set an alarm or a request to
	 * clear an alarm. Any other event is ignored.
	 *
	 * @param event the event to process
	 */
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
			Logger.error(this, "%s unexpected event %s\n", getClass().getSimpleName(), event.getTopic());
		}
	}

	/**
	 * Clears an alarm
	 *
	 * @param event the event to process
	 */
	private void processClearAlarm(final Event event) {
		Logger.debug(this, "clears alarm %s\n", event.getParameters()[0]);
		alarms.remove(event.getParameters()[0]);
	}

	/**
	 * Requests to set an alarm
	 *
	 * @param event the event to process
	 */
	private void processRequestAlarm(final Event event) {
		final Object[] params = event.getParameters();
		final String topic = (String) params[0];
		final int time = (Integer) params[1];
		Alarm alarm;
		if (params.length == 3) {
			alarm = new Alarm(topic, time, TIME_FACTOR * (Integer) params[2]);
		} else {
			alarm = new Alarm(topic, time);
		}
		Logger.debug(this, "creates alarm %s:%d\n", topic, time);
		alarms.put(topic, alarm);
	}

	@Override
	public void purgeEvents() {
		events.clear();
	}

	/**
	 * This method contains the main loop of the actor. It is called by the engine
	 * constructor.
	 */
	@Override
	public void run() {
		Logger.debug(this, "is running with TIME_FACTOR=%d\n", TIME_FACTOR);
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

	/**
	 * This method is called after the START event is received. It is the main loop
	 * of the actor. It processes events until a stop is requested.
	 */
	private void runAfterStart() {
		while (true) {
			try {
				// Wait for an event during at most one second
				// if an event occurs
				final Event event = events.poll(TIMEOUT, TimeUnit.SECONDS);
				if (event != null) {
					if (Engine.STOP_EVENT.equals(event.getTopic())) {
						Logger.debug(this, "STOP requested by %s\n", event.getSource().getName());
						clock.stop();
						break;
					}
					process(event);
				}
			} catch (final InterruptedException e) {
				e.printStackTrace();
			}
		}
		engine.signal(EventImpl.createEvent(Engine.STOPPED_ACTOR_EVENT, this));
	}

	/**
	 * This method is called until the START event is received or a stop is is
	 * requested. Returns <code>true</code> if a stop is requested or
	 * <code>false</code> otherwise.
	 *
	 * @return <code>true</code> if a stop is requested or <code>false</code>
	 *         otherwise
	 */
	private boolean runBeforeStart() {
		boolean stop = false;
		LOOP: while (true) {
			try {
				// TODO is the timeout usefull ?
				final Event event = events.poll(TIMEOUT, TimeUnit.SECONDS);
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

	/**
	 * Set the current time and fires TIME_EVENT and alarms if needed. This method
	 * is called by the clock instance.
	 *
	 * @param time the time to set
	 */
	private void setTime(final int time) {
		currentTime = time;
		final Event timeEvent = EventImpl.createEvent(Engine.TIME_EVENT, this, currentTime);
		final Map<Boolean, List<Alarm>> m = alarms.values().stream()
				.collect(Collectors.partitioningBy(t -> currentTime == t.getTimeToFire()));
		m.get(true).stream().forEach(a -> {
			Logger.debug(this, "fires %s%n", a.getTopic());
			fire(a);
			if (a.isPeriodic()) {
				a.update();
			} else {
				alarms.remove(a.getTopic());
			}
		});
		engine.signal(timeEvent);

	}
}
