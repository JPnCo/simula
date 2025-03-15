package jpnco.simula.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

class EventImplTest {

	@BeforeAll
	static void setUpBeforeClass() throws Exception {
	}

	@AfterAll
	static void tearDownAfterClass() throws Exception {
	}

	private final String TOPIC = "TOPIC";

	private final Engine ENGINE = mock(Engine.class);

	private final Actor SOURCE = mock(Actor.class);

	@BeforeEach
	void setUp() throws Exception {
		when(SOURCE.getEngine()).thenReturn(ENGINE);
		when(ENGINE.getTime()).thenReturn(999);
	}

	@AfterEach
	void tearDown() throws Exception {
	}

	@Test
	void testCreateDelayedEvent() {
		final Event event = EventImpl.createDelayedEvent(TOPIC, 300L, SOURCE, 1, 2, 3);
		assertEquals(TOPIC, event.getTopic());
		assertEquals(SOURCE, event.getSource());
		assertEquals(999, event.getTime());
		assertTrue(event.isDelayed());
		assertFalse(event.isPrioritized());
		assertEquals(1, (Integer) event.getParameters()[0]);
		assertEquals(2, (Integer) event.getParameters()[1]);
		assertEquals(3, (Integer) event.getParameters()[2]);
	}

	@Test
	void testCreateEvent() {
		final Event event = EventImpl.createEvent(TOPIC, SOURCE, 1, 2, 3);
		assertEquals(TOPIC, event.getTopic());
		assertEquals(SOURCE, event.getSource());
		assertEquals(999, event.getTime());
		assertFalse(event.isDelayed());
		assertFalse(event.isPrioritized());
		assertEquals(1, (Integer) event.getParameters()[0]);
		assertEquals(2, (Integer) event.getParameters()[1]);
		assertEquals(3, (Integer) event.getParameters()[2]);
	}

	@Test
	void testCreatePriotityEvent() {
		final Event event = EventImpl.createPriorityEvent(TOPIC, 32, SOURCE, 1, 2, 3);
		assertEquals(TOPIC, event.getTopic());
		assertEquals(SOURCE, event.getSource());
		assertEquals(999, event.getTime());
		assertEquals(32, event.getPriority());
		assertFalse(event.isDelayed());
		assertTrue(event.isPrioritized());
		assertEquals(1, (Integer) event.getParameters()[0]);
		assertEquals(2, (Integer) event.getParameters()[1]);
		assertEquals(3, (Integer) event.getParameters()[2]);
	}

	@Test
	void testDuplicateEvent() {
		final Actor NEW_SOURCE = mock(Actor.class);
		final Event e1 = EventImpl.createEvent(TOPIC, SOURCE, 1, 2, 3);
		final Event e2 = e1.duplicate(NEW_SOURCE);
		assertEquals(e1.getTopic(), e2.getTopic());
		assertEquals(NEW_SOURCE, e2.getSource());
		assertEquals(e1.getTime(), e2.getTime());
		assertEquals(e1.isDelayed(), e2.isDelayed());
		assertEquals(e1.isPrioritized(), e2.isPrioritized());
		assertEquals(e1.getParameters()[0], e2.getParameters()[0]);
		assertEquals(e1.getParameters()[1], e2.getParameters()[1]);
		assertEquals(e1.getParameters()[2], e2.getParameters()[2]);
	}

	@Test
	void testDuplicatePriorityEvent() {
		final Actor NEW_SOURCE = mock(Actor.class);
		final Event e1 = EventImpl.createPriorityEvent(TOPIC, 128, SOURCE, 1, 2, 3);
		final Event e2 = e1.duplicate(NEW_SOURCE);
		assertEquals(e1.getTopic(), e2.getTopic());
		assertEquals(NEW_SOURCE, e2.getSource());
		assertEquals(e1.getTime(), e2.getTime());
		assertEquals(e1.isDelayed(), e2.isDelayed());
		assertEquals(e1.isPrioritized(), e2.isPrioritized());
		assertEquals(e1.getPriority(), e2.getPriority());
		assertEquals(e1.getParameters()[0], e2.getParameters()[0]);
		assertEquals(e1.getParameters()[1], e2.getParameters()[1]);
		assertEquals(e1.getParameters()[2], e2.getParameters()[2]);
	}

}
