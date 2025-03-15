package jpnco.simula.actors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jpnco.simula.Actor;
import jpnco.simula.Engine;
import jpnco.simula.Event;
import jpnco.simula.actors.Logger.Level;
import jpnco.simula.engine.ActorDelegate;
import jpnco.simula.engine.EngineImpl;
import jpnco.simula.engine.IdBuilder;

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
	}

	@Test
	void testConstructor() {
		final int TIME_FACTOR = 200;
		final Engine engine = mock(Engine.class);
		final TimeSource ts = new TimeSource(engine, TIME_FACTOR);
		assertEquals(engine, ts.getEngine());
		assertThrows(UnsupportedOperationException.class, () -> {
			ts.getDelegate();
		});

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

}
