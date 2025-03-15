package jpnco.simula.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jpnco.simula.actors.Logger;
import jpnco.simula.actors.Logger.Level;

class EngineImplTest {

	@BeforeAll
	static void setUpBeforeClass() throws Exception {
	}

	@AfterAll
	static void tearDownAfterClass() throws Exception {
	}

	@BeforeEach
	void setUp() throws Exception {
	}

	@AfterEach
	void tearDown() throws Exception {
		try {
			Thread.sleep(1000);
		} catch (final InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	@Test
	void testConstructorWithNoParent() {
		System.out.println("---------- testConstructorWithNoParent ----------");
		Logger.forceLevel(Level.TRACE);
		final String NAME = "testConstructorWithNoParent";
		final int TIME_FACTOR = 2;
		final EngineImpl engine = new EngineImpl(NAME, TIME_FACTOR);
		Logger.setActivated(engine, Level.TRACE, true);
		Logger.setActivated(engine.getTimeSource(), Level.TRACE, true);
		assertEquals(NAME, engine.getName());
		assertEquals(NAME, engine.getSimpleName());
		assertEquals(TIME_FACTOR, engine.getTimeFactor());
		assertThrows(UnsupportedOperationException.class, () -> {
			engine.getDelegate();
		});
		assertNull(engine.getParent());
		assertNotNull(engine.getLogger());
		assertNotNull(engine.getTimeSource());
		assertEquals(engine, engine.getEngine());
		engine.stop();
	}

	@Test
	void testConstructorWithParent() {
		System.out.println("---------- testConstructorWithParent ----------");
		Logger.forceLevel(Level.TRACE);
		final String CHILD_NAME = "testConstructorWithParent.child";
		final String PARENT_NAME = "testConstructorWithParent.parent";
		final int PARENT_TIME_FACTOR = 2;
		final EngineImpl parent = new EngineImpl(PARENT_NAME, PARENT_TIME_FACTOR);
		final EngineImpl child = new EngineImpl(CHILD_NAME, parent);
		Logger.setActivated(parent, Level.TRACE, true);
		Logger.setActivated(parent.getTimeSource(), Level.TRACE, true);
		Logger.setActivated(child, Level.TRACE, true);
		assertEquals(PARENT_NAME, parent.getName());
		assertEquals(PARENT_NAME, parent.getSimpleName());
		assertEquals(CHILD_NAME, child.getName());
		assertEquals(CHILD_NAME, child.getSimpleName());
		assertEquals(PARENT_TIME_FACTOR, parent.getTimeFactor());
		assertEquals(PARENT_TIME_FACTOR, child.getTimeFactor());
		assertThrows(UnsupportedOperationException.class, () -> {
			parent.getDelegate();
		});
		assertThrows(UnsupportedOperationException.class, () -> {
			child.getDelegate();
		});
		assertNull(parent.getParent());
		assertNotNull(parent.getLogger());
		assertNotNull(parent.getTimeSource());
		assertNotNull(child.getLogger());
		assertNull(child.getTimeSource());
		assertEquals(parent, child.getParent());
		assertEquals(parent, parent.getEngine());
		assertEquals(child, child.getEngine());
		parent.stop();
	}

	@Test
	void testRun() {
		System.out.println("---------- testRun ----------");
		Logger.forceLevel(Level.TRACE);
		final String NAME = "EngineTestRun";
		final int TIME_FACTOR = 2;
		final EngineImpl engine = new EngineImpl(NAME, TIME_FACTOR);
		Logger.setActivated(engine, Level.TRACE, true);
		Logger.setActivated(engine.getTimeSource(), Level.TRACE, true);
		engine.stop();
	}

}
