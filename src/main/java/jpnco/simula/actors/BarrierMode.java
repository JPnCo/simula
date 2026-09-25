package jpnco.simula.actors;

/**
 * Selects the behavior of a {@link Barrier} actor after it fires, i.e. after the participant count
 * is reached (FR-005, FR-006, FR-008).
 *
 * <ul>
 *   <li>{@link #SINGLE_USE}: the barrier fires once, then unsubscribes from its ready topic and no
 *       longer reacts (FR-005).
 *   <li>{@link #CYCLIC}: the barrier resets its counter after firing and can fire again on
 *       subsequent cycles (FR-006).
 * </ul>
 */
public enum BarrierMode {

  /** The barrier fires once and then deactivates (FR-005). */
  SINGLE_USE,

  /** The barrier resets after firing and can fire again on each cycle (FR-006). */
  CYCLIC
}
