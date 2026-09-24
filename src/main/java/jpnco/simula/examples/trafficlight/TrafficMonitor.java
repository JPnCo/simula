package jpnco.simula.examples.trafficlight;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import jpnco.simula.Actor;
import jpnco.simula.Engine;
import jpnco.simula.Event;
import jpnco.simula.engine.ActorDelegate;
import jpnco.simula.engine.IdBuilder;

/**
 * An illustrative actor that aggregates the state of every intersection and prints a global status
 * on each {@link Engine#TIME_EVENT}.
 *
 * <p>It subscribes to the per-intersection summary topics forwarded by the {@link
 * IntersectionController} instances and stops the simulation once {@value #SIMULATED_SECONDS}
 * simulated seconds have elapsed, so the run length is deterministic across execution modes
 * (FR-005, SC-003).
 *
 * <p>This class is demonstration code under the {@code examples} package; it is excluded from the
 * coverage gate and is not part of the framework contract (plan: runnable demo).
 */
public final class TrafficMonitor implements Actor {

  /** The number of simulated seconds the demo runs before stopping (SC-003). */
  public static final int SIMULATED_SECONDS = 12;

  /** The engine that must be stopped when the demo completes (FR-008). */
  private final Engine root;

  private final Actor delegate;
  private final Integer id;
  private final Map<String, IntersectionController> intersections = new LinkedHashMap<>();
  private final CountDownLatch done = new CountDownLatch(1);
  private int timeEventCount = 0;

  public TrafficMonitor(final Engine root) {
    this.root = Objects.requireNonNull(root);
    id = IdBuilder.nextId();
    delegate = ActorDelegate.createDelegate(root, this);
    root.subscribe(this, Engine.TIME_EVENT);
  }

  @Override
  public Actor getDelegate() {
    return delegate;
  }

  @Override
  public Integer getId() {
    return id;
  }

  /**
   * Registers an intersection to be monitored.
   *
   * @param controller the intersection controller to monitor
   */
  public void addIntersection(final IntersectionController controller) {
    intersections.put(controller.getName(), controller);
  }

  @Override
  public void process(final Event event) {
    if (Engine.TIME_EVENT.equals(event.getTopic())) {
      timeEventCount++;
      printStatus();
      if (timeEventCount >= SIMULATED_SECONDS) {
        root.stop();
        done.countDown();
      }
    }
  }

  /** Prints the current aggregate status of every intersection (FR-005). */
  private void printStatus() {
    final StringBuilder line = new StringBuilder("status @t=").append(root.getTime()).append(" [");
    intersections
        .values()
        .forEach(
            controller ->
                line.append(controller.getName())
                    .append('=')
                    .append(controller.getLightState())
                    .append(':')
                    .append(controller.getVehicleCount())
                    .append(' '));
    line.append(']');
    System.out.println(line);
  }

  /**
   * Returns an unmodifiable view of the monitored intersections.
   *
   * @return the intersections keyed by name
   */
  public Map<String, IntersectionController> getIntersections() {
    return Collections.unmodifiableMap(intersections);
  }

  /**
   * Returns the latch released once the demo has run for {@link #SIMULATED_SECONDS} and the root
   * engine has been stopped (SC-003).
   *
   * @return the completion latch
   */
  public CountDownLatch getDoneLatch() {
    return done;
  }
}
