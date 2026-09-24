package jpnco.simula.examples.trafficlight;

/**
 * A cardinal direction of travel on the grid: north, south, east or west, each with a row and
 * column delta. A vehicle travels segment by segment; at an edge it cannot leave the grid and must
 * turn right or left (see {@link TrafficCoordinator#chooseNextDirection}).
 *
 * <p>This class is demonstration code under the {@code examples} package; it is excluded from the
 * coverage gate and is not part of the framework contract.
 */
enum Direction {

  /** Moving one cell upward (decreasing row). */
  NORTH(-1, 0),

  /** Moving one cell downward (increasing row). */
  SOUTH(1, 0),

  /** Moving one cell right (increasing column). */
  EAST(0, 1),

  /** Moving one cell left (decreasing column). */
  WEST(0, -1);

  /** The row delta of this direction. */
  private final int rowDelta;

  /** The column delta of this direction. */
  private final int colDelta;

  /** The message used when an unhandled direction reaches an exhaustive switch. */
  private static final String UNHANDLED_MESSAGE = "Unhandled direction ";

  Direction(final int rowDelta, final int colDelta) {
    this.rowDelta = rowDelta;
    this.colDelta = colDelta;
  }

  /**
   * Returns the row delta of this direction.
   *
   * @return the row delta
   */
  int rowDelta() {
    return rowDelta;
  }

  /**
   * Returns the column delta of this direction.
   *
   * @return the column delta
   */
  int colDelta() {
    return colDelta;
  }

  /**
   * Returns whether this direction travels north or south, i.e. uses the north-south band of a
   * traffic light.
   *
   * @return {@code true} for a vertical (north/south) direction
   */
  boolean isVertical() {
    return this == NORTH || this == SOUTH;
  }

  /**
   * Returns the direction obtained by turning right (clockwise) from this one.
   *
   * @return the clockwise neighbour direction
   */
  Direction turnRight() {
    switch (this) {
      case NORTH:
        return EAST;
      case EAST:
        return SOUTH;
      case SOUTH:
        return WEST;
      case WEST:
        return NORTH;
      default:
        throw new IllegalStateException(UNHANDLED_MESSAGE + this);
    }
  }

  /**
   * Returns the direction obtained by turning left (counter-clockwise) from this one.
   *
   * @return the counter-clockwise neighbour direction
   */
  Direction turnLeft() {
    switch (this) {
      case NORTH:
        return WEST;
      case WEST:
        return SOUTH;
      case SOUTH:
        return EAST;
      case EAST:
        return NORTH;
      default:
        throw new IllegalStateException(UNHANDLED_MESSAGE + this);
    }
  }
}
