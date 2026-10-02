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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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

  /**
   * Concurrent process() calls from external threads (FR-007) while the recorded state is iterated
   * must neither corrupt the map nor fail: the recording must never throw and every observed actor
   * must end up recorded exactly once (FR-006).
   */
  @Test
  void concurrentRecordingAndReadingKeepsTheMapConsistent() throws Exception {
    final SimulaSupervisor s = new SimulaSupervisor(engine);
    final int producers = 3;
    final int perProducer = 1500;
    final List<List<Actor>> batches = new ArrayList<>();
    for (int p = 0; p < producers; p++) {
      final List<Actor> batch = new ArrayList<>();
      for (int i = 0; i < perProducer; i++) {
        batch.add(source("actor-" + p + "-" + i));
      }
      batches.add(batch);
    }
    final ExecutorService pool = Executors.newFixedThreadPool(producers + 1);
    final List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
    final CountDownLatch producersDone = new CountDownLatch(producers);
    try {
      for (int p = 0; p < producers; p++) {
        final List<Actor> batch = batches.get(p);
        pool.submit(
            () -> {
              try {
                for (final Actor actor : batch) {
                  s.process(lifecycleEvent(actor, Engine.STARTED_ACTOR_EVENT));
                }
              } catch (final Throwable exc) {
                failures.add(exc);
              } finally {
                producersDone.countDown();
              }
            });
      }
      pool.submit(
          () -> {
            try {
              while (producersDone.getCount() > 0) {
                for (final Map.Entry<Actor, Status> entry : s.getStates().entrySet()) {
                  assertNotNull(entry.getValue());
                }
              }
            } catch (final Throwable exc) {
              failures.add(exc);
            }
          });
      pool.shutdown();
      assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS), "stress should complete");
    } finally {
      pool.shutdownNow();
    }
    assertTrue(
        failures.isEmpty(),
        "concurrent recording and reading must never corrupt the state: " + failures);
    assertEquals(producers * perProducer, s.getStates().size());
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
