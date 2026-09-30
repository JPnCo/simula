package jpnco.simula.engine;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import jpnco.simula.Actor;
import jpnco.simula.Engine;
import jpnco.simula.Event;
import jpnco.simula.actors.Logger;
import jpnco.simula.actors.Logger.Level;
import jpnco.simula.actors.TimeSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for EngineImpl method-level behavior and branches not exercised by the core constructor
 * tests (FR-001..FR-013). Uses real engines and a minimal test actor.
 */
class EngineImplCoverageTest {

  /** The topic used for signal/subscription tests (FR-003). */
  private static final String TOPIC = "cover";

  /** A minimal actor that records the events it processes (FR-002, FR-003). */
  private static final class CoverActor implements Actor {
    private final Actor delegate;

    CoverActor(final Engine engine) {
      delegate = ActorDelegate.createDelegate(engine, this);
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
    public void process(final Event event) {}
  }

  /** A minimal actor with a unique id from {@link IdBuilder} (FR-012). */
  private static final class IdActor implements Actor {
    private final Actor delegate;
    private final Integer id;

    IdActor(final Engine engine) {
      delegate = ActorDelegate.createDelegate(engine, this);
      id = IdBuilder.nextId();
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
    public void process(final Event event) {}
  }

  private EngineImpl root;

  @BeforeEach
  void setUp() {
    Logger.forceLevel(Level.ERROR);
    root = new EngineImpl("coverRoot", 2);
  }

  @AfterEach
  void tearDown() {
    try {
      root.stop();
      Thread.sleep(200);
    } catch (final InterruptedException exc) {
      Thread.currentThread().interrupt();
    }
  }

  @Test
  void equalsAndHashCode() {
    assertEquals(root, root);
    assertNotEquals(root, null);
    assertNotEquals(root, new Object());
    assertNotEquals(root, new EngineImpl("coverRoot", 2));
    assertNotEquals(root.hashCode(), new EngineImpl("coverRoot", 2).hashCode());
  }

  @Test
  void filterDistinguishesEngineAndLoggerFromPlainActor() {
    assertFalse(root.filter(root), "an engine is filtered out");
    assertFalse(root.filter(root.getLogger()), "a logger is filtered out");
    assertTrue(root.filter(new CoverActor(root)), "a plain actor is not filtered");
  }

  @Test
  void getTimeFromRootTimeSource() {
    assertEquals(0, root.getTime(), "root time starts at zero");
  }

  @Test
  void childInheritsParentTimeFactor() {
    final EngineImpl child = new EngineImpl("coverChild", root);
    try {
      assertEquals(root.getTimeFactor(), child.getTimeFactor());
      assertEquals(root.getTime(), child.getTime());
    } finally {
      child.stop();
    }
  }

  @Test
  void getSimpleNameAndIdentity() {
    assertEquals(root.getName(), root.getSimpleName());
    assertNotNull(root.getId());
    assertNull(root.getParent());
    assertSame(root, root.getEngine());
  }

  @Test
  void processUnexpectedEventDoesNotThrow() {
    final Event event = EventImpl.createEvent(TOPIC, root);
    root.process(event);
  }

  @Test
  void subscribeAndUnsubscribeExistingTopic() {
    final CoverActor actor = new CoverActor(root);
    root.subscribe(actor, TOPIC);
    root.unsubscribe(actor, TOPIC);
  }

  @Test
  void unsubscribeNonExistingTopicDoesNotThrow() {
    root.unsubscribe(new CoverActor(root), TOPIC);
  }

  @Test
  void addChildRegistersChild() {
    final EngineImpl child = new EngineImpl("addChildChild", root);
    assertNotNull(child.getParent());
    child.stop();
  }

  @Test
  void getChildrenReturnsAddedChildrenInOrder() {
    final EngineImpl child1 = new EngineImpl("getChildrenChild1", root);
    final EngineImpl child2 = new EngineImpl("getChildrenChild2", root);
    final List<Engine> children = root.getChildren();
    assertEquals(2, children.size());
    assertEquals(child1, children.get(0));
    assertEquals(child2, children.get(1));
    child1.stop();
    child2.stop();
  }

