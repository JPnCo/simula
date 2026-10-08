package fr.jpnco.simula.actors;

import fr.jpnco.simula.Actor;

/**
 * An observer of the lifecycle transitions recorded by a {@link SimulaSupervisor} (FR-002).
 *
 * <p>A listener is registered with {@link
 * SimulaSupervisor#addSupervisionListener(SupervisionListener)} and unregistered with {@link
 * SimulaSupervisor#removeSupervisionListener(SupervisionListener)}. It is invoked once per actual
 * status transition, synchronously, in the thread that recorded the transition — which may be any
 * thread — after the new status is visible in {@link SimulaSupervisor#getStates()} (FR-003).
 *
 * <p>Implementations MUST be thread-safe and SHOULD NOT block: they run in the recording thread of
 * a live simulation. A {@link RuntimeException} thrown by a listener is caught and logged as an
 * error by the supervisor; it never affects the recorded state nor the other listeners (FR-005).
 *
 * @author Jean-Pascal Cozic
 */
@FunctionalInterface
public interface SupervisionListener {

  /**
   * Notifies that the recorded status of a component changed.
   *
   * @param component the observed actor or engine whose status changed
   * @param previous the status recorded before this change, or {@code null} if the component was
   *     never recorded before
   * @param current the newly recorded status, already visible in {@link
   *     SimulaSupervisor#getStates()}
   */
  void statusChanged(
      Actor component, SimulaSupervisor.Status previous, SimulaSupervisor.Status current);
}
