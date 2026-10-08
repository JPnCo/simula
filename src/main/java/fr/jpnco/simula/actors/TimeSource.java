package fr.jpnco.simula.actors;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import fr.jpnco.simula.Actor;
import fr.jpnco.simula.Engine;
import fr.jpnco.simula.Event;
import fr.jpnco.simula.engine.EventImpl;
import fr.jpnco.simula.engine.IdBuilder;

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
 * <li>the period of this alarm (optional), or the marker {@link #POISSON}
 * followed by a positive rate (a Double, mean firings per real unit) and an
 * optional seed (a Number) for a reproducible sequence.
 * </ul>
 * Time and period are in real time. Do not use time factor to define their
 * values.
 * <p>
 * A Poisson alarm re-arms until it is cleared: each wait drawn from the
 * exponential distribution of parameter equal to the rate, rounded to whole
 * simulated units and never below one (FR-001, FR-002).
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
	 * Period marker requesting a Poisson recurrence: used in place of the period
	 * parameter of a request alarm event, followed by a positive rate and an
	 * optional seed (FR-001).
	 */
	public static final String POISSON = "POISSON";

	/** Error message when a Poisson request carries no positive rate (FR-006). */
	private static final String INVALID_POISSON_RATE =
			"Poisson alarm %s refused: rate must be a positive number\n";

	/** Error message when a Poisson request carries a seed that is not a number (FR-006). */
	private static final String INVALID_POISSON_SEED =
			"Poisson alarm %s refused: seed must be a number\n";

	/** Error message when a request carries a period that is not an integer (FR-006). */
	private static final String INVALID_PERIOD =
			"Alarm %s refused: period must be an integer or the POISSON marker\n";

	/** Debug message logged when a Poisson alarm is registered (FR-001). */
	private static final String CREATE_POISSON_ALARM =
			"creates Poisson alarm %s:%d rate=%s\n";

	/**
	 * This class represents an alarm that is set to fire at a given time. Fire an
	 * alarm consists to signal an event with the topic associated to this alarm.
	 * <p>
	 * Be careful, there is only one alarm per topic.
	 */
	private static class Alarm {
		private static final int NO_PERIOD = -1;
		private static final double NO_RATE = -1.0;
		private final String topic;
		private int timeToFire;
		private final int period;
		private final double rate;
		private final Random random;

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
			this(topic, timeToFire, period, NO_RATE, null);
		}

		/**
		 * Builds a Poisson alarm: it re-arms after a wait drawn from the
		 * exponential distribution of parameter {@code rate} until cleared
		 * (FR-001, FR-002). The random source belongs to this alarm alone, so its
		 * sequence is independent from the other alarms (FR-007).
		 *
		 * @param topic      the topic of the event to signal
		 * @param timeToFire the deterministic first firing time
		 * @param rate       the mean number of firings per real unit, positive
		 * @param random     the random source owned by this alarm
		 */
		Alarm(final String topic, final int timeToFire, final double rate, final Random random) {
			this(topic, timeToFire, NO_PERIOD, rate, random);
		}

		/**
		 * Full constructor shared by every alarm shape.
		 *
		 * @param topic      the topic of the event to signal
		 * @param timeToFire the next firing time
		 * @param period     the fixed period in internal units, {@link #NO_PERIOD}
		 *                   when the alarm has no fixed period
		 * @param rate       the Poisson rate, {@link #NO_RATE} when the alarm is
		 *                   not a Poisson alarm (FR-001)
		 * @param random     the random source owned by this alarm, {@code null}
		 *                   when the alarm is not a Poisson alarm (FR-007)
		 */
		Alarm(final String topic, final int timeToFire, final int period, final double rate,
				final Random random) {
			this.topic = topic;
			this.timeToFire = timeToFire;
			this.period = period;
			this.rate = rate;
			this.random = random;
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

		/**
		 * Returns whether this alarm is periodic. A Poisson alarm is periodic
		 * because it re-arms until cleared (FR-001).
		 *
		 * @return {@code true} if the alarm repeats, {@code false} otherwise
		 */
		public boolean isPeriodic() {
			return period != Alarm.NO_PERIOD || isPoisson();
		}

		/**
		 * Returns whether this alarm re-arms on a Poisson recurrence (FR-001).
		 *
		 * @return {@code true} when this alarm was built with a rate
		 */
		public boolean isPoisson() {
			return rate != Alarm.NO_RATE;
		}

		/**
		 * Returns the Poisson rate of this alarm.
		 *
		 * @return the mean number of firings per real unit, or {@link #NO_RATE}
		 *         when this alarm is not a Poisson alarm (FR-002)
		 */
		public double getRate() {
			return rate;
		}

		/**
		 * Returns the random source owned by this alarm.
		 *
		 * @return the alarm's own random source, {@code null} when this alarm is
		 *         not a Poisson alarm (FR-007)
		 */
		public Random getRandom() {
			return random;
		}

		/**
		 * Advances the time to fire by the period of this alarm, for periodic
		 * alarms.
		 */
		public void update() {
			timeToFire += period;

		}

		/**
		 * Advances the time to fire by the given drawn wait, for Poisson alarms
		 * (FR-002, SC-003).
		 *
		 * @param wait the number of simulated units before the next firing
		 */
		public void advance(final int wait) {
			timeToFire += wait;
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

		/**
		 * Builds a clock backed by a single-thread scheduler.
		 */
		Clock() {
			scheduler = Executors.newScheduledThreadPool(1);
		}

		/**
		 * Advances the time source to the next second.
		 */
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

	/**
	 * Terminal flag set once when {@link #run()} completes, whatever the exit path.
	 * Stopping is terminal, so it is never reset (FR-004). This custom-delegated
	 * actor reports its own stopped status instead of relying on a delegate
	 * (FR-005).
	 */
	private volatile boolean stopped;

	/**
	 * Builds a time source for the given engine and time factor, subscribing to
	 * the START and STOP events and starting its internal clock.
	 *
	 * @param engine     the engine that runs this time source
	 * @param timeFactor the time factor used to expand simulated seconds
	 */
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

	/**
	 * Returns whether this time source is equal to the given object. Two time
	 * sources are equal when they have the same id.
	 *
	 * @param obj the object to compare with this time source
	 * @return {@code true} if the object is a time source with the same id
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

	/**
	 * This actor has no delegate.
	 *
	 * @return always throws {@link UnsupportedOperationException}
	 */
	@Override
	public Actor getDelegate() {
		throw new UnsupportedOperationException();
	}

	/**
	 * Returns the engine that runs this time source.
	 *
	 * @return the engine of this time source
	 */
	@Override
	public Engine getEngine() {
		return engine;
	}

	/**
	 * Returns the unique id of this time source.
	 *
	 * @return the id of this time source
	 */
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

	/**
	 * Returns a hash code for this time source based on its id, consistent with
	 * {@link #equals(Object)}.
	 *
	 * @return the hash code of this time source
	 */
	@Override
	public int hashCode() {
		return Objects.hash(id);
	}

	/**
	 * Offers the event to this time source's queue, blocking by yielding until
	 * the event is accepted. A terminally stopped time source silently drops the
	 * event: stopping is terminal and no one will ever process it (FR-004).
	 *
	 * @param event the event to post
	 */
	@Override
	public void post(final Event event) {
		if (stopped) {
			return;
		}
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
	 * Requests to set an alarm: one-shot, fixed-period, or Poisson marker shape;
	 * a shape that matches none of them is refused with a logged error and
	 * registers nothing (FR-001, FR-005, FR-006).
	 *
	 * @param event the event to process
	 */
	private void processRequestAlarm(final Event event) {
		final Object[] params = event.getParameters();
		final String topic = (String) params[0];
		final int time = (Integer) params[1];
		if (params.length >= 4 && TimeSource.POISSON.equals(params[2])) {
			processPoissonRequest(topic, time, params);
			return;
		}
		if (params.length >= 3 && !(params[2] instanceof Integer)) {
			Logger.error(this, INVALID_PERIOD, topic);
			return;
		}
		Alarm alarm;
		if (params.length == 3) {
			alarm = new Alarm(topic, time, TIME_FACTOR * (Integer) params[2]);
		} else {
			alarm = new Alarm(topic, time);
		}
		Logger.debug(this, "creates alarm %s:%d\n", topic, time);
		alarms.put(topic, alarm);
	}

	/**
	 * Registers the Poisson alarm of a request whose third parameter is the
	 * {@link #POISSON} marker. The fourth parameter must be a positive rate and
	 * an optional fifth parameter may carry a seed; any other shape is refused
	 * with an error log and registers no alarm (FR-001, FR-006). A first firing
	 * time already elapsed fires when the request is processed, then the alarm
	 * re-arms from now (FR-001, FR-002).
	 *
	 * @param topic  the topic of the alarm
	 * @param time   the deterministic first firing time
	 * @param params the request parameters
	 */
	private void processPoissonRequest(final String topic, final int time, final Object[] params) {
		if (!(params[3] instanceof Number) || ((Number) params[3]).doubleValue() <= 0) {
			Logger.error(this, INVALID_POISSON_RATE, topic);
			return;
		}
		final double rate = ((Number) params[3]).doubleValue();
		final Random random;
		if (params.length >= 5) {
			if (!(params[4] instanceof Number)) {
				Logger.error(this, INVALID_POISSON_SEED, topic);
				return;
			}
			random = new Random(((Number) params[4]).longValue());
		} else {
			random = new Random();
		}
		Logger.debug(this, CREATE_POISSON_ALARM, topic, time, rate);
		final Alarm alarm = new Alarm(topic, time, rate, random);
		alarms.put(topic, alarm);
		if (time <= currentTime) {
			// The requested first firing instant is elapsed or already rung: the
			// equality match in setTime would never catch it, so fire at once and
			// re-arm from now, the whole sequence staying owned by this alarm
			// (FR-001, FR-002).
			fire(alarm);
			alarm.advance(drawWait(rate, random, TIME_FACTOR));
		}
	}

	/**
	 * Clears the queue of pending events of this time source.
	 */
	@Override
	public void purgeEvents() {
		events.clear();
	}

	/**
	 * Returns whether this time source's run loop has completed, whatever the exit
	 * path. As a custom-delegated actor, the time source reports its own terminal
	 * stopped state (FR-004, FR-005).
	 *
	 * @return {@code true} once {@link #run()} has completed, {@code false} while it runs
	 */
	@Override
	public boolean isStopped() {
		return stopped;
	}

	/**
	 * This method contains the main loop of the actor. It is called by the engine
	 * constructor. The finally block purges the pending events and marks this
	 * time source as terminally stopped on every exit path (FR-004).
	 */
	@Override
	public void run() {
		Logger.debug(this, "is running with TIME_FACTOR=%d\n", TIME_FACTOR);
		try {
			if (!runBeforeStart()) {
				getEngine().signal(EventImpl.createEvent(Engine.STARTED_ACTOR_EVENT, this));
				runAfterStart();
			}
		} catch (final Throwable exc) {
			System.out.printf("%s is dead because of %s\n", getSimpleName(), exc.getClass().getCanonicalName());
			getEngine().unregister(this);
			Logger.error(this, "is dead because of %s\n", exc.getClass().getCanonicalName());
			throw exc;
		} finally {
			// Setting the flag first closes the door to post, then the purge releases
			// the events already queued: stopping is terminal.
			stopped = true;
			events.clear();
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
	 * is called by the clock instance. A periodic alarm re-arms after firing:
	 * fixed-period alarms by their period, Poisson alarms by a drawn wait, while
	 * a one-shot alarm is removed (FR-002, FR-005).
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
				if (a.isPoisson()) {
					a.advance(drawWait(a.getRate(), a.getRandom(), TIME_FACTOR));
				} else {
					a.update();
				}
			} else {
				alarms.remove(a.getTopic());
			}
		});
		engine.signal(timeEvent);

	}

	/**
	 * Draws the next wait of a Poisson alarm: the inverse-CDF transform of the
	 * exponential distribution of parameter {@code rate}, scaled by the time
	 * factor like a fixed period, rounded to whole simulated units and floored at
	 * one so that two firings are always separated (FR-002, SC-003).
	 *
	 * @param rate       the mean number of firings per real unit, positive
	 * @param random     the random source owned by the alarm
	 * @param timeFactor the number of simulated units per real unit
	 * @return the next wait in simulated units, at least one
	 */
	static int drawWait(final double rate, final Random random, final int timeFactor) {
		final double exponential = -Math.log(1 - random.nextDouble()) / rate;
		return Math.max(1, (int) Math.round(timeFactor * exponential));
	}
}
