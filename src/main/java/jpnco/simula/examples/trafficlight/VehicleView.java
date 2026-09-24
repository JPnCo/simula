package jpnco.simula.examples.trafficlight;

/**
 * An immutable view of a vehicle's position and direction at one instant, carried inside a {@link
 * GridState} so displays can read it safely from another thread.
 *
 * <p>This class is demonstration code under the {@code examples} package; it is excluded from the
 * coverage gate and is not part of the framework contract.
 */
final class VehicleView {

  /** The vehicle identity. */
  private final int id;

  /** The vehicle row. */
  private final int row;

  /** The vehicle column. */
  private final int col;

  /** The vehicle direction. */
  private final Direction direction;

  /** The distance already travelled into the current segment, in metres. */
  private final double distanceInSegment;

  VehicleView(
      final int id,
      final int row,
      final int col,
      final Direction direction,
      final double distanceInSegment) {
    this.id = id;
    this.row = row;
    this.col = col;
    this.direction = direction;
    this.distanceInSegment = distanceInSegment;
  }

  /**
   * Returns the vehicle identity.
   *
   * @return the vehicle id
   */
  int getId() {
    return id;
  }

  /**
   * Returns the vehicle row.
   *
   * @return the row
   */
  int getRow() {
    return row;
  }

  /**
   * Returns the vehicle column.
   *
   * @return the column
   */
  int getCol() {
    return col;
  }

  /**
   * Returns the vehicle direction.
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
}
