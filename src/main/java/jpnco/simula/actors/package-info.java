
/**
 * This packages contains all the SIMULA default actors :
 * <li>Logger: this actor is used to log actors's activity
 * <li>TimeSource: this actor is used to signaled time event. by default, one
 * event by "simulated second" is signaled.
 * <li>Barrier: this actor coordinates a set of participants by counting ready
 * signals and notifying its creator when all participants are ready.
 * <li>SimulaSupervisor: this actor observes the startup and shutdown of actors
 * and engines and exposes a queryable state of the components it has observed.
 * 
 * @author Jean-Pascal Cozic
 *
 */
package jpnco.simula.actors;