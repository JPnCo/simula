package jpnco.simula.examples.trafficlight;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import jpnco.simula.Actor;
import jpnco.simula.Engine;
import jpnco.simula.Event;
import jpnco.simula.engine.ActorDelegate;
import jpnco.simula.engine.IdBuilder;

/**
 * The single actor that owns the grid and advances the simulation on each {@link
 * Engine#TIME_EVENT}.
 *
 * <p>It maintains a {@value #GRID_SIZE} by {@value #GRID_SIZE} grid of intersections. Each
 * intersection has a traffic light that alternates between letting north-south traffic flow and
 * letting east-west traffic flow; the lights are staggered across the grid so movement is varied. A
 * fixed fleet of vehicles travels the grid; a vehicle only enters the next cell when its current
 * intersection's light is green for its direction, and it may turn randomly at an intersection. The
 * grid is toroidal, so vehicles that leave one edge re-enter on the opposite edge, forming a closed
 * circuit.
 *
 * <p>All randomness uses a fixed seed so that the same scenario produces the same movement in every
 * execution mode (FR-005, SC-003). After every step the coordinator publishes an immutable {@link
 * GridState} snapshot and forwards it to its {@link GridDisplay} (the console in console mode, the
 * Swing window in GUI mode).
 *
 * <p>This class is demonstration code under the {@code examples} package; it is excluded from the
 * coverage gate and is not part of the framework contract.
 */
final class TrafficCoordinator implements Actor {

  /** The width and height of the square grid. */
  static final int GRID_SIZE = 5;

  /** The length of a road segment between two consecutive intersections, in metres. */
  static final double SEGMENT_LENGTH = 250.0;

  /** The length of the green segment drawn at an intersection, in metres. */
  static final double GREEN_SEGMENT_METERS = 20.0;

  /** The lowest possible vehicle speed, in kilometres per hour. */
  private static final double MIN_SPEED_KMH = 15.0;

  /** The highest possible vehicle speed, in kilometres per hour. */
  private static final double MAX_SPEED_KMH = 45.0;

  /** The conversion factor from kilometres per hour to metres per second. */
  private static final double KMH_TO_MPS = 3.6;

  /** The number of simulated seconds a light lets one band of traffic flow before switching. */
  private static final int NS_DURATION = 3;

  /** The probability that a vehicle changes direction at an intersection. */
  private static final double TURN_PROBABILITY = 0.25;

  /** The number of vehicles that populate the grid at the start of the demo. */
  private static final int INITIAL_VEHICLES = 25;

  /** The fixed seed that makes random movement reproducible across execution modes (SC-003). */
  private static final long RANDOM_SEED = 20260924L;

  private final Actor delegate;
  private final Integer id;
  private final GridDisplay display;
  private final int durationSeconds;
  private final Random random = new Random(RANDOM_SEED);
  private final List<Vehicle> vehicles = new ArrayList<>();
  private final int[][] crossings = new int[GRID_SIZE][GRID_SIZE];
  private final CountDownLatch done = new CountDownLatch(1);
  private int simTime = 0;
  private int totalCrossings = 0;

  /** The latest immutable snapshot, published after every step (SC-003). */
  private volatile GridState state;

  TrafficCoordinator(final Engine engine, final GridDisplay display, final int durationSeconds) {
    this.display = display;
    this.durationSeconds = durationSeconds;
    id = IdBuilder.nextId();
    delegate = ActorDelegate.createDelegate(engine, this);
    engine.subscribe(this, Engine.TIME_EVENT);
  }

  @Override
  public Actor getDelegate() {
    return delegate;
  }

  @Override
  public Integer getId() {
    return id;
  }

  /** Seeds the grid with the initial fleet of vehicles at random positions and directions. */
  void seed() {
    for (int i = 0; i < INITIAL_VEHICLES; i++) {
      final double speedKmh = MIN_SPEED_KMH + random.nextDouble() * (MAX_SPEED_KMH - MIN_SPEED_KMH);
      final double speedMps = speedKmh / KMH_TO_MPS;
      vehicles.add(
          new Vehicle(
              i,
              random.nextInt(GRID_SIZE),
              random.nextInt(GRID_SIZE),
              randomDirection(),
              speedMps));
    }
  }

