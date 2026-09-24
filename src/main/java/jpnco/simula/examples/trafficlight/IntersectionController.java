package jpnco.simula.examples.trafficlight;

import jpnco.simula.Actor;
import jpnco.simula.Engine;
import jpnco.simula.Event;
import jpnco.simula.actors.Logger;
import jpnco.simula.engine.ActorDelegate;
import jpnco.simula.engine.EventImpl;
import jpnco.simula.engine.IdBuilder;

/**
 * An illustrative actor that owns the local state of one intersection and forwards it to the root
 * {@link TrafficMonitor}.
 *
 * <p>It subscribes to the light and sensor topics of its own intersection, tracks the last known
 * light state and the aggregated vehicle count, emits one {@value VehicleSensor#VEHICLE_TOPIC}
 * event per simulated second (simulating traffic) and, on each {@link Engine#TIME_EVENT}, signals a
 * {@value #SUMMARY_TOPIC_PREFIX}-prefixed summary event on its parent engine so the root monitor
 * can aggregate all intersections (FR-004, FR-008).
 *
 * <p>This class is demonstration code under the {@code examples} package; it is excluded from the
 * coverage gate and is not part of the framework contract (plan: runnable demo).
 */
public final class IntersectionController implements Actor {

  /** The prefix of the summary topic forwarded to the root monitor (FR-008). */
  public static final String SUMMARY_TOPIC_PREFIX = "SUMMARY_";

  private final Actor delegate;
  private final Integer id;
  private final String name;
  private final Engine engine;
  private TrafficLight.State lightState = TrafficLight.State.RED;
  private int vehicleCount = 0;

  public IntersectionController(final Engine engine, final String name) {
    this.engine = engine;
    this.name = name;
    id = IdBuilder.nextId();
    delegate = ActorDelegate.createDelegate(engine, this);
    engine.subscribe(this, TrafficLight.LIGHT_CHANGED_TOPIC);
    engine.subscribe(this, VehicleSensor.VEHICLE_COUNT_TOPIC);
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
    final String topic = event.getTopic();
    if (TrafficLight.LIGHT_CHANGED_TOPIC.equals(topic)) {
      lightState = (TrafficLight.State) event.getParameters()[0];
    } else if (VehicleSensor.VEHICLE_COUNT_TOPIC.equals(topic)) {
      vehicleCount = (Integer) event.getParameters()[0];
    } else if (Engine.TIME_EVENT.equals(topic)) {
      getEngine().signal(EventImpl.createEvent(VehicleSensor.VEHICLE_TOPIC, this));
      forwardSummary();
    }
  }

  /** Forwards the current intersection state to the root monitor (FR-008). */
  private void forwardSummary() {
    final Engine parent = engine.getParent();
    if (parent != null) {
      Logger.info(this, "summary %s light=%s vehicles=%d\n", name, lightState, vehicleCount);
      parent.signal(
          EventImpl.createEvent(SUMMARY_TOPIC_PREFIX + name, this, lightState, vehicleCount));
    }
  }

  /**
   * Returns the name of this intersection.
   *
   * @return the intersection name
   */
  public String getName() {
    return name;
  }

  /**
   * Returns the current light state.
   *
   * @return the light state
   */
  public TrafficLight.State getLightState() {
    return lightState;
  }

  /**
   * Returns the current vehicle count.
   *
   * @return the vehicle count
   */
  public int getVehicleCount() {
    return vehicleCount;
  }
}
