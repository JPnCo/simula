package jpnco.simula;

/**
 * A marker interface that identifies an actor whose events are processed in
 * order of their priorities. An actor implementing this interface is expected
 * to be processed with priority over standard actors.
 *
 * @author Jean-Pascal Cozic
 */
public interface PriorityActor extends Actor {

}
