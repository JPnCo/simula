package fr.jpnco.simula.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.jpnco.simula.Actor;
import fr.jpnco.simula.Engine;
import fr.jpnco.simula.Event;
import fr.jpnco.simula.actors.Logger;
import fr.jpnco.simula.actors.Logger.Level;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for the concurrency guarantees of the ReentrantLock replacement (FR-010, FR-011, FR-012,
 * SC-007). Verifies that shared state remains mutually exclusive and that locks are released on all
 * paths.
 */
class ConcurrencyTest {

  /** The number of concurrent workers used in each test (FR-012). */
  private static final int WORKER_COUNT = 8;

  /** The number of iterations performed by each worker (FR-012). */
  private static final int ITERATIONS = 200;

  /** The maximum time to wait for concurrent workers to finish (FR-012). */
  private static final long TIMEOUT_SECONDS = 10;

  /** The topic used for subscription stress tests (FR-012). */
  private static final String TOPIC = "stress";

  /** An actor that only needs an identity for register/unregister stress (FR-012). */
  private static final class DummyActor implements Actor {
    private final Engine engine;
    private final Actor delegate;

    DummyActor(final Engine engine) {
      this.engine = engine;
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

  @AfterEach
  void tearDown() {
    try {
      Thread.sleep(200);
    } catch (final InterruptedException exc) {
      Thread.currentThread().interrupt();
    }
  }

  @Test
  void concurrentSubscriptionDoesNotCorruptState() throws InterruptedException {
    Logger.forceLevel(Level.ERROR);
    final EngineImpl engine = new EngineImpl("concurrentSubs", 2);
    final ExecutorService pool = Executors.newFixedThreadPool(WORKER_COUNT);
    final CountDownLatch ready = new CountDownLatch(WORKER_COUNT);
    final CountDownLatch start = new CountDownLatch(1);

    for (int i = 0; i < WORKER_COUNT; i++) {
      final int worker = i;
      pool.submit(
          () -> {
            final DummyActor actor = new DummyActor(engine);
            ready.countDown();
            try {
              start.await();
              for (int j = 0; j < ITERATIONS; j++) {
                engine.subscribe(actor, TOPIC + worker);
                engine.unsubscribe(actor, TOPIC + worker);
              }
            } catch (final InterruptedException exc) {
              Thread.currentThread().interrupt();
            }
          });
    }

    ready.await();
    start.countDown();
    pool.shutdown();
    assertTrue(
        pool.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS),
        "concurrent subscribe/unsubscribe should complete");
    engine.stop();
  }

  @Test
  void concurrentIdBuildingProducesUniqueIds() throws InterruptedException {
    Logger.forceLevel(Level.ERROR);
    final ExecutorService pool = Executors.newFixedThreadPool(WORKER_COUNT);
    final Set<Integer> ids = Collections.synchronizedSet(new HashSet<>());
    final CountDownLatch ready = new CountDownLatch(WORKER_COUNT);
    final CountDownLatch start = new CountDownLatch(1);

    for (int i = 0; i < WORKER_COUNT; i++) {
      pool.submit(
          () -> {
            ready.countDown();
            try {
              start.await();
              for (int j = 0; j < ITERATIONS; j++) {
                ids.add(IdBuilder.nextId());
              }
            } catch (final InterruptedException exc) {
              Thread.currentThread().interrupt();
            }
          });
    }

    ready.await();
    start.countDown();
    pool.shutdown();
    assertTrue(
        pool.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS),
        "concurrent id building should complete");
    assertEquals(WORKER_COUNT * ITERATIONS, ids.size(), "all generated ids must be unique");
  }

  @Test
  void repeatedLockedOperationsLeaveEngineFunctional() {
    Logger.forceLevel(Level.ERROR);
    final EngineImpl engine = new EngineImpl("lockRelease", 2);

    for (int i = 0; i < ITERATIONS; i++) {
      engine.subscribe(engine, TOPIC);
      engine.unsubscribe(engine, TOPIC);
    }

    engine.start();
    engine.signal(EventImpl.createEvent(TOPIC, engine, (Actor) null));
    engine.stop();
  }
}
