package jpnco.simula.actors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jpnco.simula.Engine;
import jpnco.simula.Event;

class LoggerTest {

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
	}

	@Test
	void testConstructor() {
		final Engine ENGINE = mock(Engine.class);
		final Logger logger = new Logger(ENGINE);
		assertEquals(ENGINE, logger.getEngine());
		assertNotNull(logger.getDelegate());
	}

	@Test
	void testPurgeQueue() {
		final Engine ENGINE = mock(Engine.class);
		final Logger logger = new Logger(ENGINE);
		final Event event = mock(Event.class);
		logger.post(event);
	}

}
