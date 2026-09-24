package jpnco.simula.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jpnco.simula.Actor;
import jpnco.simula.Engine;
import jpnco.simula.Event;

/**
 * Tests for EventImpl covering event creation, ordering, duplication, equality
 * and string representation (FR-006, FR-007).
 */
class EventImplTest {

	private final String TOPIC = "TOPIC";

	private final Engine ENGINE = mock(Engine.class);

	private final Actor SOURCE = mock(Actor.class);

	@BeforeEach
	void setUp() {
		when(SOURCE.getEngine()).thenReturn(ENGINE);
		when(ENGINE.getTime()).thenReturn(999);
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
	void testCreateDelayedEventWithNegativeDelayRejected() {
		assertThrows(UnsupportedOperationException.class, () -> EventImpl.createDelayedEvent(TOPIC, -1L, SOURCE));
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
	void testCreatePriorityEventWithNegativePriorityRejected() {
		assertThrows(UnsupportedOperationException.class, () -> EventImpl.createPriorityEvent(TOPIC, -1, SOURCE));
	}

	@Test
	void testDuplicateEvent() {
		final Actor newSource = mock(Actor.class);
		final Event e1 = EventImpl.createEvent(TOPIC, SOURCE, 1, 2, 3);
		final Event e2 = e1.duplicate(newSource);
		assertEquals(e1.getTopic(), e2.getTopic());
		assertEquals(newSource, e2.getSource());
		assertEquals(e1.getTime(), e2.getTime());
		assertEquals(e1.isDelayed(), e2.isDelayed());
		assertEquals(e1.isPrioritized(), e2.isPrioritized());
		assertEquals(e1.getParameters()[0], e2.getParameters()[0]);
		assertEquals(e1.getParameters()[1], e2.getParameters()[1]);
		assertEquals(e1.getParameters()[2], e2.getParameters()[2]);
	}

	@Test
	void testDuplicatePriorityEvent() {
		final Actor newSource = mock(Actor.class);
		final Event e1 = EventImpl.createPriorityEvent(TOPIC, 128, SOURCE, 1, 2, 3);
		final Event e2 = e1.duplicate(newSource);
		assertEquals(e1.getTopic(), e2.getTopic());
		assertEquals(newSource, e2.getSource());
		assertEquals(e1.getTime(), e2.getTime());
		assertEquals(e1.isDelayed(), e2.isDelayed());
		assertEquals(e1.isPrioritized(), e2.isPrioritized());
		assertEquals(e1.getPriority(), e2.getPriority());
		assertEquals(e1.getParameters()[0], e2.getParameters()[0]);
		assertEquals(e1.getParameters()[1], e2.getParameters()[1]);
		assertEquals(e1.getParameters()[2], e2.getParameters()[2]);
	}

	@Test
	void testGetParametersReturnsCopy() {
		final Event event = EventImpl.createEvent(TOPIC, SOURCE, 1, 2);
		final Object[] params = event.getParameters();
		params[0] = "mutated";
		assertEquals(1, (Integer) event.getParameters()[0], "returned array must be a defensive copy");
		assertNotSame(params, event.getParameters());
	}

	@Test
	void testStandardEventComparesEqualToAnotherStandard() {
		final Event e1 = EventImpl.createEvent(TOPIC, SOURCE, 1);
		final Event e2 = EventImpl.createEvent(TOPIC, SOURCE, 1);
		assertEquals(0, e1.compareTo(e2));
	}

	@Test
	void testDelayedEventComparesByDelay() {
		final Event near = EventImpl.createDelayedEvent(TOPIC, 10L, SOURCE);
		final Event far = EventImpl.createDelayedEvent(TOPIC, 500L, SOURCE);
		assertTrue(near.compareTo(far) < 0, "nearer delayed event should sort before farther one");
	}

	@Test
	void testDelayedEventGetDelayApproximately() {
		final Event event = EventImpl.createDelayedEvent(TOPIC, 1000L, SOURCE);
		assertTrue(event.getDelay(TimeUnit.MILLISECONDS) > 0);
		assertTrue(event.getDelay(TimeUnit.MILLISECONDS) <= 1000);
	}

	@Test
	void testPriorityEventComparesByPriority() {
		final Event low = EventImpl.createPriorityEvent(TOPIC, 1, SOURCE);
		final Event high = EventImpl.createPriorityEvent(TOPIC, 5, SOURCE);
		assertTrue(low.compareTo(high) < 0, "lower priority value should sort first");
		assertEquals(0, low.compareTo(low));
	}

	@Test
	void testEqualsSymmetryAndHashCode() {
		final Event e1 = EventImpl.createEvent(TOPIC, SOURCE, 1, 2);
		final Event e2 = EventImpl.createEvent(TOPIC, SOURCE, 1, 2);
		final Event different = EventImpl.createEvent("OTHER", SOURCE, 1, 2);
		assertEquals(e1, e1);
		assertEquals(e1, e2);
		assertEquals(e1.hashCode(), e2.hashCode());
		assertNotEquals(e1, null);
		assertNotEquals(e1, new Object());
		assertNotEquals(e1, different);
	}

	@Test
	void testEqualsFalseWhenDelayDiffers() {
		final Event near = EventImpl.createDelayedEvent(TOPIC, 10L, SOURCE);
		final Event far = EventImpl.createDelayedEvent(TOPIC, 20L, SOURCE);
		assertNotEquals(near, far);
	}

	@Test
	void testEqualsFalseWhenDelayedFlagDiffers() {
		final Event delayed = EventImpl.createDelayedEvent(TOPIC, 0L, SOURCE);
		final Event standard = EventImpl.createEvent(TOPIC, SOURCE);
		assertNotEquals(delayed, standard);
	}

	@Test
	void testEqualsFalseWhenPrioritizedFlagDiffers() {
		final Event prioritized = EventImpl.createPriorityEvent(TOPIC, 0, SOURCE);
		final Event standard = EventImpl.createEvent(TOPIC, SOURCE);
		assertNotEquals(prioritized, standard);
	}

	@Test
	void testEqualsFalseWhenParametersDiffer() {
		final Event e1 = EventImpl.createEvent(TOPIC, SOURCE, 1);
		final Event e2 = EventImpl.createEvent(TOPIC, SOURCE, 2);
		assertNotEquals(e1, e2);
	}

	@Test
	void testEqualsFalseWhenPriorityDiffers() {
		final Event e1 = EventImpl.createPriorityEvent(TOPIC, 1, SOURCE);
		final Event e2 = EventImpl.createPriorityEvent(TOPIC, 2, SOURCE);
		assertNotEquals(e1, e2);
	}

	@Test
	void testEqualsFalseWhenSourceDiffers() {
		final Actor otherSource = mock(Actor.class);
		when(otherSource.getEngine()).thenReturn(ENGINE);
		final Event e1 = EventImpl.createEvent(TOPIC, SOURCE);
		final Event e2 = EventImpl.createEvent(TOPIC, otherSource);
		assertNotEquals(e1, e2);
	}

	@Test
	void testEqualsFalseWhenTimeDiffers() {
		when(SOURCE.getEngine().getTime()).thenReturn(999, 1000);
		final Event e1 = EventImpl.createEvent(TOPIC, SOURCE);
		final Event e2 = EventImpl.createEvent(TOPIC, SOURCE);
		assertEquals(999, e1.getTime());
		assertEquals(1000, e2.getTime());
		assertNotEquals(e1, e2);
	}

	@Test
	void testEqualsWithDelayedEventWithSameValues() {
		final Event e1 = EventImpl.createDelayedEvent(TOPIC, 50L, SOURCE, 1, 2);
		final Event e2 = EventImpl.createDelayedEvent(TOPIC, 50L, SOURCE, 1, 2);
		assertEquals(e1, e2);
		assertEquals(e1.hashCode(), e2.hashCode());
	}

	@Test
	void testToStringVariants() {
		final Event standard = EventImpl.createEvent(TOPIC, SOURCE);
		assertTrue(standard.toString().contains("Event"));
		final Event delayed = EventImpl.createDelayedEvent(TOPIC, 100L, SOURCE);
		assertTrue(delayed.toString().contains("delay"));
		final Event prioritized = EventImpl.createPriorityEvent(TOPIC, 7, SOURCE);
		assertTrue(prioritized.toString().contains("priority"));
	}

	@Test
	void testIsDelayedAndIsPrioritizedCombinations() {
		assertFalse(EventImpl.createEvent(TOPIC, SOURCE).isDelayed());
		assertFalse(EventImpl.createEvent(TOPIC, SOURCE).isPrioritized());
		assertTrue(EventImpl.createPriorityEvent(TOPIC, 3, SOURCE).isPrioritized());
		assertTrue(EventImpl.createDelayedEvent(TOPIC, 3L, SOURCE).isDelayed());
	}

	@Test
	void testPriorityIsZeroForStandardEvent() {
		assertEquals(0, EventImpl.createEvent(TOPIC, SOURCE).getPriority());
	}
}
