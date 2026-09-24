package jpnco.simula.engine;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import jpnco.simula.Actor;
import jpnco.simula.Engine;
import jpnco.simula.Event;
import jpnco.simula.actors.Logger;
import jpnco.simula.actors.TimeSource;

/**
 * The implementation of the Engine interface.
 *
 * @author Jean-Pascal Cozic
 *
 */
public final class EngineImpl implements Engine {

	private final Map<Integer, Actor> actors = new ConcurrentHashMap<>();
	private final Set<Engine> children = new HashSet<>();
	private final LinkedBlockingQueue<Event> events = new LinkedBlockingQueue<>();
	private final Integer id;
	private final Engine parent;
	private final Map<String, Set<Actor>> subscribersBytopic = new ConcurrentHashMap<>();
	private final int TIME_FACTOR;
	private final int TIMEOUT;
	private final TimeSource timeSource;
	private final String name;
	private final Logger logger;
	private final ExecutionMode executionMode;
	private final ReentrantLock lock = new ReentrantLock();

	public EngineImpl(final String title, final Engine parent) {
		this(title, parent, 0, ExecutionMode.VIRTUAL);
	}

	public EngineImpl(final String title, final Engine parent, final ExecutionMode mode) {
		this(title, parent, 0, mode);
	}

	private EngineImpl(final String title, final Engine parent, final int timeFactor, final ExecutionMode mode) {
		this.parent = parent;
		this.executionMode = Objects.requireNonNull(mode);
		if (parent != null) {
			parent.addChild(this);
			TIME_FACTOR = parent.getTimeFactor();
		} else {
			TIME_FACTOR = timeFactor;
		}
		TIMEOUT = 10 * TIME_FACTOR;
		name = title;
		id = IdBuilder.nextId();
		start(this);
		logger = new Logger(this);
		registerAndStart(logger);
		if (parent == null) {
			timeSource = new TimeSource(this, TIME_FACTOR);
			registerAndStart(timeSource);
		} else {
			timeSource = null;
		}
		subscribe(Engine.START_EVENT);
		subscribe(Engine.STOP_EVENT);
		subscribe(Engine.STOPPED_ACTOR_EVENT);
		subscribe(Engine.STOPPED_ENGINE_EVENT);
		subscribe(Engine.TIME_EVENT);
	}

	public EngineImpl(final String title, final int timeFactor) {
		this(title, null, timeFactor, ExecutionMode.VIRTUAL);
	}

	public EngineImpl(final String title, final int timeFactor, final ExecutionMode mode) {
		this(title, null, timeFactor, mode);
	}

	@Override
	public void addChild(final Engine child) {
		lock.lock();
		try {
			children.add(child);
		} finally {
			lock.unlock();
		}
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
		final EngineImpl other = (EngineImpl) obj;
		return Objects.equals(id, other.id);
	}

	/**
	 * Filters actors that are not an Engine or Logger instance
	 *
	 * @param actor the actor to be filtered
	 * @return true if the actor is not an EngineImpl or Logger instance
	 */
	boolean filter(final Actor actor) {
		return !EngineImpl.class.equals(actor.getClass()) && !Logger.class.equals(actor.getClass());
	}

	@Override
	public Actor getDelegate() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Engine getEngine() {
		return this;
	}

	@Override
	public Integer getId() {
		return id;
	}

