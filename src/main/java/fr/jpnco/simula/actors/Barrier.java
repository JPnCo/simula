package fr.jpnco.simula.actors;

import fr.jpnco.simula.Actor;
import fr.jpnco.simula.Engine;
import fr.jpnco.simula.Event;
import fr.jpnco.simula.engine.ActorDelegate;
import fr.jpnco.simula.engine.EventImpl;
import fr.jpnco.simula.engine.IdBuilder;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A Barrier actor coordinates a set of participants by counting "ready" signals. Participants
 * signal readiness by signaling an event on the {@code readyTopic}; the Barrier subscribes to that
 * topic (FR-002). When the counted ready participants reach the configured participant count, the
 * Barrier signals the {@code completeTopic} to notify its creator (FR-003, FR-004).
 *
 * <p>The behavior after firing is selected by {@link BarrierMode} (FR-005, FR-006): in {@link
 * BarrierMode#SINGLE_USE} mode the Barrier unsubscribes from the ready topic and stops reacting
 * (FR-005); in {@link BarrierMode#CYCLIC} mode it resets its counter and can fire again (FR-006).
 * When the {@code distinct} flag is true, each distinct participant (the event's source, see
 * FR-010) counts only once even if it signals ready several times (FR-007).
 *
 * <p>The Barrier is a standard actor and uses the delegation pattern through {@link ActorDelegate},
 * like the {@link Logger} actor.
 */
public final class Barrier implements Actor {

  /** Error message when the participant count is not positive (FR-009). */
  private static final String INVALID_PARTICIPANTS = "participants must be positive";

  /** Error message when a topic is blank (FR-009). */
  private static final String INVALID_TOPIC = "topics must be non-blank";

  /** Error message when the mode is null (FR-008, FR-009). */
  private static final String INVALID_MODE = "mode must not be null";

  private final Actor delegate;
  private final Integer id;
  private final Engine engine;
  private final int participants;
  private final String readyTopic;
  private final String completeTopic;
  private final BarrierMode mode;
  private final boolean distinct;
  private final Set<Actor> readySources = new HashSet<>();
  private int count = 0;
  private boolean fired = false;

  /**
   * Builds a Barrier actor.
   *
   * @param engine the engine that runs this actor
   * @param participants the participant count to reach before firing (FR-003)
   * @param readyTopic the topic on which participants signal readiness (FR-002)
   * @param completeTopic the topic signaled when all participants are ready (FR-004)
   * @param mode the single-use or cyclic behavior (FR-005, FR-006)
   * @param distinct whether each distinct participant counts only once (FR-007)
   * @throws IllegalArgumentException if {@code participants} is not positive, a topic is blank, or
   *     {@code mode} is null (FR-008, FR-009)
   */
  public Barrier(
      final Engine engine,
      final int participants,
      final String readyTopic,
      final String completeTopic,
      final BarrierMode mode,
      final boolean distinct) {
    Objects.requireNonNull(engine);
    if (participants < 1) {
      throw new IllegalArgumentException(INVALID_PARTICIPANTS);
    }
    if (isBlank(readyTopic) || isBlank(completeTopic)) {
      throw new IllegalArgumentException(INVALID_TOPIC);
    }
    if (mode == null) {
      throw new IllegalArgumentException(INVALID_MODE);
    }
    this.engine = engine;
    this.participants = participants;
    this.readyTopic = readyTopic;
    this.completeTopic = completeTopic;
    this.mode = mode;
    this.distinct = distinct;
    delegate = ActorDelegate.createDelegate(engine, this);
    id = IdBuilder.nextId();
    subscribe(readyTopic);
  }

  /**
   * Returns whether this Barrier is equal to the given object. Two barriers are equal when they
   * have the same id.
   *
   * @param obj the object to compare with this barrier
   * @return {@code true} if the object is a barrier with the same id, {@code false} otherwise
   */
  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final Barrier other = (Barrier) obj;
    return Objects.equals(id, other.id);
  }

  /**
   * Returns the delegate that runs the standard event loop of this actor.
   *
   * @return the delegate of this barrier
   */
  @Override
  public Actor getDelegate() {
    return delegate;
  }

  /**
   * Returns the unique id of this barrier.
   *
   * @return the id of this barrier
   */
  @Override
  public Integer getId() {
    return id;
  }

  /**
   * Returns a hash code for this barrier based on its id, consistent with {@link #equals(Object)}.
   *
   * @return the hash code of this barrier
   */
  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  /**
   * Returns whether the given value is null or blank.
   *
   * @param value the value to test
   * @return {@code true} if the value is null or blank, {@code false} otherwise
   */
  private boolean isBlank(final String value) {
    return value == null || value.trim().isEmpty();
  }

  /**
   * Processes a subscribed event. A ready event (topic equals the ready topic) increments the
   * participant count, respecting the {@code distinct} flag (FR-003, FR-007). Once the count
   * reaches the participant count, the complete topic is signaled (FR-004) and the mode is applied
   * (FR-005, FR-006). Any other event is ignored.
   *
   * @param event the event to process
   */
  @Override
  public void process(final Event event) {
    Objects.requireNonNull(event);
    if (fired || !readyTopic.equals(event.getTopic())) {
      return;
    }
    final Actor source = event.getSource();
    if (distinct) {
      if (readySources.add(source)) {
        count++;
      }
    } else {
      count++;
    }
    if (count >= participants) {
      signalComplete();
      applyMode();
    }
  }

  /** Signals the complete topic to notify the creator that all participants are ready (FR-004). */
  private void signalComplete() {
    engine.signal(EventImpl.createEvent(completeTopic, this));
  }

  /** Applies the configured behavior after the barrier fires (FR-005, FR-006). */
  private void applyMode() {
    if (BarrierMode.SINGLE_USE.equals(mode)) {
      engine.unsubscribe(this, readyTopic);
      fired = true;
    } else {
      count = 0;
      readySources.clear();
    }
  }
}
