package jpnco.simula.engine;

/**
 * A convenience class to build unique ids in the platform.
 *
 * @author Jean-Pascal Cozic
 *
 */
public final class IdBuilder {

	private static int id = 0;

	public static Integer nextId() {
		return id++;
	}

}
