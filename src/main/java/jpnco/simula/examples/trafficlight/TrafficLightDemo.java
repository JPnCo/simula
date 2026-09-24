package jpnco.simula.examples.trafficlight;

import java.util.concurrent.TimeUnit;
import jpnco.simula.actors.Logger;
import jpnco.simula.actors.Logger.Level;
import jpnco.simula.engine.EngineImpl;
import jpnco.simula.engine.ExecutionMode;

/**
 * Runnable illustration of the simula framework: a closed-loop grid of intersections with a traffic
 * light at each one and vehicles that travel the grid and may turn randomly.
 *
 * <p>The grid is {@value TrafficCoordinator#GRID_SIZE} by {@value TrafficCoordinator#GRID_SIZE} and
 * toroidal, so vehicles that leave one edge re-enter on the opposite edge, forming a closed
 * circuit. Each intersection has a light that alternates between letting north-south and east-west
 * traffic flow; a vehicle only advances when its current intersection's light is green for its
 * direction, and it may change direction randomly at an intersection. A fixed fleet of {@value
 * TrafficCoordinator#INITIAL_VEHICLES} vehicles persists and loops forever until the demo stops.
 *
 * <p>The same scenario can be run under {@link ExecutionMode#VIRTUAL} (default) or {@link
 * ExecutionMode#PLATFORM}. All movement randomness uses a fixed seed, so the same run produces the
 * same outcome in both modes, demonstrating behavioral equivalence (FR-001..FR-006, FR-008,
 * SC-003).
 *
 * <p>Usage: {@code TrafficLightDemo [mode] [display]} where {@code mode} is {@code virtual}
 * (default) or {@code classic}, and {@code display} is {@code console} (default) or {@code gui}.
 * The console display runs for {@value TrafficMonitor#SIMULATED_SECONDS} simulated seconds then
 * stops (FR-008) and prints the final outcome; the GUI display opens a Swing window that runs until
 * it is closed.
 *
 * <p>This class is demonstration code under the {@code examples} package; it is excluded from the
 * coverage gate and is not part of the framework contract (plan: runnable demo).
 */
public final class TrafficLightDemo {

  /** The time factor (simulated seconds per real second) of the root engine (FR-002). */
  private static final int TIME_FACTOR = 1;

  /** The name of the root engine (FR-002). */
  private static final String ROOT_NAME = "root";

  /** The number of simulated seconds the console demo runs before stopping (SC-003). */
  private static final int SIMULATED_SECONDS = 120;

  /** The command-line token that selects classic platform threads (FR-003). */
  private static final String CLASSIC_ARG = "classic";

  /** The command-line token that selects the Swing GUI display. */
  private static final String GUI_ARG = "gui";

  /** The milliseconds to let the last events drain after the root engine stops (FR-008). */
  private static final long DRAIN_MILLIS = 1500;

  /** The seconds to await the completion latch before giving up (SC-003). */
  private static final long AWAIT_TIMEOUT_SECONDS = 120;

  /** A duration that never ends, used when the GUI keeps the simulation running until it closes. */
  private static final int UNBOUNDED_DURATION = Integer.MAX_VALUE;

  /** A display that ignores every snapshot, used in GUI mode where the window renders instead. */
  private static final GridDisplay NO_OP_DISPLAY = state -> {};

  private TrafficLightDemo() {}

  /**
   * Entry point of the grid traffic-light demo.
   *
   * @param args optional tokens: {@code classic} selects {@link ExecutionMode#PLATFORM} (default
   *     {@link ExecutionMode#VIRTUAL}); {@code gui} selects the Swing display (default console)
   */
  public static void main(final String[] args) {
    final ExecutionMode mode = resolveMode(args);
    final boolean gui = isGui(args);
    Logger.forceLevel(Level.ERROR);

    final EngineImpl root = new EngineImpl(ROOT_NAME, TIME_FACTOR, mode);
    final GridDisplay display;
    if (gui) {
      display = NO_OP_DISPLAY;
    } else {
      display = new TrafficMonitor();
    }
    final TrafficCoordinator coordinator =
        new TrafficCoordinator(root, display, gui ? UNBOUNDED_DURATION : SIMULATED_SECONDS);
    coordinator.seed();

    root.registerAndStart(coordinator);
    root.start();

    if (gui) {
      new TrafficLightGui(coordinator).showWindow();
      return;
    }

    awaitCompletion(coordinator);
    printOutcome(mode, coordinator);

    System.exit(0);
  }

  /**
   * Resolves the execution mode from the command-line arguments.
   *
   * @param args the command-line arguments
   * @return {@link ExecutionMode#PLATFORM} if an argument is {@value #CLASSIC_ARG}, otherwise
   *     {@link ExecutionMode#VIRTUAL}
   */
  private static ExecutionMode resolveMode(final String[] args) {
    for (final String arg : args) {
      if (CLASSIC_ARG.equalsIgnoreCase(arg)) {
        return ExecutionMode.PLATFORM;
      }
    }
    return ExecutionMode.VIRTUAL;
  }

  /**
   * Returns whether the GUI display was requested.
   *
   * @param args the command-line arguments
   * @return {@code true} if an argument is {@value #GUI_ARG}
   */
  private static boolean isGui(final String[] args) {
    for (final String arg : args) {
      if (GUI_ARG.equalsIgnoreCase(arg)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Awaits the completion of the simulation.
   *
   * @param coordinator the coordinator whose completion latch is awaited
   */
  private static void awaitCompletion(final TrafficCoordinator coordinator) {
    try {
      if (!coordinator.getDoneLatch().await(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
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
   * @param coordinator the coordinator holding the final totals and fleet state
   */
  private static void printOutcome(final ExecutionMode mode, final TrafficCoordinator coordinator) {
    final StringBuilder out = new StringBuilder("\n=== OUTCOME (").append(mode).append(") ===\n");
    final GridState state = coordinator.snapshot();
    out.append("vehicles=").append(state.getVehicleCount());
    out.append(", crossings=").append(coordinator.getTotalCrossings());
    out.append('\n');
    System.out.println(out);
  }
}
