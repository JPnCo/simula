package fr.jpnco.simula.engine;

import java.util.Locale;

/**
 * Selects the thread execution strategy used to run actors of an engine.
 *
 * <p>The default mode is {@link #VIRTUAL} so that simulations scale to a large number of actors
 * without exhausting platform resources (FR-002). A developer may select {@link #PLATFORM} to run
 * actors on classic platform threads (FR-001, FR-003).
 *
 * @author Jean-Pascal Cozic
 */
public enum ExecutionMode {

  /** Actors run on virtual threads. This is the default execution mode (FR-002). */
  VIRTUAL,

  /** Actors run on classic (platform) threads (FR-001, FR-003). */
  PLATFORM;

  /**
   * The string form of the {@link #VIRTUAL} mode. Used to build the error message listing valid
   * modes (FR-007).
   */
  private static final String VIRTUAL_NAME = "VIRTUAL";

  /**
   * The string form of the {@link #PLATFORM} mode. Used to build the error message listing valid
   * modes (FR-007).
   */
  private static final String PLATFORM_NAME = "PLATFORM";

  /**
   * Resolves an execution mode from its case-insensitive name.
   *
   * <p>Returns the mode matching the supplied name, or throws a clear error when the name is null
   * or does not correspond to any known mode (FR-007, SC-004).
   *
   * @param name the name of the execution mode (e.g. "virtual" or "platform")
   * @return the matching {@link ExecutionMode}
   * @throws IllegalArgumentException if the name is null or unknown
   */
  public static ExecutionMode fromName(final String name) {
    if (name == null) {
      throw new IllegalArgumentException(
          "Execution mode must not be null. Valid modes: " + VIRTUAL_NAME + ", " + PLATFORM_NAME);
    }
    final String normalized = name.trim().toUpperCase(Locale.ROOT);
    for (final ExecutionMode mode : values()) {
      if (mode.name().equals(normalized)) {
        return mode;
      }
    }
    throw new IllegalArgumentException(
        "Unknown execution mode '"
            + name
            + "'. Valid modes: "
            + VIRTUAL_NAME
            + ", "
            + PLATFORM_NAME);
  }
}