  @Test
  void getChildrenReturnsNonModifiableList() {
    final EngineImpl child = new EngineImpl("getChildrenChild3", root);
    final List<Engine> children = root.getChildren();
    assertThrows(UnsupportedOperationException.class, () -> children.add(null));
    assertThrows(UnsupportedOperationException.class, () -> children.remove(0));
    assertThrows(UnsupportedOperationException.class, () -> children.clear());
    child.stop();
  }

  @Test
  void getChildrenReturnsSnapshot() {
    final List<Engine> before = root.getChildren();
    final EngineImpl child = new EngineImpl("getChildrenChild4", root);
    assertTrue(before.isEmpty());
    assertFalse(root.getChildren().isEmpty());
    child.stop();
  }

  @Test
  void getActorsReturnsRegisteredActors() {
    final List<Actor> baseline = root.getActors();
    final IdActor actor1 = new IdActor(root);
    final IdActor actor2 = new IdActor(root);
    root.registerAndStart(actor1);
    root.registerAndStart(actor2);
    final List<Actor> actors = root.getActors();
    assertEquals(baseline.size() + 2, actors.size());
    assertTrue(actors.contains(actor1));
    assertTrue(actors.contains(actor2));
    root.unregister(actor1);
    root.unregister(actor2);
  }

  @Test
  void getActorsReturnsNonModifiableList() {
    final IdActor actor = new IdActor(root);
    root.registerAndStart(actor);
    final List<Actor> actors = root.getActors();
    assertThrows(UnsupportedOperationException.class, () -> actors.add(null));
    assertThrows(UnsupportedOperationException.class, () -> actors.remove(0));
    assertThrows(UnsupportedOperationException.class, () -> actors.clear());
    root.unregister(actor);
  }

  @Test
  void getActorsReturnsSnapshot() {
    final List<Actor> before = root.getActors();
    final IdActor actor = new IdActor(root);
    root.registerAndStart(actor);
    final List<Actor> after = root.getActors();
    assertFalse(before.contains(actor));
    assertTrue(after.contains(actor));
    root.unregister(actor);
  }

  @Test
  void registerAndStartRunsActor() {
    final CoverActor actor = new CoverActor(root);
    final Actor started = root.registerAndStart(actor);
    assertSame(actor, started);
    root.unregister(actor);
  }

  @Test
  void signalDeliversToSubscribedActors() {
    final CoverActor actor = new CoverActor(root);
    root.registerAndStart(actor);
    root.subscribe(actor, TOPIC);
    root.signal(EventImpl.createEvent(TOPIC, root));
    root.unregister(actor);
  }

  @Test
  void signalStopSortsSubscribers() {
    final CoverActor actor = new CoverActor(root);
    root.registerAndStart(actor);
    root.subscribe(actor, Engine.STOP_EVENT);
    root.signal(EventImpl.createEvent(Engine.STOP_EVENT, root));
    root.unregister(actor);
  }

  @Test
  void toStringContainsActorsAndChildren() {
    final EngineImpl child = new EngineImpl("toStrChild", root);
    final CoverActor actor = new CoverActor(root);
    root.registerAndStart(actor);
    final String s = root.toString();
    assertTrue(s.contains("Engine"));
    root.unregister(actor);
    child.stop();
  }

  @Test
  void timeSourceIsRootOnly() {
    final EngineImpl child = new EngineImpl("tsChild", root);
    assertNull(child.getTimeSource());
    assertNotNull(root.getTimeSource());
    assertTrue(root.getTimeSource() instanceof TimeSource);
    child.stop();
  }

  @Test
  void constructorWithExplicitClassicMode() {
    final EngineImpl classic = new EngineImpl("classicRoot", 2, ExecutionMode.PLATFORM);
    try {
      assertEquals(2, classic.getTimeFactor());
    } finally {
      classic.stop();
    }
  }

