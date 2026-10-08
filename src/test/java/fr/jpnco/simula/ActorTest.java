package fr.jpnco.simula;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import fr.jpnco.simula.engine.ActorDelegate;
import fr.jpnco.simula.engine.EngineImpl;
import fr.jpnco.simula.engine.EventImpl;
import fr.jpnco.simula.engine.IdBuilder;

/**
 * Tests the default methods of the {@link Actor} interface: compareTo,
 * getEngine, getName, getSimpleName, post, purgeEvents, run, stopMe, subscribe,
 * afterStart and beforeStop.
 */
class ActorTest {

	private class AnActor implements Actor {

		private final Integer id = IdBuilder.nextId();

		AnActor() {
			when(delegate.getEngine()).thenReturn(engine);
			when(engine.getName()).thenReturn(ENGINE);
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
		public void process(final Event event) {
			// TODO Auto-generated method stub

		}

	}

	private static final String ENGINE = "Engine4Test";

	@BeforeAll
	static void setUpBeforeClass() throws Exception {
	}

	@AfterAll
	static void tearDownAfterClass() throws Exception {
	}

	private final ActorDelegate delegate = mock(ActorDelegate.class);

	private final EngineImpl engine = mock(EngineImpl.class);

	@BeforeEach
	void setUp() throws Exception {
	}

	@AfterEach
	void tearDown() throws Exception {
	}

	@Test
	void testCompareTo() {
		final Actor actor1 = new AnActor();
		final Actor actor2 = new AnActor();
		Assertions.assertTrue(actor1.compareTo(actor2) > 0);
		Assertions.assertTrue(actor2.compareTo(actor1) < 0);
		Assertions.assertTrue(actor1.compareTo(actor1) == 0);
		Assertions.assertTrue(actor2.compareTo(actor2) == 0);
	}

	@Test
	void testGetEngine() {
		assertEquals(engine, new AnActor().getEngine());
		verify(delegate).getEngine();
	}

	@Test
	void testGetName() {
		assertEquals(AnActor.class.getSimpleName() + ":" + ENGINE, new AnActor().getName());
	}

	@Test
	void testGetSimpleName() {
		assertEquals(AnActor.class.getSimpleName(), new AnActor().getSimpleName());
	}

	@Test
	void testPost() {
		final Event event = mock(Event.class);
		new AnActor().post(event);
		verify(delegate).post(event);
	}

	@Test
	void testPurgeEvents() {
		new AnActor().purgeEvents();
		verify(delegate).purgeEvents();
	}

	@Test
	void testStopMe() {
		final Actor actor = new AnActor();
		final int TIME = 999;
		when(engine.getTime()).thenReturn(TIME);
		actor.stopMe();
		verify(engine).signal(Mockito.any(EventImpl.class));
	}

	@Test
	void testSubscribe() {
		final String TOPIC = "TOPIC";
		final Actor actor = new AnActor();
		actor.subscribe(TOPIC);
		verify(engine).subscribe(actor, TOPIC);
	}

	@Test
	void testAfterStartDefaultDoesNothing() {
		final Actor actor = new AnActor();
		actor.afterStart();
		verify(delegate, Mockito.never()).run();
	}

	@Test
	void testBeforeStopDefaultDoesNothing() {
		final Actor actor = new AnActor();
		actor.beforeStop();
		verify(delegate, Mockito.never()).run();
	}

	@Test
	void testRunDelegatesToDelegate() {
		final Actor actor = new AnActor();
		actor.run();
		verify(delegate).run();
	}

	@Test
	void testRunRethrowsDelegationError() {
		final Actor actor = new AnActor();
		doThrow(new RuntimeException("boom")).when(delegate).run();
		assertThrows(RuntimeException.class, actor::run);
		verify(engine).unregister(actor);
	}

}
