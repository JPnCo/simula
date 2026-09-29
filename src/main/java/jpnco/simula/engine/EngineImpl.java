package jpnco.simula.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
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
	private final Set<Engine> children = new LinkedHashSet<>();
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

	/**
	 * Builds a root engine with the default virtual-thread execution mode
	 * (FR-002).
	 *
	 * @param title  the name of this engine
	 * @param parent the parent engine, or {@code null} for the root engine
	 */
	public EngineImpl(final String title, final Engine parent) {
		this(title, parent, 0, ExecutionMode.VIRTUAL);
	}

	/**
	 * Builds an engine with an explicit execution mode (FR-003).
	 *
	 * @param title  the name of this engine
	 * @param parent the parent engine, or {@code null} for the root engine
	 * @param mode   the execution mode of this engine
	 */
	public EngineImpl(final String title, final Engine parent, final ExecutionMode mode) {
		this(title, parent, 0, mode);
	}

	/**
	 * Builds an engine with the given time factor and execution mode, registering
	 * and starting its logger and (for the root engine) its time source (FR-003).
	 *
	 * @param title     the name of this engine
	 * @param parent    the parent engine, or {@code null} for the root engine
	 * @param timeFactor the time factor of this engine
	 * @param mode      the execution mode of this engine
	 */
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

	/**
	 * Builds a root engine with the default virtual-thread execution mode and the
	 * given time factor (FR-002).
	 *
	 * @param title      the name of this engine
	 * @param timeFactor the time factor of this engine
	 */
	public EngineImpl(final String title, final int timeFactor) {
		this(title, null, timeFactor, ExecutionMode.VIRTUAL);
	}

	/**
	 * Builds a root engine with the given time factor and execution mode
	 * (FR-003).
	 *
	 * @param title      the name of this engine
	 * @param timeFactor the time factor of this engine
	 * @param mode       the execution mode of this engine
	 */
	public EngineImpl(final String title, final int timeFactor, final ExecutionMode mode) {
		this(title, null, timeFactor, mode);
	}

	/**
	 * Builds a child engine with the given time factor and the default
	 * virtual-thread execution mode (FR-002).
	 *
	 * @param title      the name of this engine
	 * @param parent     the parent engine
	 * @param timeFactor the time factor of this engine
	 */
	public EngineImpl(String title, Engine parent, int timeFactor) {
		this(title, parent, timeFactor, ExecutionMode.VIRTUAL);
	}

	/**
	 * Adds a child engine to this engine, guarded by the engine lock (FR-012).
	 *
	 * @param child the child to add
	 */
	@Override
	public void addChild(final Engine child) {
		lock.lock();
		try {
			children.add(child);
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Returns whether this engine is equal to the given object. Two engines are
	 * equal when they have the same id.
	 *
	 * @param obj the object to compare with this engine
	 * @return {@code true} if the object is an engine with the same id
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

	/**
	 * An engine has no delegate.
	 *
	 * @return always throws {@link UnsupportedOperationException}
	 */
	@Override
	public Actor getDelegate() {
		throw new UnsupportedOperationException();
	}

	/**
	 * Returns this engine, since an engine is its own engine.
	 *
	 * @return this engine
	 */
	@Override
	public Engine getEngine() {
		return this;
	}

	/**
	 * Returns the unique id of this engine.
	 *
	 * @return the id of this engine
	 */
	@Override
	public Integer getId() {
		return id;
	}

	/**
	 * Returns the logger actor of this engine.
	 *
	 * @return the logger of this engine
	 */
	@Override
	public Logger getLogger() {
		return logger;
	}

	/**
	 * Returns the name of this engine.
	 *
	 * @return the name of this engine
	 */
	@Override
	public String getName() {
		return name;
	}

	/**
	 * Returns the parent engine of this engine, or {@code null} for the root
	 * engine.
	 *
	 * @return the parent engine
	 */
	@Override
	public Engine getParent() {
		return parent;
	}

	/**
	 * Returns the child engines of this engine as a non-modifiable list, in the
	 * order in which they were added (FR-011), guarded by the engine lock
	 * (FR-012).
	 *
	 * @return a non-modifiable, ordered list of the child engines
	 */
	@Override
	public List<Engine> getChildren() {
		lock.lock();
		try {
			return Collections.unmodifiableList(new ArrayList<>(children));
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Returns the simple name of this engine, which is its name.
	 *
	 * @return the name of this engine
	 */
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

	/**
	 * Returns the current simulated time, from the time source or the parent
	 * engine. Returns {@code 0} while the time source is not yet initialized.
	 *
	 * @return the current simulated time
	 */
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

	/**
	 * Returns the time factor of this engine, or that of its parent when it has
	 * one.
	 *
	 * @return the time factor of this engine
	 */
	@Override
	public int getTimeFactor() {
		if (parent != null) {
			return parent.getTimeFactor();
		}
		return TIME_FACTOR;
	}

	/**
	 * Returns the time source of this engine, or {@code null} for a child engine.
	 *
	 * @return the time source of this engine
	 */
	@Override
	public TimeSource getTimeSource() {
		return timeSource;
	}

	/**
	 * Returns a hash code for this engine based on its id, consistent with
	 * {@link #equals(Object)}.
	 *
	 * @return the hash code of this engine
	 */
	@Override
	public int hashCode() {
		return Objects.hash(id);
	}

	/**
	 * Offers the event to this engine's queue, yielding until the event is
	 * accepted.
	 *
	 * @param event the event to post
	 */
	@Override
	public void post(final Event event) {
		while (!events.offer(event)) {
			// System.out.printf("%s No room in queue\n", getName());
			Thread.yield();
		}
	}

	/**
	 * Processes a subscribed event. Any event reaching this default handler is
	 * unexpected and is logged as an error.
	 *
	 * @param event the event to process
	 */
	@Override
	public void process(final Event event) {
		Logger.error(this, "Unexpected event %s.\n", event.getTopic());
	}

	/**
	 * Handles a START event by forwarding it to the child engines.
	 *
	 * @param event the START event to process
	 */
	private void processStartEvent(final Event event) {
		Logger.debug(this, "===== processStartEvent(%s) =====\n", getName());
		signal(EventImpl.createEvent(Engine.STARTED_ACTOR_EVENT, this));
		signalToChildren(event);
	}

	/**
	 * Handles a STOP event by forwarding it to the child engines.
	 *
	 * @param event the STOP event to process
	 */
	private void processStopEvent(final Event event) {
		Logger.debug(this, "===== processStopEvent(%s) =====\n", getName());
		signalToChildren(event);
	}

	/**
	 * Handles a STOPPED_ACTOR event by unregistering the stopped actor.
	 *
	 * @param event the STOPPED_ACTOR event to process
	 * @return whether no actor remains registered
	 */
	private boolean processStoppedActorEvent(final Event event) {
		Logger.debug(this, "Actor %s is stopped\n", event.getSource().getName());
		return unregister(event.getSource());
	}

	/**
	 * Handles a STOPPED_ENGINE event by removing the stopped child engine,
	 * guarded by the engine lock (FR-012).
	 *
	 * @param event the STOPPED_ENGINE event to process
	 */
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

	/**
	 * Handles a TIME event by forwarding it to the child engines.
	 *
	 * @param event the TIME event to process
	 */
	private void processTimeEvent(final Event event) {
		Logger.trace(this, "signals time event to child engines\n");
		signalToChildren(event);
	}

	/**
	 * Registers an actor in this engine, guarded by the engine lock (FR-012).
	 *
	 * @param actor the actor to register
	 */
	private void register(final Actor actor) {
		Logger.trace(this, "Registering actor %s:%d\n", actor.getName(), actor.getId());
		lock.lock();
		try {
			actors.put(actor.getId(), actor);
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Registers and starts an actor on a thread consistent with the engine's
	 * execution mode (FR-001, FR-004).
	 *
	 * @param actor the actor to register and start
	 * @return the supplied actor
	 */
	@Override
	public Actor registerAndStart(final Actor actor) {
		register(actor);
		start(actor);
		return actor;
	}

	/**
	 * Runs the main loop of this engine, processing its event queue until the
	 * engine is stopped (no actor and no child engine remains). When stopped, a
	 * child engine signals its parent.
	 */
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

	/**
	 * Signals an event to all actors subscribed to its topic. For a STOP event
	 * the subscribers are sorted and posted in order so that the logger stops
	 * last.
	 *
	 * @param event the event to signal
	 */
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

	/**
	 * Signals an event to all child engines, duplicating the event for each
	 * child, guarded by the engine lock (FR-012).
	 *
	 * @param event the event to signal to the child engines
	 */
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

	/**
	 * Starts this engine by signaling a START event to its subscribers, which
	 * forwards it to child engines and actors.
	 */
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

	/**
	 * Stops this engine by signaling a STOP event to its subscribers, which
	 * forwards it to child engines and actors (FR-008).
	 */
	@Override
	public void stop() {
		Logger.debug(this, "===== stop(%s) =====\n", getName());
		final Event stop = EventImpl.createEvent(Engine.STOP_EVENT, this, (Actor) null);
		signal(stop);
	}

	/**
	 * Subscribes an actor to a topic, guarded by the engine lock (FR-012).
	 *
	 * @param actor the actor that must subscribe the topic
	 * @param topic the topic to subscribe
	 */
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

	/**
	 * Returns a textual representation of this engine including its name, the
	 * number of child engines and actors, and the list of registered actors,
	 * guarded by the engine lock.
	 *
	 * @return a string representation of this engine
	 */
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

	/**
	 * Unregisters an actor, removing it from the registered actors and from all
	 * topic subscription sets, guarded by the engine lock (FR-012).
	 *
	 * @param actor the actor to unregister
	 * @return {@code true} if no actor remains registered
	 */
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

	/**
	 * Unsubscribes an actor from a topic, guarded by the engine lock (FR-012).
	 *
	 * @param actor the actor that must unsubscribe the topic
	 * @param topic the topic to unsubscribe
	 */
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
