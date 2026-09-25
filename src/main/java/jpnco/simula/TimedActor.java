package jpnco.simula;

/**
 * A marker interface that identifies an actor whose events are processed in
 * reverse order of their delay. An actor implementing this interface is
 * expected to manage delayed events.
 *
 * @author Jean-Pascal Cozic
 */
public interface TimedActor extends Actor {

}