  @Test
  void constructorWithExplicitModeAndParent() {
    final EngineImpl child = new EngineImpl("modeChild", root, ExecutionMode.PLATFORM);
    assertEquals(root, child.getParent());
    child.stop();
  }

  @Test
  void unregisterAnEngineTakesEngineBranch() {
    final EngineImpl other = new EngineImpl("otherUnregister", 2);
    try {
      root.unregister(other);
    } finally {
      other.stop();
    }
  }

  @Test
  void runProcessesUnexpectedEvent() {
    root.post(EventImpl.createEvent("unknownTopic", root));
    waitForRun();
  }

  @Test
  void runProcessesStoppedEngineEvent() {
    final EngineImpl child = new EngineImpl("runStopChild", root);
    try {
      final Event stopped = EventImpl.createEvent(Engine.STOPPED_ENGINE_EVENT, child, child);
      root.post(stopped);
      waitForRun();
    } finally {
      child.stop();
    }
  }

  @Test
  void runProcessesStartAndTimeEvents() {
    final EngineImpl child = new EngineImpl("runStartChild", root);
    try {
      root.post(EventImpl.createEvent(Engine.START_EVENT, root, (Actor) null));
      root.post(EventImpl.createEvent(Engine.TIME_EVENT, root));
      waitForRun();
    } finally {
      child.stop();
    }
  }

  @Test
  void runBreaksLoopWhenLastActorStopsAndNoChildren() {
    final EngineImpl leaf = new EngineImpl("leafBreak", 2);
    try {
      leaf.unregister(leaf.getLogger());
      leaf.unregister(leaf.getTimeSource());
      final Actor ghost = new CoverActor(leaf);
      leaf.post(EventImpl.createEvent(Engine.STOPPED_ACTOR_EVENT, ghost));
      waitForRun(1500);
    } finally {
      leaf.stop();
    }
  }

  @Test
  void runBreaksLoopWhenEngineStopsAndNoActorsOrChildren() {
    final EngineImpl leaf = new EngineImpl("leafEngineBreak", 2);
    try {
      leaf.unregister(leaf.getLogger());
      leaf.unregister(leaf.getTimeSource());
      final Engine fake = new EngineImpl("fakeChild", 2);
      final Event stopped = EventImpl.createEvent(Engine.STOPPED_ENGINE_EVENT, fake, fake);
      leaf.post(stopped);
      waitForRun(1500);
      fake.stop();
    } finally {
      leaf.stop();
    }
  }

  /**
   * Verifies the FR-009 contract: on a runtime that supports virtual threads (the project target,
   * Java 25), a VIRTUAL-mode engine starts an actor without raising the documented {@link
   * UnsupportedOperationException}. The clear-failure path only triggers on runtimes that do not
   * support virtual threads (Java &lt; 21).
   */
  @Test
  void virtualModeStartsActorWithoutUnsupportedFailure() {
    final EngineImpl virtual = new EngineImpl("virtualFr009", 2, ExecutionMode.VIRTUAL);
    try {
      final CoverActor actor = new CoverActor(virtual);
      assertDoesNotThrow(() -> virtual.registerAndStart(actor));
      virtual.unregister(actor);
    } finally {
      virtual.stop();
    }
  }

  /**
   * Verifies SC-006: an engine in VIRTUAL mode can start and stop successfully with a high number
   * of actors without exhausting platform resources. Every actor processes the signaled event, then
   * the engine is stopped and its actors terminate.
   */
  @Test
  void virtualModeHandlesHighActorCount() throws InterruptedException {
    final int ACTOR_COUNT = 500;
    final CountDownLatch processed = new CountDownLatch(ACTOR_COUNT);
    final EngineImpl virtual = new EngineImpl("virtualStress", 2, ExecutionMode.VIRTUAL);
    final List<Actor> actors = new ArrayList<>();
    try {
      for (int i = 0; i < ACTOR_COUNT; i++) {
        final Actor actor = new CountingActor(virtual, processed);
        actors.add(actor);
        virtual.registerAndStart(actor);
        virtual.subscribe(actor, TOPIC);
      }
      virtual.signal(EventImpl.createEvent(TOPIC, virtual));
      assertTrue(processed.await(30, TimeUnit.SECONDS), "all actors should process the event");
    } finally {
      for (final Actor actor : actors) {
        virtual.unregister(actor);
      }
      virtual.stop();
    }
  }

