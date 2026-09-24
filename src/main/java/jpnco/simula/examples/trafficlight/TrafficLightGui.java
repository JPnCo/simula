package jpnco.simula.examples.trafficlight;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * A Swing (Java2D) window that visualizes the {@code trafficlight} grid simulation in real time.
 *
 * <p>A {@link Timer} polls the {@link TrafficCoordinator} snapshot on the Event Dispatch Thread and
 * repaints the panel. Each cell of the grid draws its two road bands (north-south and east-west)
 * and a short green segment (about {@value TrafficCoordinator#GREEN_SEGMENT_METERS} metres) on the
 * band whose traffic light is green; every vehicle is drawn as a dot at its current position along
 * its segment. The status line shows the simulated time, the vehicle count and the total crossings
 * so far.
 *
 * <p>This class is demonstration code under the {@code examples} package; it is excluded from the
 * coverage gate and is not part of the framework contract.
 */
final class TrafficLightGui extends JFrame {

  /** The window title. */
  private static final String TITLE = "Traffic Light Grid";

  /** The default window width in pixels. */
  private static final int WIDTH = 720;

  /** The default window height in pixels. */
  private static final int HEIGHT = 540;

  /** The milliseconds between two repaints (SC-003). */
  private static final int REPAINT_INTERVAL_MILLIS = 100;

  /** The color of a green light segment. */
  private static final Color GREEN = new Color(34, 139, 34);

  /** The color of the road surface. */
  private static final Color ROAD = new Color(60, 60, 60);

  /** The color of the road bands within a cell. */
  private static final Color BAND = new Color(90, 90, 90);

  /** The color of a vehicle. */
  private static final Color VEHICLE = new Color(255, 215, 0);

  private static final long serialVersionUID = 1L;

  private final GridPanel grid;
  private final JLabel status;

  TrafficLightGui(final TrafficCoordinator coordinator) {
    super(TITLE);
    setDefaultCloseOperation(EXIT_ON_CLOSE);
    setLayout(new BorderLayout());

    grid = new GridPanel(coordinator);
    grid.setPreferredSize(new Dimension(WIDTH, HEIGHT - 60));
    add(grid, BorderLayout.CENTER);

    status = new JLabel(" ");
    status.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
    status.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
    add(status, BorderLayout.SOUTH);

    pack();
    setLocationRelativeTo(null);

    final Timer timer = new Timer(REPAINT_INTERVAL_MILLIS, e -> refresh());
    timer.start();
  }

  /** Refreshes the grid and the status line from the latest snapshot. */
  private void refresh() {
    grid.repaint();
    final GridState state = grid.coordinator.snapshot();
    if (state != null) {
      status.setText(
          "t="
              + state.getSimTime()
              + "   vehicles="
              + state.getVehicleCount()
              + "   totalCrossings="
              + totalCrossings(state));
    }
  }

  private static int totalCrossings(final GridState state) {
    int total = 0;
    for (int row = 0; row < TrafficCoordinator.GRID_SIZE; row++) {
      for (int col = 0; col < TrafficCoordinator.GRID_SIZE; col++) {
        total += state.crossingsAt(row, col);
      }
    }
    return total;
  }

  /** Shows the window on the Event Dispatch Thread. */
  void showWindow() {
    SwingUtilities.invokeLater(
        () -> {
          setVisible(true);
        });
  }

  /** The panel that draws the grid with Java2D. */
  private static final class GridPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final TrafficCoordinator coordinator;

    GridPanel(final TrafficCoordinator coordinator) {
      this.coordinator = coordinator;
      setBackground(new Color(24, 24, 28));
    }

    @Override
    protected void paintComponent(final Graphics g) {
      super.paintComponent(g);
      final Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      final GridState state = coordinator.snapshot();
      final int cell = Math.min(getWidth(), getHeight()) / TrafficCoordinator.GRID_SIZE;
      final int offsetX = (getWidth() - cell * TrafficCoordinator.GRID_SIZE) / 2;
      final int offsetY = (getHeight() - cell * TrafficCoordinator.GRID_SIZE) / 2;

      if (state != null) {
        drawGrid(g2, state, cell, offsetX, offsetY);
      }

      g2.dispose();
    }

    private void drawGrid(
        final Graphics2D g2,
        final GridState state,
        final int cell,
        final int offsetX,
        final int offsetY) {
      for (int row = 0; row < TrafficCoordinator.GRID_SIZE; row++) {
        for (int col = 0; col < TrafficCoordinator.GRID_SIZE; col++) {
          drawCell(g2, state, row, col, cell, offsetX, offsetY);
        }
      }
      drawVehicles(g2, state, cell, offsetX, offsetY);
    }

    private void drawCell(
        final Graphics2D g2,
        final GridState state,
        final int row,
        final int col,
        final int cell,
        final int offsetX,
        final int offsetY) {
      final int x = offsetX + col * cell;
      final int y = offsetY + row * cell;

      g2.setColor(ROAD);
      g2.fillRect(x, y, cell, cell);

      g2.setColor(BAND);
      g2.setStroke(new BasicStroke(2f));
      g2.drawRect(x, y, cell, cell);

      final int centerX = x + cell / 2;
      final int centerY = y + cell / 2;
      final int band = cell / 6;

      g2.setColor(BAND);
      g2.fillRect(centerX - band / 2, y, band, cell);
      g2.fillRect(x, centerY - band / 2, cell, band);

      final int greenLen =
          (int)
              Math.round(
                  cell
                      * TrafficCoordinator.GREEN_SEGMENT_METERS
                      / TrafficCoordinator.SEGMENT_LENGTH);
      g2.setColor(GREEN);
      if (state.isNorthSouthGreen(row, col)) {
        g2.fillRect(centerX - band / 2, centerY - greenLen / 2, band, greenLen);
      } else {
        g2.fillRect(centerX - greenLen / 2, centerY - band / 2, greenLen, band);
      }
    }

    private void drawVehicles(
        final Graphics2D g2,
        final GridState state,
        final int cell,
        final int offsetX,
        final int offsetY) {
      final int radius = Math.max(4, cell / 10);
      g2.setColor(VEHICLE);
      for (final VehicleView vehicle : state.getVehicles()) {
        final double fraction = vehicle.getDistanceInSegment() / TrafficCoordinator.SEGMENT_LENGTH;
        final double cx =
            offsetX
                + (vehicle.getCol() + 0.5) * cell
                + vehicle.getDirection().colDelta() * fraction * cell;
        final double cy =
            offsetY
                + (vehicle.getRow() + 0.5) * cell
                + vehicle.getDirection().rowDelta() * fraction * cell;
        g2.fillOval(
            (int) Math.round(cx - radius), (int) Math.round(cy - radius), 2 * radius, 2 * radius);
      }
    }
  }
}
