package jpnco.simula.examples.trafficlight;

/**
 * A sink that receives each {@link GridState} snapshot produced by the {@link TrafficCoordinator}.
 * The console mode uses the {@link TrafficMonitor}; the GUI mode supplies a display that renders on
 * the Swing event dispatch thread instead of printing.
 *
 * <p>This class is demonstration code under the {@code examples} package; it is excluded from the
 * coverage gate and is not part of the framework contract.
 */
interface GridDisplay {

  /**
   * Renders the given snapshot.
   *
   * @param state the snapshot to render
   */
  void render(GridState state);
}
