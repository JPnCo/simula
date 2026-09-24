package jpnco.simula.examples.trafficlight;

/**
 * A cardinal direction of travel on the grid. A vehicle moves one cell per step in its current
 * direction; on a toroidal grid moving off an edge wraps to the opposite edge, forming a closed
 * circuit.
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
}
