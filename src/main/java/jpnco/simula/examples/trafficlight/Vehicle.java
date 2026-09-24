package jpnco.simula.examples.trafficlight;

/**
 * A single vehicle travelling on the grid. It holds an identity, a current cell, a direction, a
 * fixed speed and the distance already travelled into the current segment. Vehicles persist for the
 * whole demo and loop forever around the toroidal grid, which is what makes the network a closed
 * circuit.
 *
 * <p>This class is demonstration code under the {@code examples} package; it is excluded from the
 * coverage gate and is not part of the framework contract.
 */
final class Vehicle {

  /** The unique identity of the vehicle. */
  private final int id;

  /** The fixed speed of the vehicle in metres per second (chosen between 15 and 45 km/h). */
  private final double speed;

  /** The current row of the vehicle (the origin of the segment it is on). */
  private int row;

  /** The current column of the vehicle (the origin of the segment it is on). */
  private int col;

  /** The current direction of the vehicle. */
  private Direction direction;

  /** The distance already travelled into the current segment, in metres. */
  private double distanceInSegment;

  Vehicle(
      final int id, final int row, final int col, final Direction direction, final double speed) {
    this.id = id;
    this.row = row;
    this.col = col;
    this.direction = direction;
    this.speed = speed;
  }

  /**
   * Returns the identity of the vehicle.
   *
   * @return the vehicle id
   */
  int getId() {
    return id;
  }

  /**
   * Returns the fixed speed of the vehicle in metres per second.
   *
   * @return the speed
   */
  double getSpeed() {
    return speed;
  }

  /**
   * Returns the current row of the vehicle.
   *
   * @return the row
   */
  int getRow() {
    return row;
  }

  /**
   * Returns the current column of the vehicle.
   *
   * @return the column
   */
  int getCol() {
    return col;
  }

  /**
   * Returns the current direction of the vehicle.
   *
   * @return the direction
   */
  Direction getDirection() {
    return direction;
  }

  /**
   * Returns the distance already travelled into the current segment, in metres.
   *
   * @return the distance in the segment
   */
  double getDistanceInSegment() {
    return distanceInSegment;
  }

  /**
   * Advances the vehicle by the given number of metres along its current segment.
   *
   * @param metres the distance to travel
   */
  void advance(final double metres) {
    distanceInSegment += metres;
  }

  /**
   * Wraps the vehicle onto the next segment: it moves to the given neighbour cell, resets the
   * travelled distance and updates its direction.
   *
   * @param nextRow the row of the next intersection
   * @param nextCol the column of the next intersection
   * @param newDirection the new direction
   */
  void enterNextSegment(final int nextRow, final int nextCol, final Direction newDirection) {
    row = nextRow;
    col = nextCol;
    direction = newDirection;
    distanceInSegment = 0;
  }

  /**
   * Clamps the travelled distance so the vehicle stops exactly at the boundary of its segment (at
   * the next intersection).
   *
   * @param segmentLength the length of a segment in metres
   */
  void stopAtBoundary(final double segmentLength) {
    distanceInSegment = segmentLength;
  }
}
