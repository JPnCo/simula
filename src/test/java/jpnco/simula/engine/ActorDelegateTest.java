package jpnco.simula.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import jpnco.simula.Actor;
import jpnco.simula.Engine;
import jpnco.simula.Event;
import jpnco.simula.actors.Logger;
import jpnco.simula.actors.Logger.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Tests for {@link ActorDelegate}, covering delegation factories, the event queue, run loops
 * (before/after start), purging and equality.
 */
class ActorDelegateTest {

  private Actor delegator;
  private Engine engine;
  private Actor delegate;

  @Test
  void createDelayedDelegate() {
    final Actor actor = ActorDelegate.createDelayedDelegate(engine, delegator);
    assertNotNull(actor);
    assertThrows(
        UnsupportedOperationException.class,
        () -> {
          actor.getDelegate();
        });
  }

  @Test
  void createDelegate() {
    final Actor actor = ActorDelegate.createDelegate(engine, delegator);
    assertNotNull(actor);
    assertThrows(
        UnsupportedOperationException.class,
        () -> {
          actor.getDelegate();
        });
  }

  @Test
  void createPrioritizedDelegate() {
    final Actor actor = ActorDelegate.createPrioritizedDelegate(engine, delegator);
    assertNotNull(actor);
    assertThrows(
        UnsupportedOperationException.class,
        () -> {
          actor.getDelegate();
        });
  }

  @Test
  void postAddsEventToQueue() {
    final Event event = mock(Event.class);
    delegate.post(event);
    assertFalse(((ActorDelegate) delegate).getQueue().isEmpty());
  }

  @Test
  void postNullThrows() {
    assertThrows(NullPointerException.class, () -> delegate.post(null));
  }

  @Test
  void purgeEventsClearsQueue() {
    final Event event = mock(Event.class);
    delegate.post(event);
    assertFalse(((ActorDelegate) delegate).getQueue().isEmpty());
    delegate.purgeEvents();
    assertTrue(((ActorDelegate) delegate).getQueue().isEmpty());
  }

  @Test
  void getEngineReturnsEngine() {
    assertEquals(engine, delegate.getEngine());
  }

  @Test
  void getIdThrows() {
    assertThrows(UnsupportedOperationException.class, () -> delegate.getId());
  }

  @Test
  void processThrows() {
    assertThrows(UnsupportedOperationException.class, () -> delegate.process(mock(Event.class)));
  }

  @Test
  void equalsAndHashCode() {
    final Actor another = ActorDelegate.createDelegate(engine, delegator);
    assertEquals(delegate, delegate);
    assertEquals(delegate, another);
    assertEquals(delegate.hashCode(), another.hashCode());
    assertNotEquals(delegate, null);
    assertNotEquals(delegate, new Object());
    assertNotEquals(delegate, ActorDelegate.createDelegate(mock(Engine.class), delegator));
  }

  @Test
  void runProcessesStartEvent() {
    final Event startEvent = mock(Event.class);
    when(startEvent.getTopic()).thenReturn(Engine.START_EVENT);
    when(startEvent.getSource()).thenReturn(delegate);
    final Event stopEvent = mock(Event.class);
    when(stopEvent.getTopic()).thenReturn(Engine.STOP_EVENT);
    when(stopEvent.getSource()).thenReturn(delegate);
    delegate.post(startEvent);
    delegate.post(stopEvent);
    delegate.run();
    verify(delegator).afterStart();
    verify(delegator).beforeStop();
  }

  @Test
  void runSignalsStartedEventOnStart() {
    final Event startEvent = mock(Event.class);
    when(startEvent.getTopic()).thenReturn(Engine.START_EVENT);
    when(startEvent.getSource()).thenReturn(delegate);
    final Event stopEvent = mock(Event.class);
    when(stopEvent.getTopic()).thenReturn(Engine.STOP_EVENT);
    when(stopEvent.getSource()).thenReturn(delegate);
    delegate.post(startEvent);
    delegate.post(stopEvent);
    delegate.run();
    final ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
    verify(engine, org.mockito.Mockito.atLeastOnce()).signal(captor.capture());
    assertTrue(
        captor.getAllValues().stream()
            .anyMatch(
                e ->
                    Engine.STARTED_ACTOR_EVENT.equals(e.getTopic())
                        && delegator.equals(e.getSource())));
  }

  @Test
  void runProcessesStopEvent() {
    final Event stopEvent = mock(Event.class);
    when(stopEvent.getTopic()).thenReturn(Engine.STOP_EVENT);
    when(stopEvent.getSource()).thenReturn(delegate);
    delegate.post(stopEvent);
    delegate.run();
    verify(delegator).beforeStop();
  }

  @Test
  void runProcessesStopMeEvent() {
    final Event stopEvent = mock(Event.class);
    when(stopEvent.getTopic()).thenReturn(Engine.STOP_ME_EVENT);
    when(stopEvent.getSource()).thenReturn(delegator);
    delegate.post(stopEvent);
    delegate.run();
  }

