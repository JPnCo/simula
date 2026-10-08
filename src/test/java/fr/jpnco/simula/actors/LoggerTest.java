package fr.jpnco.simula.actors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import fr.jpnco.simula.Actor;
import fr.jpnco.simula.Engine;
import fr.jpnco.simula.Event;
import fr.jpnco.simula.actors.Logger.Level;
import fr.jpnco.simula.engine.EventImpl;

/**
 * Tests for the {@link Logger} actor, covering the static logging methods,
 * level activation, event processing and queue purging.
 */
class LoggerTest {

	private Engine engine;
	private Logger logger;
	private Actor source;

	@BeforeAll
	static void setUpBeforeClass() throws Exception {
	}

	@AfterAll
	static void tearDownAfterClass() throws Exception {
	}

	@BeforeEach
	void setUp() throws Exception {
		Logger.forceLevel(null);
		engine = mock(Engine.class);
		when(engine.getName()).thenReturn("engine");
		when(engine.getTime()).thenReturn(100);
		logger = new Logger(engine);
		source = mock(Actor.class);
		when(source.getEngine()).thenReturn(engine);
		when(source.getName()).thenReturn("source:engine");
		when(source.getSimpleName()).thenReturn("source");
	}

	@AfterEach
	void tearDown() throws Exception {
	}

	@Test
	void testConstructor() {
		assertEquals(engine, logger.getEngine());
		assertNotNull(logger.getDelegate());
		assertNotNull(logger.getId());
	}

	@Test
	void testEqualsAndHashCode() {
		final Logger other = new Logger(engine);
		assertEquals(logger, logger);
		assertNotEquals(logger, null);
		assertNotEquals(logger, new Object());
		assertNotEquals(logger, other);
		assertEquals(logger.hashCode(), logger.hashCode());
	}

	@Test
	void testGetSimpleNameAndGetDelegate() {
		assertEquals(Logger.class.getSimpleName(), logger.getSimpleName());
		assertNotNull(logger.getDelegate());
	}

	@Test
	void testStaticLogMethodsSignalWhenActivated() {
		Logger.setActivated(source, Level.TRACE, true);
		Logger.debug(source, "debug %s\n", "x");
		Logger.error(source, "error\n");
		Logger.info(source, "info\n");
		Logger.trace(source, "trace\n");
		Logger.warning(source, "warn\n");
		Logger.probe(source, "probe\n");
		verify(engine, atLeast(6)).signal(any(Event.class));
	}

	@Test
	void testLogNotActivatedDoesNothing() {
		Logger.forceLevel(null);
		Logger.setActivated(source, Level.ERROR, true);
		Logger.debug(source, "debug\n");
		verify(engine, never()).signal(any(Event.class));
	}

	@Test
	void testForceLevel() {
		Logger.forceLevel(Level.ERROR);
		assertTrue(Logger.isActivated(source, Level.ERROR));
		assertTrue(Logger.isActivated(source, Level.PROBE));
		assertFalse(Logger.isActivated(source, Level.DEBUG));
		Logger.forceLevel(null);
	}

	@Test
	void testIsActivatedWithDefaultActivation() {
		Logger.forceLevel(null);
		assertFalse(Logger.isActivated(source, Level.DEBUG));
		assertTrue(Logger.isActivated(source, Level.ERROR));
		assertTrue(Logger.isActivated(source, Level.PROBE));
	}

	@Test
	void testSetActivatedCreatesAndReusesActivation() {
		Logger.forceLevel(null);
		Logger.setActivated(source, Level.TRACE, true);
		assertTrue(Logger.isActivated(source, Level.TRACE));
		assertTrue(Logger.isActivated(source, Level.DEBUG));
		Logger.setActivated(source, Level.ERROR, true);
		assertFalse(Logger.isActivated(source, Level.DEBUG));
		assertTrue(Logger.isActivated(source, Level.ERROR));
	}

	@Test
	void testLogToLoggerSourcePrintsWithoutSignaling() {
		Logger.forceLevel(null);
		Logger.setActivated(logger, Level.DEBUG, true);
		Logger.debug(logger, "format %d\n", 42);
		verify(engine, never()).signal(any(Event.class));
	}

	@Test
	void testProcessLogEventNonProbe() {
		final Event event = EventImpl.createEvent(Engine.LOG_EVENT, source, Level.DEBUG, "msg %d\n", 7);
		logger.process(event);
	}

	@Test
	void testProcessLogEventProbe() {
		final Event event = EventImpl.createEvent(Engine.LOG_EVENT, source, Level.PROBE, "msg %d\n", 8);
		logger.process(event);
	}

	@Test
	void testProcessPurgeQueueEvent() throws Exception {
		final BlockingQueue<Event> queue = mock(BlockingQueue.class);
		when(queue.poll(anyLong(), any(TimeUnit.class))).thenReturn(null);
		final Event event = EventImpl.createEvent(Engine.PURGE_QUEUE_EVENT, source, queue);
		logger.process(event);
	}

	@Test
	void testProcessOtherTopicDoesNothing() {
		final Event event = EventImpl.createEvent("OTHER", source);
		logger.process(event);
	}

	@Test
	void testPurgeQueueProcessesEventsUntilEmpty() throws Exception {
		final Event first = EventImpl.createEvent(Engine.LOG_EVENT, source, Level.INFO, "one\n");
		final Event second = EventImpl.createEvent(Engine.LOG_EVENT, source, Level.WARNING, "two\n");
		final BlockingQueue<Event> queue = mock(BlockingQueue.class);
		when(queue.poll(anyLong(), any(TimeUnit.class))).thenReturn(first, second, null);
		logger.purgeQueue(queue);
		verify(queue, times(3)).poll(anyLong(), any(TimeUnit.class));
	}

	@Test
	void testPurgeQueueOnEmptyQueue() throws Exception {
		final BlockingQueue<Event> queue = mock(BlockingQueue.class);
		when(queue.poll(anyLong(), any(TimeUnit.class))).thenReturn(null);
		logger.purgeQueue(queue);
		verify(queue, times(1)).poll(anyLong(), any(TimeUnit.class));
	}

	@Test
	void testPurgeQueueHandlesInterruptedExceptionOnFirstPoll() throws Exception {
		final BlockingQueue<Event> queue = mock(BlockingQueue.class);
		when(queue.poll(anyLong(), any(TimeUnit.class))).thenThrow(new InterruptedException());
		logger.purgeQueue(queue);
		Thread.interrupted();
	}

	@Test
	void testPurgeQueueHandlesInterruptedExceptionInLoop() throws Exception {
		final Event first = EventImpl.createEvent(Engine.LOG_EVENT, source, Level.INFO, "one\n");
		final BlockingQueue<Event> queue = mock(BlockingQueue.class);
		when(queue.poll(anyLong(), any(TimeUnit.class))).thenReturn(first)
				.thenThrow(new InterruptedException()).thenReturn(null);
		logger.purgeQueue(queue);
		Thread.interrupted();
	}

}
