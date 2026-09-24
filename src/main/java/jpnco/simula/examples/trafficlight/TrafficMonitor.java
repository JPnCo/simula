package jpnco.simula.examples.trafficlight;

/**
 * The console {@link GridDisplay} for the demo: it prints the grid state each simulated second to
 * {@link System#out}.
 *
 * <p>This class is demonstration code under the {@code examples} package; it is excluded from the
 * coverage gate and is not part of the framework contract.
 */
public final class TrafficMonitor implements GridDisplay {

  /** The marker used to render a north-south green light. */
  private static final char NS_LIGHT = '|';

  /** The marker used to render an east-west green light. */
  private static final char EW_LIGHT = '-';

  /** The marker used to render an intersection with no vehicle present. */
  private static final char EMPTY = '.';

  @Override
  public void render(final GridState state) {
    final int[][] occupancy = new int[TrafficCoordinator.GRID_SIZE][TrafficCoordinator.GRID_SIZE];
    for (final VehicleView vehicle : state.getVehicles()) {
      occupancy[vehicle.getRow()][vehicle.getCol()]++;
    }
    final StringBuilder grid = new StringBuilder("t=").append(state.getSimTime()).append('\n');
    for (int row = 0; row < TrafficCoordinator.GRID_SIZE; row++) {
      for (int col = 0; col < TrafficCoordinator.GRID_SIZE; col++) {
        final char light = state.isNorthSouthGreen(row, col) ? NS_LIGHT : EW_LIGHT;
        final int vehiclesHere = occupancy[row][col];
        grid.append(light).append(vehiclesHere > 0 ? vehiclesHere : EMPTY).append(' ');
      }
      grid.append('\n');
    }
    System.out.println(grid);
  }
}
