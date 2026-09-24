package jpnco.simula.examples.trafficlight;

import jpnco.simula.Actor;
import jpnco.simula.Engine;
import jpnco.simula.Event;
import jpnco.simula.actors.Logger;
import jpnco.simula.engine.ActorDelegate;
import jpnco.simula.engine.EventImpl;
import jpnco.simula.engine.IdBuilder;

/**
 * An illustrative actor that cycles a traffic light through {@link State#RED}, {@link State#GREEN}
 * and {@link State#ORANGE}.
 *
 * <p>The light advances one state each time it processes a {@link Engine#TIME_EVENT} and the number
 * of simulated seconds spent in the current state reaches its configured duration (FR-004, FR-006).
 * On each transition it signals a {@value #LIGHT_CHANGED} event on its own engine, which is
 * consumed by the {@link IntersectionController} of the same intersection.
 *
 * <p>This class is demonstration code under the {@code examples} package; it is excluded from the
 * coverage gate and is not part of the framework contract (plan: runnable demo).
 */
public final class TrafficLight implements Actor {

  /** The topic on which a light announces a state change (FR-004). */
  public static final String LIGHT_CHANGED_TOPIC = "LIGHT_CHANGED";

  /** The number of simulated seconds the light stays red (FR-004). */
  private static final int RED_DURATION = 2;

  /** The number of simulated seconds the light stays green (FR-004). */
  private static final int GREEN_DURATION = 2;

  /** The number of simulated seconds the light stays orange (FR-004). */
  private static final int ORANGE_DURATION = 1;

  /** The initial state of the light (FR-004). */
  private static final State INITIAL_STATE = State.RED;

  /** The {@link State} values in clockwise order (FR-004). */
  private static final State[] STATES = State.values();

  private final Actor delegate;
  private final Integer id;
  private State state = INITIAL_STATE;
  private int secondsInState = 0;

  public TrafficLight(final Engine engine) {
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

  @Override
  public void process(final Event event) {
    if (Engine.TIME_EVENT.equals(event.getTopic())) {
      secondsInState++;
      if (secondsInState >= durationOf(state)) {
        advance();
      }
    }
  }

  /** Advances the light to the next state, announces it and resets the state timer (FR-004). */
  private void advance() {
    state = STATES[(state.ordinal() + 1) % STATES.length];
    secondsInState = 0;
    Logger.info(this, "light=%s\n", state);
    getEngine().signal(EventImpl.createEvent(LIGHT_CHANGED_TOPIC, this, state));
  }

  /**
   * Returns the number of simulated seconds a given state lasts.
   *
   * @param lightState the state whose duration is requested
   * @return the duration in simulated seconds
   */
  private static int durationOf(final State lightState) {
    switch (lightState) {
      case GREEN:
        return GREEN_DURATION;
      case ORANGE:
        return ORANGE_DURATION;
      case RED:
      default:
        return RED_DURATION;
    }
  }

  /** The possible states of a traffic light (FR-004). */
  public enum State {
    RED,
    GREEN,
    ORANGE
  }
}
