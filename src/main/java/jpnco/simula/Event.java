package jpnco.simula;

import java.util.concurrent.Delayed;

/**
 * An event captures a fact that occurs in the system at a given instant. It is
 * signaled on a given topic and holds a set of specific parameters that defines
 * the semantic of such an event. An event is built by an actor and then
 * signaled through the engine of this actor to the concerned actors.
 * <p>
 * There are three kinds of event:
 * <li>standard events that are process in order of signal times;
 * <li>prioritized events that are process in order of their priorities;
 * <li>delayed events that are process in reverse order of their delay.
 * <p>
 * An actor concerned by a given event must subscribe to the relative topic of
 * this kind of event.
 * <p>
 * An event can be signaled on several topics by one or several actors.
 *
 * @author Jean-Pascal Cozic
 *
 */
public interface Event extends Cloneable, Delayed {

	/**
	 * Duplicate an event. This method is used to forward event between two
	 * engines.
	 *
	 * @param source the source of the clone
	 * @return the cloned event
	 */
	Event duplicate(Actor source);

	/**
	 * Returns the specific parameters of this event.
	 *
	 * @return the specific parameters of this event.
	 */
	Object[] getParameters();

	/**
	 * Returns the priority of this event.
	 *
	 * @return the priority of this event
	 */
	int getPriority();

	/**
	 * Returns the actor that have signaled this event
	 *
	 * @return the actor that have signaled this event
	 */
	Actor getSource();

	/**
	 * Returns the time at which the event has been signaled
	 *
	 * @return the time at which the event has been signaled
	 */
	int getTime();

	/**
	 * Returns the topic associated to this event.
	 *
	 * @return the topic associated to this event.
	 */
	String getTopic();

	/**
	 * Returns <code>true</code> is the event is delayed <code>false</code>
	 * otherwise
	 *
	 * @return <code>true</code> is the event is delayed <code>false</code>
	 *         otherwise
	 */
	boolean isDelayed();

	/**
	 * Returns <code>true</code> is the event is prioritized <code>false</code>
	 * otherwise
	 *
	 * @return <code>true</code> is the event is prioritized <code>false</code>
	 *         otherwise
	 */
	boolean isPrioritized();
}
