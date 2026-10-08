package fr.jpnco.simula.actors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.jpnco.simula.Actor;
import fr.jpnco.simula.Engine;
import fr.jpnco.simula.Event;
import fr.jpnco.simula.engine.EventImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Tests for the {@link Barrier} actor: construction, counting and firing (FR-002..FR-004),
 * single-use deactivation (FR-005), cyclic reset (FR-006), distinct counting (FR-007, FR-010),
 * source identification (FR-010) and constructor validation (FR-008, FR-009).
 */
class BarrierTest {

  private static final String READY_TOPIC = "READY";
  private static final String COMPLETE_TOPIC = "COMPLETE";

  private Engine engine;

  @BeforeEach
  void setUp() {
    engine = mock(Engine.class);
    when(engine.getTime()).thenReturn(0);
  }

  private Actor source(final String name) {
    final Actor src = mock(Actor.class);
    when(src.getEngine()).thenReturn(engine);
    when(src.getName()).thenReturn(name);
    when(src.getSimpleName()).thenReturn(name);
    return src;
  }

  private Event readyEvent(final Actor src) {
    return EventImpl.createEvent(READY_TOPIC, src);
  }

  private Barrier newBarrier(
      final int participants, final BarrierMode mode, final boolean distinct) {
    return new Barrier(engine, participants, READY_TOPIC, COMPLETE_TOPIC, mode, distinct);
  }

  private Event captureSignaled(final int index) {
    @SuppressWarnings("unchecked")
    final ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
    verify(engine, atLeastOnce()).signal(captor.capture());
    return captor.getAllValues().get(index);
  }

  // ---------- Constructor validation (FR-008, FR-009, SC-005) ----------

  @Test
  void constructorSubscribesToReadyTopic() {
    newBarrier(2, BarrierMode.SINGLE_USE, false);
    verify(engine).subscribe(any(Actor.class), eq(READY_TOPIC));
  }

  @Test
  void constructorRejectsNonPositiveParticipants() {
    assertThrows(
        IllegalArgumentException.class, () -> newBarrier(0, BarrierMode.SINGLE_USE, false));
    assertThrows(
        IllegalArgumentException.class, () -> newBarrier(-1, BarrierMode.SINGLE_USE, false));
  }