  /**
   * Returns whether the traffic light of the given intersection lets north-south traffic flow at
   * the given simulated time. The lights are staggered by row and column.
   *
   * @param row the row of the intersection
   * @param col the column of the intersection
   * @param time the current simulated time
   * @return {@code true} if north-south traffic may flow
   */
  boolean isNorthSouthGreen(final int row, final int col, final int time) {
    return Math.floorMod(time + row + col, 2 * NS_DURATION) < NS_DURATION;
  }

  @Override
  public void process(final Event event) {
    if (Engine.TIME_EVENT.equals(event.getTopic())) {
      simTime++;
      vehicles.forEach(vehicle -> step(vehicle, simTime));
      state = buildState();
      display.render(state);
      if (simTime >= durationSeconds) {
        totalCrossings = totalCrossings();
        getEngine().stop();
        done.countDown();
      }
    }
  }

  /**
   * Builds an immutable snapshot of the current grid.
   *
   * @return the snapshot
   */
  private GridState buildState() {
    final List<VehicleView> views = new ArrayList<>(vehicles.size());
    for (final Vehicle vehicle : vehicles) {
      views.add(
          new VehicleView(
              vehicle.getId(),
              vehicle.getRow(),
              vehicle.getCol(),
              vehicle.getDirection(),
              vehicle.getDistanceInSegment()));
    }
    final boolean[][] lights = new boolean[GRID_SIZE][GRID_SIZE];
    for (int row = 0; row < GRID_SIZE; row++) {
      for (int col = 0; col < GRID_SIZE; col++) {
        lights[row][col] = isNorthSouthGreen(row, col, simTime);
      }
    }
    return new GridState(simTime, views, lights, crossings);
  }

  /**
   * Returns the latest immutable snapshot of the grid, or {@code null} if none has been produced
   * yet.
   *
   * @return the latest snapshot, may be {@code null}
   */
  GridState snapshot() {
    return state;
  }

  /**
   * Advances one vehicle by one simulated second of travel at its fixed speed. The vehicle moves
   * along its current segment and, when it reaches the boundary, may enter the next segment if the
   * light of the next intersection is green for its direction; otherwise it stops at the boundary
   * and waits for green. It may then turn randomly.
   *
   * @param vehicle the vehicle to advance
   * @param time the current simulated time
   */
  private void step(final Vehicle vehicle, final int time) {
    vehicle.advance(vehicle.getSpeed());
    while (vehicle.getDistanceInSegment() >= SEGMENT_LENGTH) {
      final int nextRow =
          Math.floorMod(vehicle.getRow() + vehicle.getDirection().rowDelta(), GRID_SIZE);
      final int nextCol =
          Math.floorMod(vehicle.getCol() + vehicle.getDirection().colDelta(), GRID_SIZE);
      final boolean vertical = vehicle.getDirection().isVertical();
      final boolean green = isNorthSouthGreen(nextRow, nextCol, time) == vertical;
      if (!green) {
        vehicle.stopAtBoundary(SEGMENT_LENGTH);
        break;
      }
      final Direction newDirection =
          random.nextDouble() < TURN_PROBABILITY ? randomDirection() : vehicle.getDirection();
      vehicle.enterNextSegment(nextRow, nextCol, newDirection);
      crossings[nextRow][nextCol]++;
    }
  }

  /**
   * Returns a uniformly random direction.
   *
   * @return a random direction
   */
  private Direction randomDirection() {
    return Direction.values()[random.nextInt(Direction.values().length)];
  }

  /**
   * Returns the total number of crossings counted so far (a vehicle arrival increments the count of
   * the cell it enters).
   *
   * @return the total crossings
   */
  private int totalCrossings() {
    int total = 0;
    for (final int[] row : crossings) {
      for (final int value : row) {
        total += value;
      }
    }
    return total;
  }

  /**
   * Returns the final total number of crossings after the demo completes.
   *
   * @return the total crossings
   */
  int getTotalCrossings() {
    return totalCrossings;
  }

  /**
   * Returns the latch released once the demo completes (SC-003).
   *
   * @return the completion latch
   */
  CountDownLatch getDoneLatch() {
    return done;
  }
}
