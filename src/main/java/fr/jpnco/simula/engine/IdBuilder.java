package fr.jpnco.simula.engine;

import java.util.concurrent.locks.ReentrantLock;

/**
 * A convenience class to build unique ids in the platform. Unique ids are
 * assigned to engines, actors and loggers so that each entity is identifiable
 * across the simulation.
 *
 * @author Jean-Pascal Cozic
 *
 */
public final class IdBuilder {

	/**
	 * Guards the shared counter so that ids remain unique across threads.
	 */
	private static final ReentrantLock LOCK = new ReentrantLock();

	/**
	 * The next id to hand out.
	 */
	private static int id = 0;

	/**
	 * Returns the next unique id, incrementing the shared counter under lock so
	 * that concurrent calls never produce the same id twice.
	 *
	 * @return the next unique id
	 */
	public static Integer nextId() {
		LOCK.lock();
		try {
			return id++;
		} finally {
			LOCK.unlock();
		}
	}

}
