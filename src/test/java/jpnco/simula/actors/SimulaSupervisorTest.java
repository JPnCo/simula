package jpnco.simula.actors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import jpnco.simula.Actor;
import jpnco.simula.Engine;
import jpnco.simula.Event;
import jpnco.simula.actors.SimulaSupervisor.Status;
import jpnco.simula.engine.EventImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for the {@link SimulaSupervisor}: construction and subscription (FR-002), startup
 * observation (FR-001, FR-005), stop observation (FR-003, FR-004), queryable state (FR-006, FR-009,
 * FR-010) and the documented external-actor contract (FR-007).
 */
class SimulaSupervisorTest {

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

  private Event lifecycleEvent(final Actor source, final String topic) {
    return EventImpl.createEvent(topic, source);
  }

  // ---------- Construction and subscription (FR-002) ----------

  @Test
  void constructorRejectsNullEngine() {
    assertThrows(NullPointerException.class, () -> new SimulaSupervisor(null));
  }

  @Test
  void constructorSubscribesToLifecycleEvents() {
    new SimulaSupervisor(engine);
    verify(engine).subscribe(any(Actor.class), eq(Engine.STARTED_ACTOR_EVENT));
    verify(engine).subscribe(any(Actor.class), eq(Engine.STOPPED_ACTOR_EVENT));
    verify(engine).subscribe(any(Actor.class), eq(Engine.STOPPED_ENGINE_EVENT));
  }

  @Test
  void constructorBuildsDelegateAndId() {
    final SimulaSupervisor s = new SimulaSupervisor(engine);
    assertNotNull(s.getDelegate());
    assertNotNull(s.getId());
  }

  @Test
  void equalsAndHashCodeAreIdentityBasedOnId() {
    final SimulaSupervisor a = new SimulaSupervisor(engine);
    final SimulaSupervisor b = new SimulaSupervisor(engine);
    assertEquals(a, a);
    assertNotEquals(a, b);
    assertNotEquals(a, null);
    assertEquals(a.hashCode(), a.hashCode());
  }

  // ---------- US1: startup observation (FR-001, FR-005, SC-002) ----------

  @Test
  void recordsStartedOnStartupEvent() {
    final SimulaSupervisor s = new SimulaSupervisor(engine);
    final Actor other = source("other");
    s.process(lifecycleEvent(other, Engine.STARTED_ACTOR_EVENT));
    assertEquals(Status.STARTED, s.getStates().get(other));
  }

  // ---------- US2: stop observation (FR-003, FR-004, SC-003) ----------

  @Test
  void recordsStoppedOnActorStopEvent() {
    final SimulaSupervisor s = new SimulaSupervisor(engine);
    final Actor other = source("other");
    s.process(lifecycleEvent(other, Engine.STOPPED_ACTOR_EVENT));
    assertEquals(Status.STOPPED, s.getStates().get(other));
  }

  @Test
  void recordsStoppedOnEngineStopEvent() {
    final SimulaSupervisor s = new SimulaSupervisor(engine);
    final Actor child = source("child");
    s.process(lifecycleEvent(child, Engine.STOPPED_ENGINE_EVENT));
    assertEquals(Status.STOPPED, s.getStates().get(child));
  }

  // ---------- US3: queryable state (FR-006, FR-009, SC-004) ----------

  @Test
  void stateMatchesSequenceOfStartThenStop() {
    final SimulaSupervisor s = new SimulaSupervisor(engine);
    final Actor other = source("other");
    s.process(lifecycleEvent(other, Engine.STARTED_ACTOR_EVENT));
    assertEquals(Status.STARTED, s.getStates().get(other));
    s.process(lifecycleEvent(other, Engine.STOPPED_ACTOR_EVENT));
    assertEquals(Status.STOPPED, s.getStates().get(other));
  }

  @Test
  void stopWithoutPriorStartIsRecordedStoppedWithoutError() {
    final SimulaSupervisor s = new SimulaSupervisor(engine);
    final Actor other = source("other");
    s.process(lifecycleEvent(other, Engine.STOPPED_ACTOR_EVENT));
    assertEquals(Status.STOPPED, s.getStates().get(other));
  }

  @Test
  void getStatesReturnsUnmodifiableView() {
    final SimulaSupervisor s = new SimulaSupervisor(engine);
    final Map<Actor, Status> states = s.getStates();
    assertThrows(
        UnsupportedOperationException.class, () -> states.put(source("x"), Status.STARTED));
  }

  @Test
  void ignoresUnrelatedEvents() {
    final SimulaSupervisor s = new SimulaSupervisor(engine);
    final Actor other = source("other");
    s.process(lifecycleEvent(other, "UNRELATED"));
    assertTrue(s.getStates().isEmpty());
    verify(engine, never()).signal(any(Event.class));
  }

  // ---------- US4: documented external-actor contract (FR-007) ----------

  @Test
  void observesExternalActorFollowingDocumentedContract() {
    final SimulaSupervisor s = new SimulaSupervisor(engine);
    final Actor external = source("external");
    s.process(lifecycleEvent(external, Engine.STARTED_ACTOR_EVENT));
    assertEquals(Status.STARTED, s.getStates().get(external));
  }
}