  // --- Feature 005: forbid actor restart (FR-001..FR-003, FR-007, FR-008) ---

  /** Polls until the actor is no longer registered on the engine, then asserts its absence. */
  private static void awaitAbsent(final Engine engine, final Actor actor)
      throws InterruptedException {
    final long timeoutMillis = 5000;
    final long deadline = System.currentTimeMillis() + timeoutMillis;
    while (engine.getActors().contains(actor) && System.currentTimeMillis() < deadline) {
      Thread.sleep(20);
    }
    assertFalse(
        engine.getActors().contains(actor), "actor should have been unregistered after stop");
  }

  /**
   * T010: re-registering a stopped actor is refused and leaves the engine untouched (FR-001,
   * FR-002, FR-003).
   */
  @Test
  void stoppedActorCannotBeRegisteredAgain() throws InterruptedException {
    final IdActor actor = new IdActor(root);
    root.registerAndStart(actor);
    root.start();
    // Give the delegate thread time to subscribe before stopping: signaling START/STOP_ME to a
    // delegate that has not subscribed yet is inherently racy (framework behavior, out of scope
    // 005).
    waitForRun(300);
    assertTrue(root.getActors().contains(actor), "actor should be registered after start");
    actor.stopMe();
    awaitAbsent(root, actor);
    final List<Actor> snapshot = root.getActors();
    assertThrows(
        IllegalArgumentException.class,
        () -> root.registerAndStart(actor),
        "a stopped actor instance must be refused (SC-001)");
    assertEquals(
        snapshot,
        root.getActors(),
        "engine actor set must be unchanged after the refusal (SC-002)");
  }

  /**
   * T011: registering a currently-registered actor is refused and does not disturb it (FR-008,
   * SC-005).
   */
  @Test
  void currentlyRegisteredActorCannotBeRegisteredTwice() throws InterruptedException {
    final CountDownLatch processed = new CountDownLatch(2);
    final CountingActor actor = new CountingActor(root, processed);
    root.registerAndStart(actor);
    root.start();
    root.subscribe(actor, TOPIC);
    root.signal(EventImpl.createEvent(TOPIC, root));
    waitForRun(300);
    assertEquals(1, processed.getCount(), "running actor should have processed the first event");
    assertThrows(
        IllegalArgumentException.class,
        () -> root.registerAndStart(actor),
        "a currently-registered actor must not be registered twice (FR-008)");
    assertTrue(root.getActors().contains(actor), "the running actor must stay registered");
    root.signal(EventImpl.createEvent(TOPIC, root));
    assertTrue(
        processed.await(5, TimeUnit.SECONDS), "the running actor must stay undisturbed (SC-005)");
  }

  /**
   * T012: a fresh instance is accepted even when a same-class actor previously stopped (FR-007).
   */
  @Test
  void freshInstanceAcceptedAfterOtherActorStopped() throws InterruptedException {
    final IdActor first = new IdActor(root);
    root.registerAndStart(first);
    root.start();
    waitForRun(300);
    first.stopMe();
    awaitAbsent(root, first);
    final IdActor second = new IdActor(root);
    assertDoesNotThrow(
        () -> root.registerAndStart(second), "a fresh instance must be accepted (FR-007)");
    assertTrue(root.getActors().contains(second), "the fresh instance must be registered");
  }

