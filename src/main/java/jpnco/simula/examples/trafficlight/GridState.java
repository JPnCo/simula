package jpnco.simula.examples.trafficlight;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An immutable snapshot of the grid at one simulated instant, published by the {@link
 * TrafficCoordinator} after every step. It is read by the console and GUI displays, which may run
 * on a different thread than the coordinator.
 *
 * <p>This class is demonstration code under the {@code examples} package; it is excluded from the
 * coverage gate and is not part of the framework contract.
 */
final class GridState {

  /** The simulated time at which this snapshot was taken. */
  private final int simTime;

  /** The number of vehicles in the fleet. */
  private final int vehicleCount;

  /** The positions and directions of the vehicles at this instant. */
  private final List<VehicleView> vehicles;

  /** Whether each intersection lets north-south traffic flow at this instant. */
  private final boolean[][] northSouthGreen;

  /** The per-cell number of vehicle arrivals so far. */
  private final int[][] crossings;

  GridState(
      final int simTime,
      final List<VehicleView> vehicles,
      final boolean[][] northSouthGreen,
      final int[][] crossings) {
    this.simTime = simTime;
    this.vehicleCount = vehicles.size();
    this.vehicles = Collections.unmodifiableList(new ArrayList<>(vehicles));
    this.northSouthGreen = copy(northSouthGreen);
    this.crossings = copy(crossings);
  }

  /**
   * Returns the simulated time of this snapshot.
   *
   * @return the simulated time
   */
  int getSimTime() {
    return simTime;
  }

  /**
   * Returns the number of vehicles in the fleet.
   *
   * @return the vehicle count
   */
  int getVehicleCount() {
    return vehicleCount;
  }

  /**
   * Returns an unmodifiable view of the vehicles.
   *
   * @return the vehicles
   */
  List<VehicleView> getVehicles() {
    return vehicles;
  }

  /**
   * Returns whether the intersection at the given cell lets north-south traffic flow.
   *
   * @param row the row
   * @param col the column
   * @return {@code true} if north-south traffic may flow at this cell
   */
  boolean isNorthSouthGreen(final int row, final int col) {
    return northSouthGreen[row][col];
  }

  /**
   * Returns the number of vehicle arrivals at the given cell so far.
   *
   * @param row the row
   * @param col the column
   * @return the crossing count
   */
  int crossingsAt(final int row, final int col) {
    return crossings[row][col];
  }

  private static boolean[][] copy(final boolean[][] source) {
    final boolean[][] copy = new boolean[source.length][];
    for (int i = 0; i < source.length; i++) {
      copy[i] = source[i].clone();
    }
    return copy;
  }

  private static int[][] copy(final int[][] source) {
    final int[][] copy = new int[source.length][];
    for (int i = 0; i < source.length; i++) {
      copy[i] = source[i].clone();
    }
    return copy;
  }
}
