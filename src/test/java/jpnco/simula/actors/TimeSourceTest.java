package jpnco.simula.actors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import jpnco.simula.Actor;
import jpnco.simula.Engine;
import jpnco.simula.Event;
import jpnco.simula.actors.Logger.Level;
import jpnco.simula.engine.ActorDelegate;
import jpnco.simula.engine.EngineImpl;
import jpnco.simula.engine.EventImpl;
import jpnco.simula.engine.IdBuilder;

/**
 * Tests for the {@link TimeSource} actor: constructor, equality, event
 * processing (alarms), the run loop, the internal clock time management and
 * queue handling.
 */
class TimeSourceTest {

	private class AnActor implements Actor {

		private final Integer id = IdBuilder.nextId();
		private final Actor delegate;
		private boolean alarmIsOk = false;
		private int count = 0;
		private int expectedTime = 0;

		AnActor(final Engine engine) {
			delegate = ActorDelegate.createDelegate(engine, this);
			subscribe(ALARM);
			subscribe(Engine.TIME_EVENT);
		}

		public int getCount() {
			return count;
		}

		@Override
		public Actor getDelegate() {
			return delegate;
		}

		@Override
		public Integer getId() {
			return id;
		}

		public boolean isAlarmOk() {
			return alarmIsOk;
		}

		@Override
		public void process(final Event event) {
			Logger.trace(this, "Process %s%n", event.getTopic());
			switch (event.getTopic()) {
			case Engine.TIME_EVENT:
				final int time = (Integer) event.getParameters()[0];
				assertEquals(++expectedTime, time);
				Logger.trace(this, "Time is now %s%n", time);
				break;
			case ALARM:
				alarmIsOk = true;
				++count;
				break;
			}

		}
	}

	private static final String ALARM = "ALARM";

	private final List<TimeSource> created = new ArrayList<>();

	@BeforeAll
	static void setUpBeforeClass() throws Exception {
	}

	@AfterAll
	static void tearDownAfterClass() throws Exception {
	}

	@BeforeEach
	void setUp() throws Exception {
		Logger.forceLevel(Level.TRACE);
	}

	@AfterEach
	void tearDown() throws Exception {
		for (final TimeSource ts : created) {
			stopClock(ts);
		}
		created.clear();
	}

	/**
	 * Creates a TimeSource and tracks it for clock cleanup.
	 *
	 * @param engine the engine to associate
	 * @param factor the time factor
	 * @return the created TimeSource
	 */
	private TimeSource newTimeSource(final Engine engine, final int factor) {
		final TimeSource ts = new TimeSource(engine, factor);
		created.add(ts);
		return ts;
	}

	/**
	 * Stops the internal clock of the given TimeSource to avoid leaking
	 * scheduler threads.
	 *
	 * @param ts the time source whose clock must be stopped
	 */
	private void stopClock(final TimeSource ts) {
		try {
			final Field clockField = TimeSource.class.getDeclaredField("clock");
			clockField.setAccessible(true);
			final Object clock = clockField.get(ts);
			final Method stop = clock.getClass().getDeclaredMethod("stop");
			stop.setAccessible(true);
			stop.invoke(clock);
		} catch (final Exception e) {
			// best effort cleanup
		}
	}

	/**
	 * Invokes the private setTime method of the given TimeSource.
	 *
	 * @param ts   the time source
	 * @param time the time to set
	 * @throws Exception if reflection fails
	 */
	private void setTime(final TimeSource ts, final int time) throws Exception {
		final Method m = TimeSource.class.getDeclaredMethod("setTime", int.class);
		m.setAccessible(true);
		m.invoke(ts, time);
	}

	/**
	 * Builds a REQUEST_ALARM event with the given parameters.
	 *
	 * @param engine the engine of the source actor
	 * @param params the event parameters
	 * @return the built event
	 */
	private Event requestAlarmEvent(final Engine engine, final Object... params) {
		final Actor src = mock(Actor.class);
		when(src.getEngine()).thenReturn(engine);
		when(src.getName()).thenReturn("src");
		return EventImpl.createEvent(Engine.REQUEST_ALARM_EVENT, src, params);
	}

	/**
	 * Builds a CLEAR_ALARM event with the given parameters.
	 *
	 * @param engine the engine of the source actor
	 * @param params the event parameters
	 * @return the built event
	 */
	private Event clearAlarmEvent(final Engine engine, final Object... params) {
		final Actor src = mock(Actor.class);
		when(src.getEngine()).thenReturn(engine);
		when(src.getName()).thenReturn("src");
		return EventImpl.createEvent(Engine.CLEAR_ALARM_EVENT, src, params);
	}