  @Test
  void runIgnoresStopMeFromOtherSourceThenStart() {
    final Event stopMeOther = mock(Event.class);
    when(stopMeOther.getTopic()).thenReturn(Engine.STOP_ME_EVENT);
    when(stopMeOther.getSource()).thenReturn(mock(Actor.class));
    final Event startEvent = mock(Event.class);
    when(startEvent.getTopic()).thenReturn(Engine.START_EVENT);
    when(startEvent.getSource()).thenReturn(delegate);
    final Event stopEvent = mock(Event.class);
    when(stopEvent.getTopic()).thenReturn(Engine.STOP_EVENT);
    when(stopEvent.getSource()).thenReturn(delegate);
    delegate.post(stopMeOther);
    delegate.post(startEvent);
    delegate.post(stopEvent);
    delegate.run();
    verify(delegator).afterStart();
  }

  @Test
  void runProcessesGenericEventInBeforeStart() {
    final Event generic = mock(Event.class);
    when(generic.getTopic()).thenReturn("GENERIC");
    when(generic.getSource()).thenReturn(delegator);
    final Event stopEvent = mock(Event.class);
    when(stopEvent.getTopic()).thenReturn(Engine.STOP_EVENT);
    when(stopEvent.getSource()).thenReturn(delegate);
    delegate.post(generic);
    delegate.post(stopEvent);
    delegate.run();
    verify(delegator).process(generic);
  }

  @Test
  void runProcessesGenericEventInAfterStart() {
    final Event startEvent = mock(Event.class);
    when(startEvent.getTopic()).thenReturn(Engine.START_EVENT);
    when(startEvent.getSource()).thenReturn(delegate);
    final Event generic = mock(Event.class);
    when(generic.getTopic()).thenReturn("GENERIC");
    when(generic.getSource()).thenReturn(delegator);
    final Event stopEvent = mock(Event.class);
    when(stopEvent.getTopic()).thenReturn(Engine.STOP_EVENT);
    when(stopEvent.getSource()).thenReturn(delegate);
    delegate.post(startEvent);
    delegate.post(generic);
    delegate.post(stopEvent);
    delegate.run();
    verify(delegator).process(generic);
  }

  @Test
  void getQueueIsBlockingQueue() {
    final BlockingQueue<Event> queue = ((ActorDelegate) delegate).getQueue();
    assertNotNull(queue);
  }

  @Test
  void runForLoggerDelegatorSkipsBeforeStart() {
    final Logger logger = new Logger(engine);
    final Actor loggerDelegate = logger.getDelegate();
    final Event stopEvent = mock(Event.class);
    when(stopEvent.getTopic()).thenReturn(Engine.STOP_EVENT);
    when(stopEvent.getSource()).thenReturn(loggerDelegate);
    logger.post(stopEvent);
    logger.run();
    verify(engine, atLeastOnce()).signal(any(Event.class));
  }

  @Test
  void runHandlesStopMeFromSelfInAfterStart() {
    final Event start = mock(Event.class);
    when(start.getTopic()).thenReturn(Engine.START_EVENT);
    when(start.getSource()).thenReturn(delegate);
    final Event stopMe = mock(Event.class);
    when(stopMe.getTopic()).thenReturn(Engine.STOP_ME_EVENT);
    when(stopMe.getSource()).thenReturn(delegator);
    final Event stop = mock(Event.class);
    when(stop.getTopic()).thenReturn(Engine.STOP_EVENT);
    when(stop.getSource()).thenReturn(delegate);
    delegate.post(start);
    delegate.post(stopMe);
    delegate.post(stop);
    delegate.run();
    verify(delegator).afterStart();
  }

  @Test
  void runIgnoresStopMeFromOtherInAfterStart() {
    final Event start = mock(Event.class);
    when(start.getTopic()).thenReturn(Engine.START_EVENT);
    when(start.getSource()).thenReturn(delegate);
    final Event stopMeOther = mock(Event.class);
    when(stopMeOther.getTopic()).thenReturn(Engine.STOP_ME_EVENT);
    when(stopMeOther.getSource()).thenReturn(mock(Actor.class));
    final Event stop = mock(Event.class);
    when(stop.getTopic()).thenReturn(Engine.STOP_EVENT);
    when(stop.getSource()).thenReturn(delegate);
    delegate.post(start);
    delegate.post(stopMeOther);
    delegate.post(stop);
    delegate.run();
    verify(delegator).afterStart();
  }

  @Test
  void purgeEventsHandlesUnsupportedOperation() throws Exception {
    final BlockingQueue<Event> badQueue = mock(BlockingQueue.class);
    doThrow(new UnsupportedOperationException()).when(badQueue).clear();
    final Field field = ActorDelegate.class.getDeclaredField("events");
    field.setAccessible(true);
    field.set(delegate, badQueue);
    delegate.purgeEvents();
  }

