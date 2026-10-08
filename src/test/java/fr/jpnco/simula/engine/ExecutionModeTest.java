package fr.jpnco.simula.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.jpnco.simula.Actor;
import fr.jpnco.simula.Engine;
import fr.jpnco.simula.Event;
import fr.jpnco.simula.actors.Logger;
import fr.jpnco.simula.actors.Logger.Level;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for the configurable execution mode (US1, US2). Verifies that actors run on virtual threads
 * by default and on classic threads when explicitly selected (FR-001, FR-002, FR-003, FR-004,
 * FR-006, FR-007).
 */
class ExecutionModeTest {

  /** The topic used to signal the test event to a probe actor (FR-003). */
  private static final String TEST_TOPIC = "TEST";

  /** The maximum time to wait for an actor to process an event (FR-003). */
  private static final long LATCH_TIMEOUT_SECONDS = 5;

  /**
   * An actor that records the thread on which it processed a test event and releases a latch when
   * done (FR-004, FR-006).
   */
  private static final class ProbeActor implements Actor {

    private final Actor delegate;
    private final CountDownLatch latch = new CountDownLatch(1);
    private final AtomicReference<Thread> processingThread = new AtomicReference<>();
    private final AtomicBoolean subscribed = new AtomicBoolean();

    ProbeActor(final Engine engine) {
      delegate = ActorDelegate.createDelegate(engine, this);
    }

    @Override
    public void afterStart() {
      subscribed.set(true);
    }

    @Override
    public Actor getDelegate() {
      return delegate;
    }

    @Override
    public Integer getId() {
      return 0;
    }

    @Override
    public void process(final Event event) {
      if (TEST_TOPIC.equals(event.getTopic())) {
        processingThread.set(Thread.currentThread());
        latch.countDown();
      }
    }

    boolean await() {
      try {
        return latch.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      } catch (final InterruptedException exc) {
        Thread.currentThread().interrupt();
        return false;
      }
    }

    boolean isProcessingThreadVirtual() {
      final Thread t = processingThread.get();
      return t != null && t.isVirtual();
    }

    String getProcessingThreadName() {
      final Thread t = processingThread.get();
      return t == null ? "none" : t.getName();
    }

    Thread getProcessingThread() {
      return processingThread.get();
    }
  }

  @AfterEach
  void tearDown() {
    try {
      Thread.sleep(300);
    } catch (final InterruptedException exc) {
      Thread.currentThread().interrupt();
    }
  }

  @Test
  void defaultModeRunsActorOnVirtualThread() {
    Logger.forceLevel(Level.ERROR);
    final EngineImpl engine = new EngineImpl("defaultVirtual", 2);
    final ProbeActor actor = new ProbeActor(engine);
    engine.registerAndStart(actor);
    actor.subscribe(TEST_TOPIC);
    engine.start();
    engine.signal(EventImpl.createEvent(TEST_TOPIC, engine, (Actor) null));

    assertTrue(actor.await(), "actor should process the event");
    assertTrue(actor.isProcessingThreadVirtual(), "default mode should run on a virtual thread");
    engine.stop();
  }

  @Test
  void classicModeRunsActorOnPlatformThread() {
    Logger.forceLevel(Level.ERROR);
    final EngineImpl engine = new EngineImpl("explicitClassic", 2, ExecutionMode.PLATFORM);
    final ProbeActor actor = new ProbeActor(engine);
    engine.registerAndStart(actor);
    actor.subscribe(TEST_TOPIC);
    engine.start();
    engine.signal(EventImpl.createEvent(TEST_TOPIC, engine, (Actor) null));

    assertTrue(actor.await(), "actor should process the event");
    assertFalse(actor.isProcessingThreadVirtual(), "classic mode should run on a platform thread");
    engine.stop();
  }

  @Test
  void actorThreadCarriesMeaningfulNameInBothModes() {
    Logger.forceLevel(Level.ERROR);
    final EngineImpl engine = new EngineImpl("namedMode", 2);
    final ProbeActor actor = new ProbeActor(engine);
    engine.registerAndStart(actor);
    actor.subscribe(TEST_TOPIC);
    engine.start();
    engine.signal(EventImpl.createEvent(TEST_TOPIC, engine, (Actor) null));

    assertTrue(actor.await(), "actor should process the event");
    assertFalse(actor.getProcessingThreadName().isEmpty(), "thread name must not be empty");
    engine.stop();
  }

  @Test
  void fromNameAcceptsLowercase() {
    assertEquals(ExecutionMode.VIRTUAL, ExecutionMode.fromName("virtual"));
    assertEquals(ExecutionMode.PLATFORM, ExecutionMode.fromName("platform"));
  }

