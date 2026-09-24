package jpnco.simula.examples.trafficlight;

import java.util.concurrent.TimeUnit;
import jpnco.simula.actors.Logger;
import jpnco.simula.actors.Logger.Level;
import jpnco.simula.engine.EngineImpl;
import jpnco.simula.engine.ExecutionMode;

/**
 * Runnable illustration of the simula framework: a small traffic-light network with two
 * intersections, each with a cycling light, a vehicle sensor, and an aggregating controller, plus a
 * root {@link TrafficMonitor} that prints a global status each simulated second.
 *
 * <p>The same scenario can be run under {@link ExecutionMode#VIRTUAL} (default) or {@link
 * ExecutionMode#PLATFORM} to demonstrate the configurable execution mode and that observable
 * outcomes are equivalent across modes (FR-001..FR-006, FR-008, SC-003).
 *
 * <p>Usage: {@code TrafficLightDemo [virtual|classic]}. The scenario runs for {@value
 * TrafficMonitor#SIMULATED_SECONDS} simulated seconds, then the root engine stops every actor and
 * child engine (FR-008) and the final outcome summary is printed.
 *
 * <p>This class is demonstration code under the {@code examples} package; it is excluded from the
 * coverage gate and is not part of the framework contract (plan: runnable demo).
 */
public final class TrafficLightDemo {

  /** The time factor (simulated seconds per real second) of the root engine (FR-002). */
  private static final int TIME_FACTOR = 1;

  /** The name of the root engine (FR-002). */
  private static final String ROOT_NAME = "root";

  /** The name of the first intersection (FR-004). */
  private static final String INTERSECTION_1 = "intersection-1";

  /** The name of the second intersection (FR-004). */
  private static final String INTERSECTION_2 = "intersection-2";

  /** The command-line token that selects classic platform threads (FR-003). */
  private static final String CLASSIC_ARG = "classic";

  /** The milliseconds to let the last events drain after the root engine stops (FR-008). */
  private static final long DRAIN_MILLIS = 1500;

  /** The milliseconds to await the completion latch (SC-003). */
  private static final long AWAIT_TIMEOUT_SECONDS = 60;

  private TrafficLightDemo() {}

  /**
   * Entry point of the traffic-light demo.
   *
   * @param args optional mode token: {@code classic} selects {@link ExecutionMode#PLATFORM}, any
   *     other or absent value selects {@link ExecutionMode#VIRTUAL} (FR-003)
   */
  public static void main(final String[] args) {
    final ExecutionMode mode = resolveMode(args);
    Logger.forceLevel(Level.INFO);

    final EngineImpl root = new EngineImpl(ROOT_NAME, TIME_FACTOR, mode);
    final TrafficMonitor monitor = new TrafficMonitor(root);

    final IntersectionController first = buildIntersection(root, monitor, INTERSECTION_1, mode);
    final IntersectionController second = buildIntersection(root, monitor, INTERSECTION_2, mode);
    monitor.addIntersection(first);
    monitor.addIntersection(second);

    root.registerAndStart(monitor);
    root.start();

    awaitCompletion(monitor);
    printOutcome(mode, monitor);

    System.exit(0);
  }

  /**
   * Builds one intersection: a child engine hosting a traffic light, a vehicle sensor and an
   * aggregating controller.
   *
   * @param root the parent (root) engine
   * @param monitor the monitor that aggregates the intersection
   * @param name the intersection name
   * @param mode the execution mode of the child engine
   * @return the intersection controller
   */
  private static IntersectionController buildIntersection(
      final EngineImpl root,
      final TrafficMonitor monitor,
      final String name,
      final ExecutionMode mode) {
    final EngineImpl child = new EngineImpl(name, root, mode);
    final IntersectionController controller = new IntersectionController(child, name);
    child.registerAndStart(controller);
    child.registerAndStart(new TrafficLight(child));
    child.registerAndStart(new VehicleSensor(child));
    return controller;
  }

  /**
   * Resolves the execution mode from the command-line arguments.
   *
   * @param args the command-line arguments
   * @return {@link ExecutionMode#PLATFORM} if the first argument is {@value #CLASSIC_ARG},
   *     otherwise {@link ExecutionMode#VIRTUAL}
   */
  private static ExecutionMode resolveMode(final String[] args) {
    if (args.length > 0 && CLASSIC_ARG.equalsIgnoreCase(args[0])) {
      return ExecutionMode.PLATFORM;
    }
    return ExecutionMode.VIRTUAL;
  }

  /**
   * Awaits the completion of the simulation.
   *
   * @param monitor the monitor whose completion latch is awaited
   */
  private static void awaitCompletion(final TrafficMonitor monitor) {
    try {
      if (!monitor.getDoneLatch().await(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        System.err.println("Simulation did not complete within the timeout.");
      }
      Thread.sleep(DRAIN_MILLIS);
    } catch (final InterruptedException exc) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Prints the final outcome summary of the simulation.
   *
   * @param mode the execution mode that was used
   * @param monitor the monitor holding the final intersection state
   */
  private static void printOutcome(final ExecutionMode mode, final TrafficMonitor monitor) {
    final StringBuilder out = new StringBuilder("\n=== OUTCOME (").append(mode).append(") ===\n");
    monitor
        .getIntersections()
        .values()
        .forEach(
            controller ->
                out.append(controller.getName())
                    .append(": light=")
                    .append(controller.getLightState())
                    .append(", vehicles=")
                    .append(controller.getVehicleCount())
                    .append('\n'));
    System.out.println(out);
  }
}