  @Test
  void constructorRejectsNullMode() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new Barrier(engine, 1, READY_TOPIC, COMPLETE_TOPIC, null, false));
  }

  @Test
  void constructorRejectsBlankReadyTopic() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new Barrier(engine, 1, " ", COMPLETE_TOPIC, BarrierMode.SINGLE_USE, false));
  }

  @Test
  void constructorRejectsBlankCompleteTopic() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new Barrier(engine, 1, READY_TOPIC, "", BarrierMode.SINGLE_USE, false));
  }

  // ---------- Identity (getId, getDelegate, equals, hashCode) ----------

  @Test
  void getIdReturnsUniqueId() {
    final Barrier b = newBarrier(1, BarrierMode.SINGLE_USE, false);
    assertNotNull(b.getId());
    final Barrier other = newBarrier(1, BarrierMode.SINGLE_USE, false);
    assertNotEquals(b.getId(), other.getId());
  }

  @Test
  void getDelegateReturnsNonNullDelegate() {
    final Barrier b = newBarrier(1, BarrierMode.SINGLE_USE, false);
    assertNotNull(b.getDelegate());
  }

  @Test
  void equalsIsIdentityBasedOnId() {
    final Barrier b = newBarrier(1, BarrierMode.SINGLE_USE, false);
    assertEquals(b, b);
    assertNotEquals(b, newBarrier(1, BarrierMode.SINGLE_USE, false));
    assertNotEquals(b, null);
  }

  @Test
  void hashCodeIsStable() {
    final Barrier b = newBarrier(1, BarrierMode.SINGLE_USE, false);
    assertEquals(b.hashCode(), b.hashCode());
  }

  // ---------- US1: counting and firing (FR-002..FR-004) ----------

  @Test
  void firesCompleteTopicWhenParticipantsReached() {
    final Barrier barrier = newBarrier(2, BarrierMode.SINGLE_USE, false);
    barrier.process(readyEvent(source("a")));
    barrier.process(readyEvent(source("b")));
    final Event fired = captureSignaled(0);
    assertEquals(COMPLETE_TOPIC, fired.getTopic());
    assertEquals(barrier, fired.getSource());
  }

  @Test
  void doesNotFireBeforeParticipantsReached() {
    final Barrier barrier = newBarrier(2, BarrierMode.SINGLE_USE, false);
    barrier.process(readyEvent(source("a")));
    verify(engine, never()).signal(any(Event.class));
  }

  @Test
  void completeEventSourceIsTheBarrier() {
    final Barrier barrier = newBarrier(1, BarrierMode.SINGLE_USE, false);
    barrier.process(readyEvent(source("a")));
    final Event fired = captureSignaled(0);
    assertEquals(barrier, fired.getSource());
  }

  // ---------- US2: single-use deactivation (FR-005, SC-002) ----------

  @Test
  void singleUseUnsubscribesAfterFiring() {
    final Barrier barrier = newBarrier(1, BarrierMode.SINGLE_USE, false);
    barrier.process(readyEvent(source("a")));
    verify(engine).unsubscribe(barrier, READY_TOPIC);
  }

  @Test
  void singleUseDoesNotFireAgain() {
    final Barrier barrier = newBarrier(1, BarrierMode.SINGLE_USE, false);
    barrier.process(readyEvent(source("a")));
    barrier.process(readyEvent(source("b")));
    verify(engine, times(1)).signal(any(Event.class));
  }

  // ---------- US3: cyclic reset (FR-006, SC-003) ----------

  @Test
  void cyclicResetsAndFiresAgain() {
    final Barrier barrier = newBarrier(1, BarrierMode.CYCLIC, false);
    barrier.process(readyEvent(source("a")));
    barrier.process(readyEvent(source("b")));
    verify(engine, times(2)).signal(any(Event.class));
  }

  @Test
  void cyclicDoesNotUnsubscribe() {
    final Barrier barrier = newBarrier(1, BarrierMode.CYCLIC, false);
    barrier.process(readyEvent(source("a")));
    verify(engine, never()).unsubscribe(any(Actor.class), any(String.class));
  }

  // ---------- US4: distinct counting (FR-007, FR-010, SC-004) ----------

  @Test
  void distinctCountsSameSourceOnce() {
    final Barrier barrier = newBarrier(2, BarrierMode.SINGLE_USE, true);
    final Actor a = source("a");
    barrier.process(readyEvent(a));
    barrier.process(readyEvent(a));
    verify(engine, never()).signal(any(Event.class));
  }

  @Test
  void distinctFiresWithDistinctSources() {
    final Barrier barrier = newBarrier(2, BarrierMode.SINGLE_USE, true);
    barrier.process(readyEvent(source("a")));
    barrier.process(readyEvent(source("b")));
    verify(engine, times(1)).signal(any(Event.class));
  }

  @Test
  void nonDistinctCountsEverySignal() {
    final Barrier barrier = newBarrier(2, BarrierMode.SINGLE_USE, false);
    final Actor a = source("a");
    barrier.process(readyEvent(a));
    barrier.process(readyEvent(a));
    verify(engine, times(1)).signal(any(Event.class));
  }

  @Test
  void sourceOfReadyEventIsTheParticipant() {
    final Actor participant = source("participant");
    final Barrier barrier = newBarrier(1, BarrierMode.SINGLE_USE, false);
    barrier.process(readyEvent(participant));
    assertEquals(participant, readyEvent(participant).getSource());
  }

  @Test
  void getIdIsNonNegative() {
    final Barrier b = newBarrier(1, BarrierMode.SINGLE_USE, false);
    assertTrue(b.getId() >= 0);
  }

  // ---------- Additional branch coverage ----------

  @Test
  void processIgnoresEventsOnOtherTopics() {
    final Barrier barrier = newBarrier(1, BarrierMode.SINGLE_USE, false);
    final Event other = EventImpl.createEvent("OTHER", source("a"));
    barrier.process(other);
    verify(engine, never()).signal(any(Event.class));
  }

  @Test
  void processIgnoresNullEvent() {
    final Barrier barrier = newBarrier(1, BarrierMode.SINGLE_USE, false);
    assertThrows(NullPointerException.class, () -> barrier.process(null));
  }

  @Test
  void equalsDifferentClassReturnsFalse() {
    final Barrier b = newBarrier(1, BarrierMode.SINGLE_USE, false);
    assertNotEquals(b, new Object());
  }

  @Test
  void constructorRejectsNullReadyTopic() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new Barrier(engine, 1, null, COMPLETE_TOPIC, BarrierMode.SINGLE_USE, false));
  }

  @Test
  void constructorRejectsNullCompleteTopic() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new Barrier(engine, 1, READY_TOPIC, null, BarrierMode.SINGLE_USE, false));
  }
}