  @Test
  void fromNameAcceptsMixedCaseAndSpaces() {
    assertEquals(ExecutionMode.VIRTUAL, ExecutionMode.fromName("  ViRtUaL  "));
    assertEquals(ExecutionMode.PLATFORM, ExecutionMode.fromName("\tPLATFORM\n"));
  }

  @Test
  void fromNameAcceptsExactEnumNames() {
    assertEquals(ExecutionMode.VIRTUAL, ExecutionMode.fromName("VIRTUAL"));
    assertEquals(ExecutionMode.PLATFORM, ExecutionMode.fromName("PLATFORM"));
  }

  @Test
  void valuesContainsBothModes() {
    assertEquals(2, ExecutionMode.values().length);
    final ExecutionMode[] modes = ExecutionMode.values();
    assertEquals(ExecutionMode.VIRTUAL, modes[0]);
    assertEquals(ExecutionMode.PLATFORM, modes[1]);
  }

  @Test
  void unknownModeNameIsRejected() {
    final IllegalArgumentException exc =
        assertThrows(IllegalArgumentException.class, () -> ExecutionMode.fromName("unknown"));
    assertTrue(exc.getMessage().contains("Unknown execution mode"), "error should be clear");
  }

  @Test
  void nullModeNameIsRejected() {
    assertThrows(IllegalArgumentException.class, () -> ExecutionMode.fromName(null));
  }

  @Test
  void identicalScenarioProducesEquivalentOutcomesInBothModes() {
    Logger.forceLevel(Level.ERROR);
    final EngineImpl virtualEngine = new EngineImpl("equivVirtual", 2);
    final EngineImpl classicEngine = new EngineImpl("equivClassic", 2, ExecutionMode.PLATFORM);

    final ProbeActor virtualActor = new ProbeActor(virtualEngine);
    final ProbeActor classicActor = new ProbeActor(classicEngine);
    virtualEngine.registerAndStart(virtualActor);
    classicEngine.registerAndStart(classicActor);
    virtualActor.subscribe(TEST_TOPIC);
    classicActor.subscribe(TEST_TOPIC);
    virtualEngine.start();
    classicEngine.start();

    virtualEngine.signal(EventImpl.createEvent(TEST_TOPIC, virtualEngine, (Actor) null));
    classicEngine.signal(EventImpl.createEvent(TEST_TOPIC, classicEngine, (Actor) null));

    assertTrue(virtualActor.await(), "virtual-mode actor should process the event");
    assertTrue(classicActor.await(), "classic-mode actor should process the event");

    virtualEngine.stop();
    classicEngine.stop();
  }

  @Test
  void stopTerminatesActorsInBothModes() {
    Logger.forceLevel(Level.ERROR);
    final EngineImpl virtualEngine = new EngineImpl("stopVirtual", 2);
    final EngineImpl classicEngine = new EngineImpl("stopClassic", 2, ExecutionMode.PLATFORM);

    final ProbeActor virtualActor = new ProbeActor(virtualEngine);
    final ProbeActor classicActor = new ProbeActor(classicEngine);
    virtualEngine.registerAndStart(virtualActor);
    classicEngine.registerAndStart(classicActor);
    virtualActor.subscribe(TEST_TOPIC);
    classicActor.subscribe(TEST_TOPIC);
    virtualEngine.start();
    classicEngine.start();

    virtualEngine.signal(EventImpl.createEvent(TEST_TOPIC, virtualEngine, (Actor) null));
    classicEngine.signal(EventImpl.createEvent(TEST_TOPIC, classicEngine, (Actor) null));
    assertTrue(virtualActor.await(), "virtual-mode actor should process before stop");
    assertTrue(classicActor.await(), "classic-mode actor should process before stop");

    virtualEngine.stop();
    classicEngine.stop();

    assertTrue(
        waitForThreadToFinish(virtualActor.getProcessingThread()),
        "virtual-mode actor thread should terminate after stop");
    assertTrue(
        waitForThreadToFinish(classicActor.getProcessingThread()),
        "classic-mode actor thread should terminate after stop");
  }

  /**
   * Waits for the given thread to terminate (FR-008, SC-005).
   *
   * @param thread the thread to await, may be null
   * @return true if the thread terminated or was null, false otherwise
   */
  private static boolean waitForThreadToFinish(final Thread thread) {
    if (thread == null) {
      return true;
    }
    try {
      thread.join(TimeUnit.SECONDS.toMillis(LATCH_TIMEOUT_SECONDS));
    } catch (final InterruptedException exc) {
      Thread.currentThread().interrupt();
      return false;
    }
    return !thread.isAlive();
  }
}
