package jpnco.simula.engine;

import java.util.concurrent.locks.ReentrantLock;

/**
 * A convenience class to build unique ids in the platform.
 *
 * @author Jean-Pascal Cozic
 *
 */
public final class IdBuilder {

	private static final ReentrantLock LOCK = new ReentrantLock();

	private static int id = 0;

	public static Integer nextId() {
		LOCK.lock();
		try {
			return id++;
		} finally {
			LOCK.unlock();
		}
	}

}
