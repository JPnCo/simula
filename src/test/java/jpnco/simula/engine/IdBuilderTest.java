package jpnco.simula.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link IdBuilder}, covering sequential increment and concurrent
 * uniqueness of the generated identifiers.
 */
class IdBuilderTest {

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
	void testNextId() {
		int id = IdBuilder.nextId();
		assertEquals(++id, IdBuilder.nextId());
		assertEquals(++id, IdBuilder.nextId());
		assertEquals(++id, IdBuilder.nextId());
	}

	@Test
	void testSequentialIncrement() {
		final int first = IdBuilder.nextId();
		final int second = IdBuilder.nextId();
		final int third = IdBuilder.nextId();
		assertEquals(first + 1, second);
		assertEquals(second + 1, third);
	}

	@Test
	void testInstantiationIsAllowed() {
		final IdBuilder builder = new IdBuilder();
		assertEquals(IdBuilder.class, builder.getClass());
	}

	@Test
	void testConcurrentUniqueness() throws Exception {
		final int threads = 8;
		final int idsPerThread = 500;
		final ExecutorService pool = Executors.newFixedThreadPool(threads);
		try {
			final List<Callable<List<Integer>>> tasks = new CopyOnWriteArrayList<>();
			for (int i = 0; i < threads; i++) {
				tasks.add(() -> IntStream.range(0, idsPerThread).mapToObj(j -> IdBuilder.nextId())
						.collect(Collectors.toList()));
			}
			final List<Future<List<Integer>>> futures = pool.invokeAll(tasks);
			final List<Integer> allIds = new CopyOnWriteArrayList<>();
			for (final Future<List<Integer>> future : futures) {
				allIds.addAll(future.get());
			}
			final Set<Integer> unique = allIds.stream().collect(Collectors.toSet());
			assertEquals(allIds.size(), unique.size(), "all generated ids must be unique under concurrency");
		} finally {
			pool.shutdownNow();
		}
	}

}