  @Test
  void runHandlesDelegatorThrowable() {
    doThrow(new RuntimeException("boom")).when(delegator).subscribe(anyString());
    delegate.run();
    verify(engine, atLeastOnce()).signal(any(Event.class));
  }

  @Test
  void runHandlesProcessThrowableInAfterStart() {
    final Event start = mock(Event.class);
    when(start.getTopic()).thenReturn(Engine.START_EVENT);
    when(start.getSource()).thenReturn(delegate);
    final Event generic = mock(Event.class);
    when(generic.getTopic()).thenReturn("GENERIC");
    when(generic.getSource()).thenReturn(delegator);
    doThrow(new RuntimeException("process boom")).when(delegator).process(any());
    final Event stop = mock(Event.class);
    when(stop.getTopic()).thenReturn(Engine.STOP_EVENT);
    when(stop.getSource()).thenReturn(delegate);
    delegate.post(start);
    delegate.post(generic);
    delegate.post(stop);
    delegate.run();
    verify(delegator).afterStart();
    verify(engine, atLeastOnce()).signal(any(Event.class));
  }

  @Test
  void equalsFalseWhenDelegatorDiffersSameEngine() {
    final Actor otherDelegator = mock(Actor.class);
    when(otherDelegator.getName()).thenReturn("other:engine");
    when(otherDelegator.getSimpleName()).thenReturn("other");
    final Actor other = ActorDelegate.createDelegate(engine, otherDelegator);
    assertNotEquals(delegate, other);
  }

  @Test
  void postRetriesWhenQueueOfferFails() throws Exception {
    final BlockingQueue<Event> mockQueue = mock(BlockingQueue.class);
    when(mockQueue.offer(any())).thenReturn(false, true);
    setQueue(delegate, mockQueue);
    delegate.post(mock(Event.class));
    verify(mockQueue, org.mockito.Mockito.times(2)).offer(any());
  }

  @Test
  void runBeforeStartHandlesNullPollAndInterrupt() throws Exception {
    final Event start = mock(Event.class);
    when(start.getTopic()).thenReturn(Engine.START_EVENT);
    when(start.getSource()).thenReturn(delegate);
    final Event stop = mock(Event.class);
    when(stop.getTopic()).thenReturn(Engine.STOP_EVENT);
    when(stop.getSource()).thenReturn(delegate);
    final BlockingQueue<Event> mockQueue = mock(BlockingQueue.class);
    when(mockQueue.offer(any())).thenReturn(true);
    when(mockQueue.poll(anyLong(), any(TimeUnit.class)))
        .thenReturn(null)
        .thenThrow(new InterruptedException())
        .thenReturn(start)
        .thenReturn(stop);
    setQueue(delegate, mockQueue);
    delegate.run();
    verify(delegator).afterStart();
    Thread.interrupted();
  }

  @Test
  void runAfterStartHandlesNullPollAndInterrupt() throws Exception {
    final Event start = mock(Event.class);
    when(start.getTopic()).thenReturn(Engine.START_EVENT);
    when(start.getSource()).thenReturn(delegate);
    final Event stop = mock(Event.class);
    when(stop.getTopic()).thenReturn(Engine.STOP_EVENT);
    when(stop.getSource()).thenReturn(delegate);
    final BlockingQueue<Event> mockQueue = mock(BlockingQueue.class);
    when(mockQueue.offer(any())).thenReturn(true);
    when(mockQueue.poll(anyLong(), any(TimeUnit.class)))
        .thenReturn(start)
        .thenReturn(null)
        .thenThrow(new InterruptedException())
        .thenReturn(stop);
    setQueue(delegate, mockQueue);
    delegate.run();
    verify(delegator).afterStart();
    Thread.interrupted();
  }

  /**
   * Replaces the internal events queue of the given delegate.
   *
   * @param delegate the delegate to modify
   * @param queue the queue to install
   * @throws Exception if reflection fails
   */
  private void setQueue(final Actor delegate, final BlockingQueue<Event> queue) throws Exception {
    final Field field = ActorDelegate.class.getDeclaredField("events");
    field.setAccessible(true);
    field.set(delegate, queue);
  }

  @BeforeEach
  void setUp() {
    engine = mock(Engine.class);
    when(engine.getName()).thenReturn("engine");
    Logger.setActivated(engine, Level.TRACE, true);
    delegator = mock(Actor.class);
    when(delegator.getName()).thenReturn("delegator:engine");
    when(delegator.getSimpleName()).thenReturn("delegator");
    when(delegator.getEngine()).thenReturn(engine);
    delegate = ActorDelegate.createDelegate(engine, delegator);
    Logger.setActivated(delegator, Level.TRACE, true);
    Logger.setActivated(delegate, Level.TRACE, true);
  }

  @AfterEach
  void tearDown() {
    engine.stop();
  }
}