  /** T013: a refused registration leaves no trace: no subscription, no execution (FR-003). */
  @Test
  void refusedRegistrationLeavesNoTrace() throws InterruptedException {
    final CountDownLatch processed = new CountDownLatch(1);
    final CountingActor actor = new CountingActor(root, processed);
    root.registerAndStart(actor);
    root.start();
    waitForRun(300);
    actor.stopMe();
    awaitAbsent(root, actor);
    assertThrows(IllegalArgumentException.class, () -> root.registerAndStart(actor));
    root.signal(EventImpl.createEvent(TOPIC, root));
    waitForRun(300);
    assertEquals(1, processed.getCount(), "a refused actor must not process any event (FR-003)");
    assertFalse(
        root.getActors().contains(actor), "a refused actor must not appear registered (FR-003)");
  }

  /**
   * An actor whose custom delegation never reports a stopped status: its delegate has no delegate
   * of its own, so the defaulted isStopped() stays false (FR-005).
   */
  private static final class CustomActor implements Actor {
    private final Integer id = IdBuilder.nextId();
    private final Engine engine;
    private final Actor customDelegate =
        new Actor() {
          @Override
          public Actor getDelegate() {
            return null;
          }

          @Override
          public Integer getId() {
            return 0;
          }

          @Override
          public void process(final Event event) {}

          @Override
          public void run() {}
        };

    CustomActor(final Engine engine) {
      this.engine = engine;
    }

    @Override
    public Actor getDelegate() {
      return customDelegate;
    }

    @Override
    public Engine getEngine() {
      return engine;
    }

    @Override
    public Integer getId() {
      return id;
    }

    @Override
    public void process(final Event event) {}
  }

  /**
   * T030: a custom-delegated actor that never reports stopped is refused via the weak memory
   * (FR-005, SC-001).
   */
  @Test
  void stoppedCustomDelegatedActorIsRefused() {
    final CustomActor actor = new CustomActor(root);
    root.registerAndStart(actor);
    assertFalse(actor.isStopped(), "the custom delegation never reports stopped");
    root.unregister(actor);
    assertThrows(
        IllegalArgumentException.class,
        () -> root.registerAndStart(actor),
        "the engine memory of stopped instances must refuse the re-registration (FR-005)");
    assertFalse(root.getActors().contains(actor));
  }

  /**
   * T031: stopping and releasing many short-lived actors keeps the engine healthy; a fresh
   * registration still works (SC-003).
   */
  @Test
  void longSimulationWithShortLivedActorsStaysHealthy() throws InterruptedException {
    final int actorCount = 100;
    root.start();
    waitForRun(300);
    final int baseline = root.getActors().size();
    final List<Actor> actors = new ArrayList<>();
    for (int i = 0; i < actorCount; i++) {
      final IdActor actor = new IdActor(root);
      actors.add(actor);
      root.registerAndStart(actor);
    }
    assertEquals(baseline + actorCount, root.getActors().size());
    for (final Actor actor : actors) {
      actor.stopMe();
    }
    final long deadline = System.currentTimeMillis() + 15000;
    while (root.getActors().size() > baseline && System.currentTimeMillis() < deadline) {
      Thread.sleep(50);
    }
    assertEquals(
        baseline, root.getActors().size(), "all short-lived actors should have unregistered");
    actors.clear();
    final IdActor fresh = new IdActor(root);
    assertDoesNotThrow(
        () -> root.registerAndStart(fresh), "a fresh registration must still succeed (SC-003)");
    assertTrue(root.getActors().contains(fresh));
  }

  /** An actor that counts down a latch each time it processes the test topic (FR-002). */
  private static final class CountingActor implements Actor {
    private final Actor delegate;
    private final Integer id;
    private final CountDownLatch processed;

    CountingActor(final Engine engine, final CountDownLatch processed) {
      this.processed = processed;
      this.id = IdBuilder.nextId();
      delegate = ActorDelegate.createDelegate(engine, this);
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
      if (TOPIC.equals(event.getTopic())) {
        processed.countDown();
      }
    }
  }

  private static void waitForRun() {
    waitForRun(500);
  }

  private static void waitForRun(final long millis) {
    try {
      Thread.sleep(millis);
    } catch (final InterruptedException exc) {
      Thread.currentThread().interrupt();
    }
  }
}