	/**
	 * Builds an event with the given topic whose source is the TimeSource itself.
	 *
	 * @param ts    the time source used as source
	 * @param topic the topic of the event
	 * @return the built event
	 */
	private Event selfEvent(final TimeSource ts, final String topic) {
		return EventImpl.createEvent(topic, ts);
	}

	/**
	 * Builds a generic event with a source different from the TimeSource.
	 *
	 * @param ts    the time source whose engine is reused
	 * @param topic the topic of the event
	 * @return the built event
	 */
	private Event otherEvent(final TimeSource ts, final String topic) {
		final Actor src = mock(Actor.class);
		when(src.getEngine()).thenReturn(ts.getEngine());
		when(src.getName()).thenReturn("other");
		return EventImpl.createEvent(topic, src);
	}

	@Test
	void testConstructor() {
		final int TIME_FACTOR = 200;
		final Engine engine = mock(Engine.class);
		final TimeSource ts = newTimeSource(engine, TIME_FACTOR);
		assertEquals(engine, ts.getEngine());
		assertThrows(UnsupportedOperationException.class, () -> {
			ts.getDelegate();
		});
		assertNotNull(ts.getId());
	}

	@Test
	void testTimeAndAlarm() {
		final int TIME_FACTOR = 2;
		final String NAME = "testAlarm";
		final EngineImpl engine = new EngineImpl(NAME, TIME_FACTOR);
		final TimeSource ts = engine.getTimeSource();
		final AnActor actor = new AnActor(engine);
		engine.registerAndStart(actor);
		engine.start();
		final Event REQUEST_ALARM = mock(Event.class);
		when(REQUEST_ALARM.getTopic()).thenReturn(Engine.REQUEST_ALARM_EVENT);
		final Object[] params = new Object[] { ALARM, 3 };
		when(REQUEST_ALARM.getParameters()).thenReturn(params);
		ts.post(REQUEST_ALARM);
		try {
			Thread.sleep(10000);
		} catch (final InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		assertTrue(actor.isAlarmOk());
		engine.stop();

	}

	@Test
	void testTimeAndPeriodicAlarm() {
		final int TIME_FACTOR = 2;
		final String NAME = "testPeriodicAlarm";
		final EngineImpl engine = new EngineImpl(NAME, TIME_FACTOR);
		final TimeSource ts = engine.getTimeSource();
		final AnActor actor = new AnActor(engine);
		engine.registerAndStart(actor);
		engine.start();
		final Event REQUEST_ALARM = mock(Event.class);
		when(REQUEST_ALARM.getTopic()).thenReturn(Engine.REQUEST_ALARM_EVENT);
		final Object[] params = new Object[] { ALARM, 3, 2 };
		when(REQUEST_ALARM.getParameters()).thenReturn(params);
		ts.post(REQUEST_ALARM);
		try {
			Thread.sleep(14000);
		} catch (final InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		assertTrue(actor.isAlarmOk());
		assertEquals(2, actor.getCount());
		engine.stop();
	}

	// ---------- Poisson alarms (feature 007) ----------

	/**
	 * Requests a seeded Poisson alarm on a mocked engine and steps the clock from
	 * 1 to 20.
	 *
	 * @param seed the seed of the random sequence
	 * @return the simulated times at which the alarm topic was signaled
	 * @throws Exception if reflection fails
	 */
	private List<Integer> poissonFireTimes(final long seed) throws Exception {
		final Engine engine = mock(Engine.class);
		when(engine.getTime()).thenReturn(0);
		final TimeSource ts = newTimeSource(engine, 2);
		ts.process(requestAlarmEvent(engine, ALARM, 2, TimeSource.POISSON, 1.0, seed));
		for (int t = 1; t <= 20; t++) {
			setTime(ts, t);
		}
		final ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
		verify(engine, atLeast(0)).signal(captor.capture());
		final List<Event> all = captor.getAllValues();
		final List<Integer> times = new ArrayList<>();
		for (int i = 0; i < all.size(); i++) {
			if (ALARM.equals(all.get(i).getTopic())) {
				for (int j = i; j < all.size(); j++) {
					if (Engine.TIME_EVENT.equals(all.get(j).getTopic())) {
						times.add((Integer) all.get(j).getParameters()[0]);
						break;
					}
				}
			}
		}
		return times;
	}

	@Test
	void poissonAlarmFiresAtIrregularGaps() throws Exception {
		final List<Integer> times = poissonFireTimes(42L);
		assertTrue(times.size() >= 3, "expected at least 3 firings, got " + times);
		assertEquals(2, times.get(0).intValue(), "the first firing is the requested time");
		final Set<Integer> gaps = new HashSet<>();
		for (int i = 1; i < times.size(); i++) {
			final int gap = times.get(i) - times.get(i - 1);
			assertTrue(gap >= 1, "gaps must be at least one unit: " + times);
			gaps.add(gap);
		}
		assertTrue(gaps.size() >= 2, "firings must be irregular, gaps=" + gaps);
	}

	@Test
	void seededPoissonAlarmsReplayIdentically() throws Exception {
		assertEquals(poissonFireTimes(42L), poissonFireTimes(42L),
				"the same rate and seed must produce the same firing sequence");
	}

	@Test
	void clearingAPoissonAlarmStopsTheFirings() throws Exception {
		final Engine engine = mock(Engine.class);
		when(engine.getTime()).thenReturn(0);
		final TimeSource ts = newTimeSource(engine, 2);
		ts.process(requestAlarmEvent(engine, ALARM, 2, TimeSource.POISSON, 1.0, 42L));
		setTime(ts, 2);
		ts.process(clearAlarmEvent(engine, ALARM));
		for (int t = 3; t <= 20; t++) {
			setTime(ts, t);
		}
		final ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
		verify(engine, atLeast(0)).signal(captor.capture());
		assertEquals(1, captor.getAllValues().stream().filter(e -> ALARM.equals(e.getTopic())).count(),
				"no firing must occur after the clear");
	}

	@Test
	void unseededPoissonAlarmFiresItsFirstTime() throws Exception {
		final Engine engine = mock(Engine.class);
		when(engine.getTime()).thenReturn(0);
		final TimeSource ts = newTimeSource(engine, 2);
		ts.process(requestAlarmEvent(engine, ALARM, 2, TimeSource.POISSON, 1.0));
		for (int t = 1; t <= 20; t++) {
			setTime(ts, t);
		}
		final ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
		verify(engine, atLeast(0)).signal(captor.capture());
		assertTrue(captor.getAllValues().stream().anyMatch(e -> ALARM.equals(e.getTopic())),
				"an unseeded Poisson alarm must still fire at its deterministic first time");
	}

	@Test
	void drawWaitMeanMatchesRateAndDispersionIsPresent() {
		final Random random = new Random(7L);
		final double rate = 1.0;
		final int timeFactor = 10;
		final int draws = 2000;
		double sum = 0;
		final Set<Integer> values = new HashSet<>();
		for (int i = 0; i < draws; i++) {
			final int wait = TimeSource.drawWait(rate, random, timeFactor);
			assertTrue(wait >= 1, "every wait is at least one unit");
			sum += wait;
			values.add(wait);
		}
		final double mean = sum / draws;
		final double expected = timeFactor / rate;
		assertTrue(Math.abs(mean - expected) / expected < 0.10,
				"empirical mean " + mean + " must be within 10% of " + expected);
		assertTrue(values.size() >= 2, "the draws must disperse");
	}

	@Test
	void poissonRequestWithNonPositiveRateIsRefused() throws Exception {
		final Engine engine = mock(Engine.class);
		when(engine.getTime()).thenReturn(0);
		final TimeSource ts = newTimeSource(engine, 2);
		ts.process(requestAlarmEvent(engine, ALARM, 2, TimeSource.POISSON, 0.0, 1L));
		ts.process(requestAlarmEvent(engine, "NEG", 2, TimeSource.POISSON, -1.0));
		for (int t = 1; t <= 20; t++) {
			setTime(ts, t);
		}
		final ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
		verify(engine, atLeast(0)).signal(captor.capture());
		assertTrue(captor.getAllValues().stream().noneMatch(e -> ALARM.equals(e.getTopic()) || "NEG".equals(e.getTopic())),
				"a refused request must register no alarm");
	}

	@Test
	void poissonRequestWithNonNumberSeedIsRefused() throws Exception {
		final Engine engine = mock(Engine.class);
		when(engine.getTime()).thenReturn(0);
		final TimeSource ts = newTimeSource(engine, 2);
		ts.process(requestAlarmEvent(engine, ALARM, 2, TimeSource.POISSON, 1.0, "nope"));
		for (int t = 1; t <= 20; t++) {
			setTime(ts, t);
		}
		final ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
		verify(engine, atLeast(0)).signal(captor.capture());
		assertTrue(captor.getAllValues().stream().noneMatch(e -> ALARM.equals(e.getTopic())),
				"a refused request must register no alarm");
	}

	@Test
	void doublePeriodWithoutMarkerIsRefusedNotReinterpreted() throws Exception {
		final Engine engine = mock(Engine.class);
		when(engine.getTime()).thenReturn(0);
		final TimeSource ts = newTimeSource(engine, 2);
		assertDoesNotThrow(() -> ts.process(requestAlarmEvent(engine, ALARM, 3, 1.5)));
		assertDoesNotThrow(() -> ts.process(requestAlarmEvent(engine, "FAR", 3, 1.5, 9)));
		for (int t = 1; t <= 20; t++) {
			setTime(ts, t);
		}
		final ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
		verify(engine, atLeast(0)).signal(captor.capture());
		assertTrue(captor.getAllValues().stream()
				.noneMatch(e -> ALARM.equals(e.getTopic()) || "FAR".equals(e.getTopic())),
				"a malformed period must register no alarm");
	}

	@Test
	void poissonRequestWithNonNumberRateIsRefused() throws Exception {
		final Engine engine = mock(Engine.class);
		when(engine.getTime()).thenReturn(0);
		final TimeSource ts = newTimeSource(engine, 2);
		ts.process(requestAlarmEvent(engine, ALARM, 2, TimeSource.POISSON, "fast"));
		for (int t = 1; t <= 20; t++) {
			setTime(ts, t);
		}
		final ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
		verify(engine, atLeast(0)).signal(captor.capture());
		assertTrue(captor.getAllValues().stream().noneMatch(e -> ALARM.equals(e.getTopic())),
				"a refused request must register no alarm");
	}

	@Test
	void testEqualsAndHashCode() {
		final Engine engine = mock(Engine.class);
		when(engine.getTime()).thenReturn(0);
		final TimeSource ts1 = newTimeSource(engine, 200);
		final TimeSource ts2 = newTimeSource(engine, 200);
		assertEquals(ts1, ts1);
		assertNotEquals(ts1, null);
		assertNotEquals(ts1, new Object());
		assertNotEquals(ts1, ts2);
		assertEquals(ts1.hashCode(), ts1.hashCode());
	}

	@Test
	void testGetTimeAfterSetTime() throws Exception {
		final Engine engine = mock(Engine.class);
		when(engine.getTime()).thenReturn(0);
		final TimeSource ts = newTimeSource(engine, 200);
		assertEquals(0, ts.getTime());
		setTime(ts, 42);
		assertEquals(42, ts.getTime());
	}

	@Test
	void testProcessRequestAlarmWithPeriod() {
		final Engine engine = mock(Engine.class);
		when(engine.getTime()).thenReturn(0);
		final TimeSource ts = newTimeSource(engine, 2);
		ts.process(requestAlarmEvent(engine, ALARM, 3, 2));
	}

	@Test
	void testProcessRequestAlarmWithoutPeriod() {
		final Engine engine = mock(Engine.class);
		when(engine.getTime()).thenReturn(0);
		final TimeSource ts = newTimeSource(engine, 2);
		ts.process(requestAlarmEvent(engine, ALARM, 3));
	}

	@Test
	void testProcessClearAlarm() {
		final Engine engine = mock(Engine.class);
		when(engine.getTime()).thenReturn(0);
		final TimeSource ts = newTimeSource(engine, 2);
		ts.process(requestAlarmEvent(engine, ALARM, 3));
		ts.process(clearAlarmEvent(engine, ALARM));
	}

	@Test
	void testProcessUnexpectedEvent() {
		final Engine engine = mock(Engine.class);
		when(engine.getTime()).thenReturn(0);
		final TimeSource ts = newTimeSource(engine, 2);
		final Actor src = mock(Actor.class);
		when(src.getEngine()).thenReturn(engine);
		ts.process(EventImpl.createEvent("UNKNOWN", src));
	}

	@Test
	void testSetTimeFiresNonPeriodicAlarm() throws Exception {
		final Engine engine = mock(Engine.class);
		when(engine.getTime()).thenReturn(0);
		final TimeSource ts = newTimeSource(engine, 2);
		ts.process(requestAlarmEvent(engine, ALARM, 3));
		setTime(ts, 3);
		verify(engine, atLeast(2)).signal(any(Event.class));
	}

	@Test
	void testSetTimeFiresPeriodicAlarmAndUpdates() throws Exception {
		final Engine engine = mock(Engine.class);
		when(engine.getTime()).thenReturn(0);
		final TimeSource ts = newTimeSource(engine, 2);
		ts.process(requestAlarmEvent(engine, ALARM, 3, 2));
		setTime(ts, 3);
		setTime(ts, 3 + 4);
		verify(engine, atLeast(4)).signal(any(Event.class));
	}

	@Test
	void testSetTimeNoAlarmDue() throws Exception {
		final Engine engine = mock(Engine.class);
		when(engine.getTime()).thenReturn(0);
		final TimeSource ts = newTimeSource(engine, 2);
		ts.process(requestAlarmEvent(engine, ALARM, 5));
		setTime(ts, 3);
		verify(engine, atLeast(1)).signal(any(Event.class));
	}

	@Test
	void testRunStartThenStop() {
		final Engine engine = mock(Engine.class);
		when(engine.getTime()).thenReturn(0);
		final TimeSource ts = newTimeSource(engine, 200);
		ts.post(selfEvent(ts, Engine.START_EVENT));
		ts.post(selfEvent(ts, Engine.STOP_EVENT));
		ts.run();
		verify(engine, atLeast(1)).signal(any(Event.class));
	}

	@Test
	void testRunSignalsStartedEventForItself() {
		final Engine engine = mock(Engine.class);
		when(engine.getTime()).thenReturn(0);
		final TimeSource ts = newTimeSource(engine, 200);
		ts.post(selfEvent(ts, Engine.START_EVENT));
		ts.post(selfEvent(ts, Engine.STOP_EVENT));
		ts.run();
		final ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
		verify(engine, atLeast(1)).signal(captor.capture());
		assertTrue(captor.getAllValues().stream()
				.anyMatch(e -> Engine.STARTED_ACTOR_EVENT.equals(e.getTopic()) && ts.equals(e.getSource())));
	}

	@Test
	void testRunStopBeforeStart() {
		final Engine engine = mock(Engine.class);
		when(engine.getTime()).thenReturn(0);
		final TimeSource ts = newTimeSource(engine, 200);
		ts.post(selfEvent(ts, Engine.STOP_EVENT));
		ts.run();
	}

	@Test
	void testRunStopMeSelfBeforeStart() {
		final Engine engine = mock(Engine.class);
		when(engine.getTime()).thenReturn(0);
		final TimeSource ts = newTimeSource(engine, 200);
		ts.post(selfEvent(ts, Engine.STOP_ME_EVENT));
		ts.run();
	}

	@Test
	void testRunStopMeOtherBeforeStartThenStop() {
		final Engine engine = mock(Engine.class);
		when(engine.getTime()).thenReturn(0);
		final TimeSource ts = newTimeSource(engine, 200);
		ts.post(otherEvent(ts, Engine.STOP_ME_EVENT));
		ts.post(selfEvent(ts, Engine.STOP_EVENT));
		ts.run();
	}

	@Test
	void testRunGenericEventBeforeStartThenStop() {
		final Engine engine = mock(Engine.class);
		when(engine.getTime()).thenReturn(0);
		final TimeSource ts = newTimeSource(engine, 200);
		ts.post(otherEvent(ts, "GENERIC"));
		ts.post(selfEvent(ts, Engine.STOP_EVENT));
		ts.run();
	}

	@Test
	void testRunAfterStartGenericEventThenStop() {
		final Engine engine = mock(Engine.class);
		when(engine.getTime()).thenReturn(0);
		final TimeSource ts = newTimeSource(engine, 200);
		ts.post(selfEvent(ts, Engine.START_EVENT));
		ts.post(otherEvent(ts, "GENERIC"));
		ts.post(selfEvent(ts, Engine.STOP_EVENT));
		ts.run();
		verify(engine, atLeast(1)).signal(any(Event.class));
	}

	@Test
	void testRunAfterStartProcessesStopMeAsGeneric() {
		final Engine engine = mock(Engine.class);
		when(engine.getTime()).thenReturn(0);
		final TimeSource ts = newTimeSource(engine, 200);
		ts.post(selfEvent(ts, Engine.START_EVENT));
		ts.post(selfEvent(ts, Engine.STOP_ME_EVENT));
		ts.post(selfEvent(ts, Engine.STOP_EVENT));
		ts.run();
		verify(engine, atLeast(1)).signal(any(Event.class));
	}

	@Test
	void testPurgeEvents() {
		final Engine engine = mock(Engine.class);
		when(engine.getTime()).thenReturn(0);
		final TimeSource ts = newTimeSource(engine, 200);
		ts.post(selfEvent(ts, Engine.STOP_EVENT));
		ts.purgeEvents();
		ts.post(selfEvent(ts, Engine.STOP_EVENT));
		ts.run();
	}

	@Test
	void testPostAfterRunCompletionIsDropped() throws Exception {
		final Engine engine = mock(Engine.class);
		when(engine.getTime()).thenReturn(0);
		final TimeSource ts = newTimeSource(engine, 200);
		ts.post(selfEvent(ts, Engine.STOP_EVENT));
		ts.run();
		ts.post(mock(Event.class));
		final Field field = TimeSource.class.getDeclaredField("events");
		field.setAccessible(true);
		assertTrue(((BlockingQueue<?>) field.get(ts)).isEmpty(),
				"a stopped time source must drop late events and keep its queue empty");
	}

	@Test
	void testPostRetriesWhenQueueOfferFails() throws Exception {
		final Engine engine = mock(Engine.class);
		when(engine.getTime()).thenReturn(0);
		final TimeSource ts = newTimeSource(engine, 200);
		final BlockingQueue<Event> mockQueue = mock(LinkedBlockingQueue.class);
		when(mockQueue.offer(any())).thenReturn(false, true);
		setQueue(ts, mockQueue);
		ts.post(mock(Event.class));
	}

	@Test
	void testRunBeforeStartHandlesNullAndInterrupt() throws Exception {
		final Engine engine = mock(Engine.class);
		when(engine.getTime()).thenReturn(0);
		final TimeSource ts = newTimeSource(engine, 200);
		final Event stop = selfEvent(ts, Engine.STOP_EVENT);
		final BlockingQueue<Event> mockQueue = mock(LinkedBlockingQueue.class);
		when(mockQueue.offer(any())).thenReturn(true);
		when(mockQueue.poll(anyLong(), any(TimeUnit.class))).thenReturn(null)
				.thenThrow(new InterruptedException()).thenReturn(stop);
		setQueue(ts, mockQueue);
		ts.run();
		Thread.interrupted();
	}

	@Test
	void testRunAfterStartHandlesNullAndInterrupt() throws Exception {
		final Engine engine = mock(Engine.class);
		when(engine.getTime()).thenReturn(0);
		final TimeSource ts = newTimeSource(engine, 200);
		final Event start = selfEvent(ts, Engine.START_EVENT);
		final Event stop = selfEvent(ts, Engine.STOP_EVENT);
		final BlockingQueue<Event> mockQueue = mock(LinkedBlockingQueue.class);
		when(mockQueue.offer(any())).thenReturn(true);
		when(mockQueue.poll(anyLong(), any(TimeUnit.class))).thenReturn(start).thenReturn(null)
				.thenThrow(new InterruptedException()).thenReturn(stop);
		setQueue(ts, mockQueue);
		ts.run();
		Thread.interrupted();
		verify(engine, atLeast(1)).signal(any(Event.class));
	}

	@Test
	void testRunRethrowsUnexpectedThrowable() throws Exception {
		final Engine engine = mock(Engine.class);
		when(engine.getTime()).thenReturn(0);
		final TimeSource ts = newTimeSource(engine, 200);
		final BlockingQueue<Event> mockQueue = mock(LinkedBlockingQueue.class);
		when(mockQueue.offer(any())).thenReturn(true);
		when(mockQueue.poll(anyLong(), any(TimeUnit.class))).thenThrow(new RuntimeException("boom"));
		setQueue(ts, mockQueue);
		assertThrows(RuntimeException.class, ts::run);
		verify(engine, atLeast(1)).signal(any(Event.class));
	}

	/**
	 * Replaces the internal events queue of the given TimeSource.
	 *
	 * @param ts    the time source to modify
	 * @param queue the queue to install
	 * @throws Exception if reflection fails
	 */
	private void setQueue(final TimeSource ts, final BlockingQueue<Event> queue) throws Exception {
		final Field field = TimeSource.class.getDeclaredField("events");
		field.setAccessible(true);
		field.set(ts, queue);
	}

}