	@Override
	public Logger getLogger() {
		return logger;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public Engine getParent() {
		return parent;
	}

	@Override
	public String getSimpleName() {
		return getName();
	}

	/**
	 * Returns a copy of the set of subscribers of a topic. So it is possible to
	 * subscribe and unsubscribe during the copy is iterated
	 *
	 * @param topic to searched topic
	 * @return a copy of the set of subscribers of a topic
	 */
	private Set<Actor> getSubscribers(final String topic) {
		lock.lock();
		try {
			final Set<Actor> subs = subscribersBytopic.get(topic);
			if (subs != null) {
				final Set<Actor> subscribers = new HashSet<>(subs);
				return subscribers;
			}
			return Collections.emptySet();
		} finally {
			lock.unlock();
		}
	}

	@Override
	public int getTime() {
		// timeSource may be null during initialization. So time is 0.
		if (timeSource != null) {
			return timeSource.getTime();
		}
		if (parent != null) {
			return parent.getTime();
		}
		return 0;
	}

	@Override
	public int getTimeFactor() {
		if (parent != null) {
			return parent.getTimeFactor();
		}
		return TIME_FACTOR;
	}

	@Override
	public TimeSource getTimeSource() {
		return timeSource;
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

	@Override
	public void process(final Event event) {
		Logger.error(this, "Unexpected event %s.\n", event.getTopic());
	}

	private void processStartEvent(final Event event) {
		Logger.debug(this, "===== processStartEvent(%s) =====\n", getName());
		signalToChildren(event);
	}

	private void processStopEvent(final Event event) {
		Logger.debug(this, "===== processStopEvent(%s) =====\n", getName());
		signalToChildren(event);
	}

	private boolean processStoppedActorEvent(final Event event) {
		Logger.debug(this, "Actor %s is stopped\n", event.getSource().getName());
		return unregister(event.getSource());
	}

	private void processStoppedEngineEvent(final Event event) {
		final Engine child = (Engine) event.getParameters()[0];
		Logger.trace(this, "Child engine %s is stopped\n", child.getName());
		lock.lock();
		try {
			children.remove(child);
		} finally {
			lock.unlock();
		}
	}

	private void processTimeEvent(final Event event) {
		Logger.trace(this, "signals time event to child engines\n");
		signalToChildren(event);
	}

	private void register(final Actor actor) {
		Logger.trace(this, "Registering actor %s:%d\n", actor.getName(), actor.getId());
		lock.lock();
		try {
			actors.put(actor.getId(), actor);
		} finally {
			lock.unlock();
		}
	}

	@Override
	public Actor registerAndStart(final Actor actor) {
		register(actor);
		start(actor);
		return actor;
	}

	@Override
	public void run() {
		try {
			Logger.trace(this, "is running\n");
			LOOP: while (true) {
				try {
					final Event event = events.poll(TIMEOUT, TimeUnit.SECONDS);
					if (event != null) {
						switch (event.getTopic()) {
						case Engine.START_EVENT:
							processStartEvent(event);
							break;
						case Engine.STOP_EVENT:
							processStopEvent(event);
							break;
						case Engine.STOPPED_ACTOR_EVENT:
							if (processStoppedActorEvent(event) && children.isEmpty()) {
								break LOOP;
							}
							break;
					case Engine.STOPPED_ENGINE_EVENT:
						processStoppedEngineEvent(event);
						lock.lock();
						try {
							if (actors.isEmpty() && children.isEmpty()) {
								break LOOP;
							}
						} finally {
							lock.unlock();
						}
						break;
						case Engine.TIME_EVENT:
							processTimeEvent(event);
							break;
						default:
							process(event);
						}
					} else {
//						System.out.printf("%s is still alive with %d actors and %d child engines alive.\n",
//								getSimpleName(), actors.size(), children.size());
					}
				} catch (final InterruptedException e) {
					e.printStackTrace();
				}
			}
			if (parent != null) {
				// signals parent that this engine is stopped
				final Event stopped = EventImpl.createEvent(Engine.STOPPED_ENGINE_EVENT, this, this);
				parent.signal(stopped);
			}
			Logger.trace(this, "is stopped\n");
		} catch (final Throwable exc) {
			// System.out.printf("%s is dead because of %s\n", getSimpleName(),
			// exc.getClass().getCanonicalName());
			Logger.error(this, "Stopping actor %s because of %s(message=%s)\n", getName(),
					exc.getClass().getCanonicalName(), exc.getMessage());
			exc.printStackTrace();
			Logger.trace(this, "is stopped\n");
		}
	}

	@Override
	public void signal(final Event event) {
		Objects.requireNonNull(event);
		if (!Engine.LOG_EVENT.equals(event.getTopic())) {
			Logger.trace(this, "%s signals %s at %d\n", event.getSource().getName(), event.getTopic(), event.getTime());
		} else {
			// Do not log because of infinite loop !
		}
		if (Engine.STOP_EVENT.equals(event.getTopic())) {
			// Must stop logger after all actors in order to have the maximum of logs
			getSubscribers(event.getTopic()).stream().sorted().forEach(s -> {
				s.post(event);
				Thread.yield();
			});
		} else {
			getSubscribers(event.getTopic()).stream().forEach(s -> s.post(event));
		}
	}

	@Override
	public void signalToChildren(final Event event) {
		Objects.requireNonNull(event);
		lock.lock();
		try {
			children.parallelStream().forEach(s -> s.signal(event.duplicate(s)));
		} finally {
			lock.unlock();
		}
	}

	@Override
	public void start() {
		Logger.trace(this, "starting (%s)...\n", getName());
		final Event start = EventImpl.createEvent(Engine.START_EVENT, this, (Actor) null);
		signal(start);
	}

	/**
	 * Starts the supplied actor on a thread consistent with the engine's
	 * {@link ExecutionMode} (FR-001, FR-004).
	 * <p>
	 * In {@link ExecutionMode#VIRTUAL} mode the actor is started on a virtual
	 * thread carrying a meaningful name (FR-006). If the runtime does not support
	 * virtual threads (a Java release older than 21), {@link Thread#ofVirtual()}
	 * throws {@link UnsupportedOperationException}; the framework does not
	 * silently fall back, so the failure is clear and immediate (FR-009). In
	 * {@link ExecutionMode#PLATFORM} mode the actor is started on a classic
	 * platform thread (FR-001, FR-004).
	 *
	 * @param actor the actor to start; must not be null
	 */
	private void start(final Actor actor) {
		Objects.requireNonNull(actor);
		Logger.trace(this, "starting (%s)...\n", actor.getName());
		if (ExecutionMode.VIRTUAL.equals(executionMode)) {
			Thread.ofVirtual().name(actor.getName()).start(actor);
		} else {
			new Thread(actor, actor.getName()).start();
		}
	}

	@Override
	public void stop() {
		Logger.debug(this, "===== stop(%s) =====\n", getName());
		final Event stop = EventImpl.createEvent(Engine.STOP_EVENT, this, (Actor) null);
		signal(stop);
	}

	@Override
	public void subscribe(final Actor actor, final String topic) {
		Objects.requireNonNull(actor);
		Objects.requireNonNull(topic);
		Logger.trace(this, "Actor %s subscribes to topic %s\n", actor.getName(), topic);
		lock.lock();
		try {
			Set<Actor> subscribers = subscribersBytopic.get(topic);
			if (subscribers == null) {
				subscribers = new HashSet<>();
				subscribersBytopic.put(topic, subscribers);
			}
			subscribers.add(actor);
		} finally {
			lock.unlock();
		}
	}

	@Override
	public String toString() {
		final StringBuffer buf = new StringBuffer();
		buf.append("[Engine ");
		buf.append(getName());
		buf.append(" #child engines=");
		buf.append(children.size());
		buf.append(" #actors=");
		buf.append(actors.size());
		buf.append("\n");
		lock.lock();
		try {
			actors.values().stream().forEach(a -> {
				buf.append("\t");
				buf.append(a.getSimpleName());
				buf.append("\n");
			});
			if (!children.isEmpty()) {
				// display all child engines
				buf.append("Child engines\n");
				children.stream().forEach(c -> {
					buf.append(c.toString());
					buf.append("\n");
				});
			}
		} finally {
			lock.unlock();
		}
		buf.append("]");
		return buf.toString();
	}

	@Override
	public boolean unregister(final Actor actor) {
		Objects.requireNonNull(actor);
		lock.lock();
		try {
			if (!(actor instanceof Engine)) {
				Logger.debug(this, "Unregister %s\n", actor.getName());
				subscribersBytopic.values().stream().forEach(s -> s.remove(actor));
				if (actors.remove(actor.getId()) == null) {
					// System.out.printf("Actor %s is already unregistered\n", actor.getName());
					Logger.error(this, "Actor %s is already unregistered\n", actor.getName());
					Thread.dumpStack();
				}
			} else {
				Logger.error(this, "Unregister Engine %s - %d\n", actor.getName(), actors.size());
				Thread.dumpStack();
			}
			return actors.isEmpty();
		} finally {
			lock.unlock();
		}
	}

	@Override
	public void unsubscribe(final Actor actor, final String topic) {
		Objects.requireNonNull(actor);
		Objects.requireNonNull(topic);
		Logger.trace(this, "%s unsubscribes to topic %s\n", actor.getName(), topic);
		lock.lock();
		try {
			final Set<Actor> subscribers = subscribersBytopic.get(topic);
			if (subscribers != null) {
				subscribers.remove(actor);
			} else {
				Logger.error(this, "Cannot unsubscribe %s because it is not subscribed by %s\n", topic,
						actor.getSimpleName());
			}
		} finally {
			lock.unlock();
		}
	}
}
