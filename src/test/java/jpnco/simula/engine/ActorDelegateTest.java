package jpnco.simula.engine;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jpnco.simula.Actor;
import jpnco.simula.Engine;
import jpnco.simula.Event;
import jpnco.simula.actors.Logger;
import jpnco.simula.actors.Logger.Level;

class ActorDelegateTest {

	@BeforeAll
	static void setUpBeforeClass() throws Exception {
	}

	@AfterAll
	static void tearDownAfterClass() throws Exception {
	}

	private Actor delegator;
	private Engine engine;
	private Actor delegate;

	@Test
	void createDelayedDelegate() {
		final Actor actor = ActorDelegate.createDelayedDelegate(engine, delegator);
		assertNotNull(actor);
		assertThrows(UnsupportedOperationException.class, () -> {
			actor.getDelegate();
		});
	}

	@Test
	void createDelegate() {
		final Actor actor = ActorDelegate.createDelegate(engine, delegator);
		assertNotNull(actor);
		assertThrows(UnsupportedOperationException.class, () -> {
			actor.getDelegate();
		});
	}

	@Test
	void createPrioritizedDelegate() {
		final Actor actor = ActorDelegate.createPrioritizedDelegate(engine, delegator);
		assertNotNull(actor);
		assertThrows(UnsupportedOperationException.class, () -> {
			actor.getDelegate();
		});
	}

	@Test
	void postAddsEventToQueue() {
		final Event event = mock(Event.class);
		delegate.post(event);
		assertFalse(((ActorDelegate) delegate).getQueue().isEmpty());
	}

	@Test
	void purgeEventsClearsQueue() {
		final Event event = mock(Event.class);
		delegate.post(event);
		assertFalse(((ActorDelegate) delegate).getQueue().isEmpty());
		delegate.purgeEvents();
		assertTrue(((ActorDelegate) delegate).getQueue().isEmpty());
	}

	@Test
	void runProcessesStartEvent() {
		final Event startEvent = mock(Event.class);
		when(startEvent.getTopic()).thenReturn(Engine.START_EVENT);
		when(startEvent.getSource()).thenReturn(delegate);
		final Event stopEvent = mock(Event.class);
		when(stopEvent.getTopic()).thenReturn(Engine.STOP_EVENT);
		when(stopEvent.getSource()).thenReturn(delegate);
		delegate.post(startEvent);
		// must post STOP_EVENT to exit run()
		delegate.post(stopEvent);
		delegate.run();
		verify(delegator).afterStart();
		verify(delegator).beforeStop();
	}

	@Test
	void runProcessesStopEvent() {
		final Event stopEvent = mock(Event.class);
		when(stopEvent.getTopic()).thenReturn(Engine.STOP_EVENT);
		when(stopEvent.getSource()).thenReturn(delegate);
		delegate.post(stopEvent);
		delegate.run();
		verify(delegator).beforeStop();
	}

	@Test
	void runProcessesStopMeEvent() {
		final Event stopEvent = mock(Event.class);
		when(stopEvent.getTopic()).thenReturn(Engine.STOP_ME_EVENT);
		// the source must be the delegator
		when(stopEvent.getSource()).thenReturn(delegator);
		delegate.post(stopEvent);
		delegate.run();
	}

	@BeforeEach
	void setUp() throws Exception {
		engine = mock(Engine.class);
		when(engine.getName()).thenReturn("engine");
		Logger.setActivated(engine, Level.TRACE, true);
		delegator = mock(Actor.class);
		when(delegator.getName()).thenReturn("delegator:engine");
		when(delegator.getSimpleName()).thenReturn("delegator");
		when(delegator.getEngine()).thenReturn(engine);
		delegate = ActorDelegate.createDelegate(engine, delegator);
		Logger.setActivated(delegator, Level.TRACE, true);
		Logger.setActivated(delegate, Level.TRACE, true);

	}

	@AfterEach
	void tearDown() throws Exception {
		engine.stop();
	}

}
