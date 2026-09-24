package jpnco.simula.examples.trafficlight;

import jpnco.simula.Actor;
import jpnco.simula.Engine;
import jpnco.simula.Event;
import jpnco.simula.actors.Logger;
import jpnco.simula.engine.ActorDelegate;
import jpnco.simula.engine.EventImpl;
import jpnco.simula.engine.IdBuilder;

/**
 * An illustrative actor that counts vehicles passing through one intersection.
 *
 * <p>It subscribes to the {@value #VEHICLE_TOPIC} topic; each time a {@link
 * VehicleSensor#VEHICLE_TOPIC} event is processed it increments its counter and signals a {@value
 * #VEHICLE_COUNT_TOPIC} event carrying the updated count, consumed by the {@link
 * IntersectionController} (FR-004, FR-006).
 *
 * <p>This class is demonstration code under the {@code examples} package; it is excluded from the
 * coverage gate and is not part of the framework contract (plan: runnable demo).
 */
public final class VehicleSensor implements Actor {

  /** The topic on which a passing vehicle is announced (FR-004). */
  public static final String VEHICLE_TOPIC = "VEHICLE";

  /** The topic on which the sensor announces its updated count (FR-004). */
  public static final String VEHICLE_COUNT_TOPIC = "VEHICLE_COUNT";

  private final Actor delegate;
  private final Integer id;
  private int count = 0;

  public VehicleSensor(final Engine engine) {
    id = IdBuilder.nextId();
    delegate = ActorDelegate.createDelegate(engine, this);
    engine.subscribe(this, VEHICLE_TOPIC);
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
    if (VEHICLE_TOPIC.equals(event.getTopic())) {
      count++;
      Logger.info(this, "vehicle #%d\n", count);
      getEngine().signal(EventImpl.createEvent(VEHICLE_COUNT_TOPIC, this, count));
    }
  }

  /**
   * Returns the number of vehicles counted so far.
   *
   * @return the vehicle count
   */
  public int getCount() {
    return count;
  }
}
