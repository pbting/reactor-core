/*
 * Copyright (c) 2011-2018 Pivotal Software Inc, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package reactor.core.publisher;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.logging.Level;

import org.assertj.core.api.Assertions;
import org.junit.Assert;
import org.junit.Test;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.Fuseable;
import reactor.core.Scannable;
import reactor.core.publisher.FluxBufferPredicate.Mode;
import reactor.test.StepVerifier;
import reactor.test.StepVerifierOptions;
import reactor.test.publisher.FluxOperatorTest;
import reactor.test.publisher.TestPublisher;
import reactor.util.concurrent.Queues;

import static org.assertj.core.api.Assertions.assertThat;

public class FluxWindowPredicateTest extends
                                     FluxOperatorTest<String, Flux<String>> {

	@Test
	public void windowWhileNoEmptyWindows() {
		Flux.just("ALPHA", "#", "BETA", "#")
		    .windowWhile(s -> !"#".equals(s))
		    .flatMap(Flux::collectList)
		    .as(StepVerifier::create)
		    .assertNext(w -> assertThat(w).containsExactly("ALPHA"))
		    .assertNext(w -> assertThat(w).containsExactly("BETA"))
		    .verifyComplete();
	}

	@Test
	public void windowUntilNoEmptyWindows() {
		Flux.just("ALPHA", "#", "BETA", "#")
		    .windowUntil("#"::equals)
		    .flatMap(Flux::collectList)
		    .as(StepVerifier::create)
		    .assertNext(w -> assertThat(w).containsExactly("ALPHA", "#"))
		    .assertNext(w -> assertThat(w).containsExactly("BETA", "#"))
		    .verifyComplete();
	}

	@Test
	public void windowUntilCutBeforeNoEmptyWindows() {
		Flux.just("ALPHA", "#", "BETA", "#")
		    .windowUntil("#"::equals, true)
		    .flatMap(Flux::collectList)
		    .as(StepVerifier::create)
		    .assertNext(w -> assertThat(w).containsExactly("ALPHA"))
		    .assertNext(w -> assertThat(w).containsExactly("#", "BETA"))
		    .assertNext(w -> assertThat(w).containsExactly("#"))
		    .verifyComplete();
	}

	@Test
	public void windowWhileIntentionallyEmptyWindows() {
		Flux.just("ALPHA", "#", "BETA", "#", "#")
		    .windowWhile(s -> !"#".equals(s))
		    .flatMap(Flux::collectList)
		    .as(StepVerifier::create)
		    .assertNext(w -> assertThat(w).containsExactly("ALPHA"))
		    .assertNext(w -> assertThat(w).containsExactly("BETA"))
		    .assertNext(w -> assertThat(w).isEmpty())
		    .verifyComplete();
	}

	@Test
	public void windowUntilIntentionallyEmptyWindows() {
		Flux.just("ALPHA", "#", "BETA", "#", "#")
		    .windowUntil("#"::equals)
		    .flatMap(Flux::collectList)
		    .as(StepVerifier::create)
		    .assertNext(w -> assertThat(w).containsExactly("ALPHA", "#"))
		    .assertNext(w -> assertThat(w).containsExactly("BETA", "#"))
		    .assertNext(w -> assertThat(w).containsExactly("#"))
		    .verifyComplete();
	}

	@Test
	public void windowUntilCutBeforeIntentionallyEmptyWindows() {
		Flux.just("ALPHA", "#", "BETA", "#", "#")
		    .windowUntil("#"::equals, true)
		    .flatMap(Flux::collectList)
		    .as(StepVerifier::create)
		    .assertNext(w -> assertThat(w).containsExactly("ALPHA"))
		    .assertNext(w -> assertThat(w).containsExactly("#", "BETA"))
		    .assertNext(w -> assertThat(w).containsExactly("#"))
		    .assertNext(w -> assertThat(w).containsExactly("#"))
		    .verifyComplete();
	}

	@Override
	protected Scenario<String, Flux<String>> defaultScenarioOptions(Scenario<String, Flux<String>> defaultOptions) {
		return defaultOptions.shouldAssertPostTerminateState(false)
		                     .fusionMode(Fuseable.ASYNC)
		                     .fusionModeThreadBarrier(Fuseable.ANY)
		                     .prefetch(Queues.SMALL_BUFFER_SIZE);
	}

	@Override
	protected List<Scenario<String, Flux<String>>> scenarios_operatorSuccess() {
		return Arrays.asList(
				scenario(f -> f.windowUntil(t -> true, true, 1))
						.prefetch(1)
						.receive(s -> s.buffer().subscribe(b -> Assert.fail()),
								s -> s.buffer().subscribe(b -> assertThat(b).containsExactly(item(0))),
								s -> s.buffer().subscribe(b -> assertThat(b).containsExactly(item(1))),
								s -> s.buffer().subscribe(b -> assertThat(b).containsExactly(item(2)))),

				scenario(f -> f.windowWhile(t -> true))
						.receive(s -> s.buffer().subscribe(b -> assertThat(b).containsExactly(item(0), item(1), item(2)))),

				scenario(f -> f.windowUntil(t -> true))
						.receive(s -> s.buffer().subscribe(b -> assertThat(b).containsExactly(item(0))),
								s -> s.buffer().subscribe(b -> assertThat(b).containsExactly(item(1))),
								s -> s.buffer().subscribe(b -> assertThat(b).containsExactly(item(2)))),

				scenario(f -> f.windowUntil(t -> true, false, 1))
						.prefetch(1)
						.receive(s -> s.buffer().subscribe(b -> assertThat(b).containsExactly(item(0))),
								s -> s.buffer().subscribe(b -> assertThat(b).containsExactly(item(1))),
						        s -> s.buffer().subscribe(b -> assertThat(b).containsExactly(item(2)))),

				scenario(f -> f.windowUntil(t -> false))
						.receive(s -> s.buffer().subscribe(b -> assertThat(b).containsExactly(item(0), item(1), item(2))))
		);
	}

	@Override
	protected List<Scenario<String, Flux<String>>> scenarios_operatorError() {
		return Arrays.asList(
				scenario(f -> f.windowWhile(t -> {
					throw exception();
				})),

				scenario(f -> f.windowUntil(t -> {
					throw exception();
				})),

				scenario(f -> f.windowUntil(t -> {
					throw exception();
				}, true))
		);
	}

	@Override
	protected List<Scenario<String, Flux<String>>> scenarios_errorFromUpstreamFailure() {
		return Arrays.asList(
				scenario(f -> f.windowWhile(t -> true)),

				scenario(f -> f.windowUntil(t -> true)),

				scenario(f -> f.windowUntil(t -> true, true))
		);
	}

	@Test
	public void apiUntil() {
		StepVerifier.create(Flux.just("red", "green", "#", "orange", "blue", "#", "black", "white")
		                        .windowUntil(color -> color.equals("#"))
		                        .flatMap(Flux::materialize)
		                        .map(s -> s.isOnComplete() ? "WINDOW CLOSED" : s.get()))
		            .expectNext("red", "green", "#", "WINDOW CLOSED")
		            .expectNext("orange", "blue", "#", "WINDOW CLOSED")
		            .expectNext("black", "white", "WINDOW CLOSED")
	                .verifyComplete();
	}

	@Test
	public void apiUntilCutAfter() {
		StepVerifier.create(Flux.just("red", "green", "#", "orange", "blue", "#", "black", "white")
		                        .windowUntil(color -> color.equals("#"), false)
		                        .flatMap(Flux::materialize)
		                        .map(s -> s.isOnComplete() ? "WINDOW CLOSED" : s.get()))
		            .expectNext("red", "green", "#", "WINDOW CLOSED")
		            .expectNext("orange", "blue", "#", "WINDOW CLOSED")
		            .expectNext("black", "white", "WINDOW CLOSED")
	                .verifyComplete();
	}

	@Test
	public void apiUntilCutBefore() {
		StepVerifier.create(Flux.just("red", "green", "#", "orange", "blue", "#", "black", "white")
		                        .windowUntil(color -> color.equals("#"), true)
		                        .flatMap(Flux::materialize)
		                        .map(s -> s.isOnComplete() ? "WINDOW CLOSED" : s.get()))
		            .expectNext("red", "green", "WINDOW CLOSED", "#")
		            .expectNext("orange", "blue", "WINDOW CLOSED", "#")
		            .expectNext("black", "white", "WINDOW CLOSED")
	                .verifyComplete();
	}

	@Test
	public void apiWhile() {
		StepVerifier.create(Flux.just("red", "green", "#", "orange", "blue", "#", "black", "white")
		                        .windowWhile(color -> !color.equals("#"))
		                        .flatMap(Flux::materialize)
		                        .map(s -> s.isOnComplete() ? "WINDOW CLOSED" : s.get()))
		            .expectNext("red", "green", "WINDOW CLOSED")
		            .expectNext("orange", "blue", "WINDOW CLOSED")
		            .expectNext("black", "white", "WINDOW CLOSED")
	                .verifyComplete();
	}

	@Test
	public void normalUntil() {
		FluxProcessorSink<Integer> sp1 = Processors.direct();
		FluxWindowPredicate<Integer> windowUntil = new FluxWindowPredicate<>(sp1.asFlux(),
				Queues.small(),
				Queues.unbounded(),
				Queues.SMALL_BUFFER_SIZE,
				i -> i % 3 == 0,
				Mode.UNTIL);

		StepVerifier.create(windowUntil.flatMap(Flux::materialize))
		            .expectSubscription()
		            .then(() -> sp1.next(1))
		            .expectNext(Signal.next(1))
				    .then(() -> sp1.next(2))
		            .expectNext(Signal.next(2))
				    .then(() -> sp1.next(3))
		            .expectNext(Signal.next(3), Signal.complete())
				    .then(() -> sp1.next(4))
				    .expectNext(Signal.next(4))
				    .then(() -> sp1.next(5))
				    .expectNext(Signal.next(5))
				    .then(() -> sp1.next(6))
				    .expectNext(Signal.next(6), Signal.complete())
				    .then(() -> sp1.next(7))
				    .expectNext(Signal.next(7))
				    .then(() -> sp1.next(8))
				    .expectNext(Signal.next(8))
				    .then(sp1::complete)
		            .expectNext(Signal.complete())
				    .verifyComplete();

		assertThat(sp1.hasDownstreams()).isFalse();
	}

	@Test
	public void onCompletionBeforeLastBoundaryWindowEmitted() {
		Flux<Integer> source = Flux.just(1, 2);

		FluxWindowPredicate<Integer> windowUntil =
				new FluxWindowPredicate<>(source, Queues.small(), Queues.unbounded(), Queues.SMALL_BUFFER_SIZE,
						i -> i >= 3, Mode.UNTIL);

		FluxWindowPredicate<Integer> windowUntilCutBefore =
				new FluxWindowPredicate<>(source, Queues.small(), Queues.unbounded(), Queues.SMALL_BUFFER_SIZE,
						i -> i >= 3, Mode.UNTIL_CUT_BEFORE);

		FluxWindowPredicate<Integer> windowWhile =
				new FluxWindowPredicate<>(source, Queues.small(), Queues.unbounded(), Queues.SMALL_BUFFER_SIZE,
						i -> i < 3, Mode.WHILE);

		StepVerifier.create(windowUntil.flatMap(Flux::collectList))
				.expectNext(Arrays.asList(1, 2))
				.expectComplete()
				.verify();

		StepVerifier.create(windowUntilCutBefore.flatMap(Flux::collectList))
		            .expectNext(Arrays.asList(1, 2))
		            .expectComplete()
		            .verify();

		StepVerifier.create(windowWhile.flatMap(Flux::collectList))
		            .expectNext(Arrays.asList(1, 2))
		            .expectComplete()
		            .verify();
	}

	@Test
	public void mainErrorUntilIsPropagatedToBothWindowAndMain() {
		FluxProcessorSink<Integer> sp1 = Processors.direct();
		FluxWindowPredicate<Integer> windowUntil = new FluxWindowPredicate<>(
				sp1.asFlux(), Queues.small(), Queues.unbounded(), Queues.SMALL_BUFFER_SIZE,
				i -> i % 3 == 0, Mode.UNTIL);

		StepVerifier.create(windowUntil.flatMap(Flux::materialize))
		            .expectSubscription()
		            .then(() -> sp1.next(1))
		            .expectNext(Signal.next(1))
		            .then(() -> sp1.next(2))
		            .expectNext(Signal.next(2))
		            .then(() -> sp1.next(3))
		            .expectNext(Signal.next(3), Signal.complete())
		            .then(() -> sp1.next(4))
		            .expectNext(Signal.next(4))
		            .then(() -> sp1.error(new RuntimeException("forced failure")))
		            //this is the error in the window:
		            .expectNextMatches(signalErrorMessage("forced failure"))
		            //this is the error in the main:
		            .expectErrorMessage("forced failure")
		            .verify();
		assertThat(sp1.hasDownstreams()).isFalse();
	}

	@Test
	public void predicateErrorUntil() {
		FluxProcessorSink<Integer> sp1 = Processors.direct();
		FluxWindowPredicate<Integer> windowUntil = new FluxWindowPredicate<>(
				sp1.asFlux(), Queues.small(), Queues.unbounded(), Queues.SMALL_BUFFER_SIZE,
				i -> {
					if (i == 5) throw new IllegalStateException("predicate failure");
					return i % 3 == 0;
				}, Mode.UNTIL);

		StepVerifier.create(windowUntil.flatMap(Flux::materialize))
					.expectSubscription()
					.then(() -> sp1.next(1))
					.expectNext(Signal.next(1))
					.then(() -> sp1.next(2))
					.expectNext(Signal.next(2))
					.then(() -> sp1.next(3))
					.expectNext(Signal.next(3), Signal.complete())
					.then(() -> sp1.next(4))
					.expectNext(Signal.next(4))
					.then(() -> sp1.next(5))
					//error in the window:
					.expectNextMatches(signalErrorMessage("predicate failure"))
					.expectErrorMessage("predicate failure")
					.verify();
		assertThat(sp1.hasDownstreams()).isFalse();
	}

	@Test
	public void normalUntilCutBefore() {
		FluxProcessorSink<Integer> sp1 = Processors.direct();
		FluxWindowPredicate<Integer> windowUntilCutBefore = new FluxWindowPredicate<>(sp1.asFlux(),
				Queues.small(), Queues.unbounded(), Queues.SMALL_BUFFER_SIZE,
				i -> i % 3 == 0, Mode.UNTIL_CUT_BEFORE);

		StepVerifier.create(windowUntilCutBefore.flatMap(Flux::materialize))
				.expectSubscription()
				    .then(() -> sp1.next(1))
				    .expectNext(Signal.next(1))
				    .then(() -> sp1.next(2))
				    .expectNext(Signal.next(2))
				    .then(() -> sp1.next(3))
				    .expectNext(Signal.complete(), Signal.next(3))
				    .then(() -> sp1.next(4))
				    .expectNext(Signal.next(4))
				    .then(() -> sp1.next(5))
				    .expectNext(Signal.next(5))
				    .then(() -> sp1.next(6))
				    .expectNext(Signal.complete(), Signal.next(6))
				    .then(() -> sp1.next(7))
				    .expectNext(Signal.next(7))
				    .then(() -> sp1.next(8))
				    .expectNext(Signal.next(8))
				    .then(sp1::complete)
				    .expectNext(Signal.complete())
				    .verifyComplete();
		assertThat(sp1.hasDownstreams()).isFalse();
	}

	@Test
	public void mainErrorUntilCutBeforeIsPropagatedToBothWindowAndMain() {
		FluxProcessorSink<Integer> sp1 = Processors.direct();
		FluxWindowPredicate<Integer> windowUntilCutBefore =
				new FluxWindowPredicate<>(sp1.asFlux(), Queues.small(), Queues.unbounded(), Queues.SMALL_BUFFER_SIZE,
						i -> i % 3 == 0, Mode.UNTIL_CUT_BEFORE);

		StepVerifier.create(windowUntilCutBefore.flatMap(Flux::materialize))
		            .expectSubscription()
		            .then(() -> sp1.next(1))
		            .expectNext(Signal.next(1))
		            .then(() -> sp1.next(2))
		            .expectNext(Signal.next(2))
		            .then(() -> sp1.next(3))
		            .expectNext(Signal.complete())
		            .expectNext(Signal.next(3))
		            .then(() -> sp1.next(4))
		            .expectNext(Signal.next(4))
		            .then(() -> sp1.error(new RuntimeException("forced failure")))
		            //this is the error in the window:
		            .expectNextMatches(signalErrorMessage("forced failure"))
		            //this is the error in the main:
		            .expectErrorMessage("forced failure")
		            .verify();
		assertThat(sp1.hasDownstreams()).isFalse();
	}

	@Test
	public void predicateErrorUntilCutBefore() {
		FluxProcessorSink<Integer> sp1 = Processors.direct();
		FluxWindowPredicate<Integer> windowUntilCutBefore =
				new FluxWindowPredicate<>(sp1.asFlux(), Queues.small(), Queues.unbounded(), Queues.SMALL_BUFFER_SIZE,
				i -> {
					if (i == 5) throw new IllegalStateException("predicate failure");
					return i % 3 == 0;
				}, Mode.UNTIL_CUT_BEFORE);

		StepVerifier.create(windowUntilCutBefore.flatMap(Flux::materialize))
					.expectSubscription()
					.then(() -> sp1.next(1))
					.expectNext(Signal.next(1))
					.then(() -> sp1.next(2))
					.expectNext(Signal.next(2))
					.then(() -> sp1.next(3))
					.expectNext(Signal.complete(), Signal.next(3))
					.then(() -> sp1.next(4))
					.expectNext(Signal.next(4))
					.then(() -> sp1.next(5))
					//error in the window:
					.expectNextMatches(signalErrorMessage("predicate failure"))
					.expectErrorMessage("predicate failure")
					.verify();
		assertThat(sp1.hasDownstreams()).isFalse();
	}

	private <T> Predicate<? super Signal<T>> signalErrorMessage(String expectedMessage) {
		return signal -> signal.isOnError()
				&& signal.getThrowable() != null
				&& expectedMessage.equals(signal.getThrowable().getMessage());
	}

	@Test
	public void normalWhile() {
		FluxProcessorSink<Integer> sp1 = Processors.direct();
		FluxWindowPredicate<Integer> windowWhile = new FluxWindowPredicate<>(
				sp1.asFlux(), Queues.small(), Queues.unbounded(), Queues.SMALL_BUFFER_SIZE,
				i -> i % 3 != 0, Mode.WHILE);

		StepVerifier.create(windowWhile.flatMap(Flux::materialize))
		            .expectSubscription()
		            .then(() -> sp1.next(1))
		            .expectNext(Signal.next(1))
		            .then(() -> sp1.next(2))
		            .expectNext(Signal.next(2))
		            .then(() -> sp1.next(3))
		            .expectNext(Signal.complete())
		            .then(() -> sp1.next(4))
		            .expectNext(Signal.next(4))
		            .then(() -> sp1.next(5))
		            .expectNext(Signal.next(5))
		            .then(() -> sp1.next(6))
		            .expectNext(Signal.complete())
		            .then(() -> sp1.next(7))
		            .expectNext(Signal.next(7))
		            .then(() -> sp1.next(8))
		            .expectNext(Signal.next(8))
		            .then(sp1::complete)
		            .expectNext(Signal.complete())
		            .verifyComplete();
		assertThat(sp1.hasDownstreams()).isFalse();
	}

	@Test
	public void normalWhileDoesntInitiallyMatch() {
		FluxProcessorSink<Integer> sp1 = Processors.direct();
		FluxWindowPredicate<Integer> windowWhile = new FluxWindowPredicate<>(
				sp1.asFlux(), Queues.small(), Queues.unbounded(), Queues.SMALL_BUFFER_SIZE,
				i -> i % 3 == 0, Mode.WHILE);

		StepVerifier.create(windowWhile.flatMap(Flux::materialize))
		            .expectSubscription()
		            .expectNoEvent(Duration.ofMillis(10))
		            .then(() -> sp1.next(1)) //closes initial, open 2nd
		            .expectNext(Signal.complete())
		            .then(() -> sp1.next(2)) //closes second, open 3rd
		            .expectNext(Signal.complete())
		            .then(() -> sp1.next(3)) //emits 3
		            .expectNext(Signal.next(3))
		            .expectNoEvent(Duration.ofMillis(10))
		            .then(() -> sp1.next(4)) //closes 3rd, open 4th
		            .expectNext(Signal.complete())
		            .then(() -> sp1.next(5)) //closes 4th, open 5th
		            .expectNext(Signal.complete())
		            .then(() -> sp1.next(6)) //emits 6
		            .expectNext(Signal.next(6))
		            .expectNoEvent(Duration.ofMillis(10))
		            .then(() -> sp1.next(7)) //closes 5th, open 6th
		            .expectNext(Signal.complete())
		            .then(() -> sp1.next(8)) //closes 6th, open 7th
		            .expectNext(Signal.complete())
		            .then(() -> sp1.next(9)) //emits 9
		            .expectNext(Signal.next(9))
		            .expectNoEvent(Duration.ofMillis(10))
		            .then(sp1::complete) // completion triggers completion of the last window (7th)
		            .expectNext(Signal.complete())
		            .expectComplete()
		            .verify(Duration.ofSeconds(1));
		assertThat(sp1.hasDownstreams()).isFalse();
	}

	@Test
	public void normalWhileDoesntMatch() {
		FluxProcessorSink<Integer> sp1 = Processors.direct();
		FluxWindowPredicate<Integer> windowWhile = new FluxWindowPredicate<>(
				sp1.asFlux(), Queues.small(), Queues.unbounded(), Queues.SMALL_BUFFER_SIZE,
				i -> i > 4, Mode.WHILE);

		StepVerifier.create(windowWhile.flatMap(Flux::materialize))
		            .expectSubscription()
		            .expectNoEvent(Duration.ofMillis(10))
		            .then(() -> sp1.next(1))
		            .expectNext(Signal.complete())
		            .then(() -> sp1.next(2))
		            .expectNext(Signal.complete())
		            .then(() -> sp1.next(3))
		            .expectNext(Signal.complete())
		            .then(() -> sp1.next(4))
		            .expectNext(Signal.complete())
		            .expectNoEvent(Duration.ofMillis(10))
		            .then(() -> sp1.next(1))
		            .expectNext(Signal.complete())
		            .then(() -> sp1.next(2))
		            .expectNext(Signal.complete())
		            .then(() -> sp1.next(3))
		            .expectNext(Signal.complete())
		            .then(() -> sp1.next(4))
		            .expectNext(Signal.complete()) //closing window opened by 3
		            .expectNoEvent(Duration.ofMillis(10))
		            .then(sp1::complete)
		            //remainder window, not emitted
		            .expectComplete()
		            .verify(Duration.ofSeconds(1));
		assertThat(sp1.hasDownstreams()).isFalse();
	}

	@Test
	public void mainErrorWhileIsPropagatedToBothWindowAndMain() {
		FluxProcessorSink<Integer> sp1 = Processors.direct();
		FluxWindowPredicate<Integer> windowWhile = new FluxWindowPredicate<>(
				sp1.asFlux(), Queues.small(), Queues.unbounded(), Queues.SMALL_BUFFER_SIZE,
				i -> i % 3 == 0, Mode.WHILE);

		StepVerifier.create(windowWhile.flatMap(Flux::materialize))
		            .expectSubscription()
		            .then(() -> sp1.next(1))
		            .expectNext(Signal.complete())
		            .then(() -> sp1.next(2))
		            .expectNext(Signal.complete())
		            .then(() -> sp1.next(3)) //at this point, new window, need another data to close it
		            .then(() -> sp1.next(4))
		            .expectNext(Signal.next(3), Signal.complete())
		            .then(() -> sp1.error(new RuntimeException("forced failure")))
		            //this is the error in the main:
		            .expectErrorMessage("forced failure")
		            .verify(Duration.ofMillis(100));
		assertThat(sp1.hasDownstreams()).isFalse();
	}

	@Test
	public void whileStartingSeveralSeparatorsEachCreateEmptyWindow() {
		StepVerifier.create(Flux.just("#")
		                        .repeat(10)
		                        .concatWith(Flux.just("other", "value"))
		                        .windowWhile(s -> !s.equals("#"))
		                        .flatMap(Flux::count)
		)
		            .expectNext(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L)
		            .expectNext(2L)
		            .verifyComplete();
	}

	@Test
	public void whileOnlySeparatorsGivesSequenceOfWindows() {
		StepVerifier.create(Flux.just("#")
		                        .repeat(10)
		                        .windowWhile(s -> !s.equals("#"))
		                        .flatMap(w -> w.count()))
		            .expectNext(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L)
	                //no "remainder" window
		            .verifyComplete();
	}

	@Test
	public void predicateErrorWhile() {
		FluxProcessorSink<Integer> sp1 = Processors.direct();
		FluxWindowPredicate<Integer> windowWhile = new FluxWindowPredicate<>(
				sp1.asFlux(), Queues.small(), Queues.unbounded(), Queues.SMALL_BUFFER_SIZE,
				i -> {
					if (i == 3) return true;
					if (i == 5) throw new IllegalStateException("predicate failure");
					return false;
				}, Mode.WHILE);

		StepVerifier.create(windowWhile.flatMap(Flux::materialize))
					.expectSubscription()
					.then(() -> sp1.next(1)) //empty window
					.expectNext(Signal.complete())
					.then(() -> sp1.next(2)) //empty window
					.expectNext(Signal.complete())
					.then(() -> sp1.next(3)) //window opens
					.expectNext(Signal.next(3))
					.then(() -> sp1.next(4)) //previous window closes, new (empty) window
					.expectNext(Signal.complete())
					.then(() -> sp1.next(5)) //fails, the empty window receives onError
					//error in the window:
					.expectErrorMessage("predicate failure")
					.verify(Duration.ofMillis(100));
		assertThat(sp1.hasDownstreams()).isFalse();
	}


	@Test
	public void whileRequestOneByOne() {
		StepVerifier.create(Flux.just("red", "green", "#", "orange", "blue", "#", "black", "white")
		                        .hide()
		                        .windowWhile(color -> !color.equals("#"))
		                        .flatMap(w -> w, 1), 1)
	                .expectNext("red")
	                .thenRequest(1)
	                .expectNext("green")
	                .thenRequest(1)
	                .expectNext("orange")
	                .thenRequest(1)
	                .expectNext("blue")
	                .thenRequest(1)
	                .expectNext("black")
	                .thenRequest(1)
	                .expectNext("white")
	                .thenRequest(1)
	                .verifyComplete();
	}

	@Test
	public void mismatchAtBeginningUntil() {
		StepVerifier.create(Flux.just("#", "red", "green")
		                        .windowUntil(s -> s.equals("#"))
		                        .flatMap(Flux::materialize)
		                        .map(sig -> sig.isOnComplete() ? "END" : sig.get()))
	                .expectNext("#", "END")
	                .expectNext("red", "green", "END")
	                .verifyComplete();
	}

	@Test
	public void mismatchAtBeginningUntilCutBefore() {
		StepVerifier.create(Flux.just("#", "red", "green")
		                        .windowUntil(s -> s.equals("#"), true)
		                        .flatMap(Flux::materialize)
		                        .map(sig -> sig.isOnComplete() ? "END" : sig.get()))
		            .expectNext("END")
	                .expectNext("#", "red", "green", "END")
	                .verifyComplete();
	}

	@Test
	public void mismatchAtBeginningWhile() {
		StepVerifier.create(Flux.just("#", "red", "green")
		                        .windowWhile(s -> !s.equals("#"))
		                        .flatMap(Flux::materialize)
		                        .map(sig -> sig.isOnComplete() ? "END" : sig.get()))
	                .expectNext("END")
	                .expectNext("red", "green", "END")
	                .verifyComplete();
	}

	@Test
	public void innerCancellationCancelsMainSequence() {
		StepVerifier.create(Flux.just("red", "green", "#", "black", "white")
		                        .log()
		                        .windowWhile(s -> !s.equals("#"))
		                        .flatMap(w -> w.take(1)))
		            .expectNext("red")
		            .thenCancel()
		            .verify();
	}

	@Test
	public void prefetchIntegerMaxIsRequestUnboundedUntil() {
		TestPublisher<?> tp = TestPublisher.create();
		tp.flux().windowUntil(s -> true, true, Integer.MAX_VALUE).subscribe();
		tp.assertMinRequested(Long.MAX_VALUE);
	}

	@Test
	public void prefetchIntegerMaxIsRequestUnboundedWhile() {
		TestPublisher<?> tp = TestPublisher.create();
		tp.flux().windowWhile(s -> true, Integer.MAX_VALUE).subscribe();
		tp.assertMinRequested(Long.MAX_VALUE);
	}

	@Test
	public void manualRequestWindowUntilOverRequestingSourceByPrefetch() {
		AtomicLong req = new AtomicLong();
		int prefetch = 4;

		Flux<Integer> source = Flux.range(1, 20)
		                           .doOnRequest(req::addAndGet)
		                           .log("source", Level.FINE)
		                           .hide();

		StepVerifier.create(source.windowUntil(i -> i % 5 == 0, false, prefetch)
		                          .concatMap(w -> w, 1)
				.log("downstream", Level.FINE),  0)
		            .thenRequest(2)
		            .expectNext(1, 2)
		            .thenRequest(6)
		            .expectNext(3, 4, 5, 6, 7, 8)
		            .expectNoEvent(Duration.ofMillis(100))
		            .thenCancel()
		            .verify();

		assertThat(req.get()).isEqualTo(8 + prefetch);
	}

	@Test
	public void manualRequestWindowWhileOverRequestingSourceByPrefetch() {
		AtomicLong req = new AtomicLong();
		int prefetch = 4;

		Flux<Integer> source = Flux.range(1, 20)
		                           .doOnRequest(req::addAndGet)
		                           .log("source", Level.FINE)
		                           .hide();

		StepVerifier.create(source.windowWhile(i -> i % 5 != 0, prefetch)
		                          .concatMap(w -> w.log("window", Level.FINE), 1)
		                          .log("downstream", Level.FINE),  0)
		            .thenRequest(2)
		            .expectNext(1, 2)
		            .thenRequest(6)
		            .expectNext(3, 4, 6, 7, 8, 9)
		            .expectNoEvent(Duration.ofMillis(100))
		            .thenCancel()
		            .verify();

		assertThat(req.get()).isEqualTo(12 + prefetch); //9 forwarded elements, 2
		// delimiters, 1 cancel and prefetch
	}

	// see https://github.com/reactor/reactor-core/issues/477
	@Test
	public void windowWhileOneByOneStartingDelimiterReplenishes() {
		AtomicLong req = new AtomicLong();
		Flux<String> source = Flux.just("#", "1A", "1B", "1C", "#", "2A", "2B", "2C", "2D", "#", "3A").hide();

		StepVerifier.create(
				source
				.doOnRequest(req::addAndGet)
				.log("source", Level.FINE)
				.windowWhile(s -> !"#".equals(s), 2)
				.log("windowWhile", Level.FINE)
				.concatMap(w -> w.collectList()
				                 .log("window", Level.FINE)
						, 1)
				.log("downstream", Level.FINE)
			, StepVerifierOptions.create().checkUnderRequesting(false).initialRequest(1))
		            .expectNextMatches(List::isEmpty)
		            .thenRequest(1)
		            .assertNext(l -> assertThat(l).containsExactly("1A", "1B", "1C"))
		            .thenRequest(1)
		            .assertNext(l -> assertThat(l).containsExactly("2A", "2B", "2C", "2D"))
		            .thenRequest(1)
		            .assertNext(l -> assertThat(l).containsExactly("3A"))
                    .expectComplete()
		            .verify(Duration.ofSeconds(1));

		assertThat(req.get()).isEqualTo(13); //11 elements + the prefetch
	}

	// see https://github.com/reactor/reactor-core/issues/477
	@Test
	public void windowWhileUnboundedStartingDelimiterReplenishes() {
		AtomicLong req = new AtomicLong();
		Flux<String> source = Flux.just("#", "1A", "1B", "1C", "#", "2A", "2B", "2C", "2D", "#", "3A").hide();

		StepVerifier.create(
		source
				.doOnRequest(req::addAndGet)
				.log("source", Level.FINE)
				.windowWhile(s -> !"#".equals(s), 2)
				.log("windowWhile", Level.FINE)
				.concatMap(w -> w.collectList()
				                 .log("window", Level.FINE)
						, 1)
				.log("downstream", Level.FINE)
		)
		            .expectNextMatches(List::isEmpty)
		            .assertNext(l -> assertThat(l).containsExactly("1A", "1B", "1C"))
		            .assertNext(l -> assertThat(l).containsExactly("2A", "2B", "2C", "2D"))
		            .assertNext(l -> assertThat(l).containsExactly("3A"))
		            .expectComplete()
		            .verify(Duration.ofSeconds(1));

		assertThat(req.get()).isEqualTo(13); //11 elements + the prefetch
	}

	@Test
	public void windowUntilUnboundedStartingDelimiterReplenishes() {
		AtomicLong req = new AtomicLong();
		Flux<String> source = Flux.just("#", "1A", "1B", "1C", "#", "2A", "2B", "2C", "2D", "#", "3A").hide();

		StepVerifier.create(
		source
				.doOnRequest(req::addAndGet)
				.log("source", Level.FINE)
				.windowUntil(s -> "#".equals(s), false, 2)
				.log("windowUntil", Level.FINE)
				.concatMap(w -> w.collectList()
								 .log("window", Level.FINE)
						, 1)
				.log("downstream", Level.FINE)
		)
		            .assertNext(l -> assertThat(l).containsExactly("#"))
		            .assertNext(l -> assertThat(l).containsExactly("1A", "1B", "1C", "#"))
		            .assertNext(l -> assertThat(l).containsExactly("2A", "2B", "2C", "2D", "#"))
		            .assertNext(l -> assertThat(l).containsExactly("3A"))
		            .expectComplete()
		            .verify(Duration.ofSeconds(1));

		assertThat(req.get()).isEqualTo(13); //11 elements + the prefetch
	}

	@Test
    public void scanMainSubscriber() {
        CoreSubscriber<Flux<Integer>> actual = new LambdaSubscriber<>(null, e -> {}, null, null);
        FluxWindowPredicate.WindowPredicateMain<Integer> test = new FluxWindowPredicate.WindowPredicateMain<>(actual,
        		Queues.<Flux<Integer>>unbounded().get(), Queues.unbounded(), 123, i -> true, Mode.WHILE);

        Subscription parent = Operators.emptySubscription();
        test.onSubscribe(parent);

		Assertions.assertThat(test.scan(Scannable.Attr.PARENT)).isSameAs(parent);
		Assertions.assertThat(test.scan(Scannable.Attr.ACTUAL)).isSameAs(actual);
		Assertions.assertThat(test.scan(Scannable.Attr.PREFETCH)).isEqualTo(123);
		test.requested = 35;
		Assertions.assertThat(test.scan(Scannable.Attr.REQUESTED_FROM_DOWNSTREAM)).isEqualTo(35);
		test.queue.offer(Flux.just(1).groupBy(i -> i).blockFirst());
		Assertions.assertThat(test.scan(Scannable.Attr.BUFFERED)).isEqualTo(1);

		Assertions.assertThat(test.scan(Scannable.Attr.ERROR)).isNull();
		test.error = new IllegalStateException("boom");
		Assertions.assertThat(test.scan(Scannable.Attr.ERROR)).hasMessage("boom");

		Assertions.assertThat(test.scan(Scannable.Attr.TERMINATED)).isFalse();
		test.onComplete();
		Assertions.assertThat(test.scan(Scannable.Attr.TERMINATED)).isTrue();

		Assertions.assertThat(test.scan(Scannable.Attr.CANCELLED)).isFalse();
		test.cancel();
		Assertions.assertThat(test.scan(Scannable.Attr.CANCELLED)).isTrue();
    }



	@Test
    public void scanOtherSubscriber() {
        CoreSubscriber<Flux<Integer>> actual = new LambdaSubscriber<>(null, e -> {}, null, null);
        FluxWindowPredicate.WindowPredicateMain<Integer> main = new FluxWindowPredicate.WindowPredicateMain<>(actual,
        		Queues.<Flux<Integer>>unbounded().get(), Queues.unbounded(), 123, i -> true, Mode.WHILE);
        FluxWindowPredicate.WindowFlux<Integer> test = new FluxWindowPredicate.WindowFlux<>(
        		Queues.<Integer>unbounded().get(), main);

        Subscription parent = Operators.emptySubscription();
        test.onSubscribe(parent);

		Assertions.assertThat(test.scan(Scannable.Attr.PARENT)).isSameAs(main);
		Assertions.assertThat(test.scan(Scannable.Attr.ACTUAL)).isNull(); // RS: TODO Need to make actual non-null
		test.requested = 35;
		Assertions.assertThat(test.scan(Scannable.Attr.REQUESTED_FROM_DOWNSTREAM)).isEqualTo(35);
		test.queue.offer(27);
		Assertions.assertThat(test.scan(Scannable.Attr.BUFFERED)).isEqualTo(1);

		Assertions.assertThat(test.scan(Scannable.Attr.ERROR)).isNull();
		test.error = new IllegalStateException("boom");
		Assertions.assertThat(test.scan(Scannable.Attr.ERROR)).hasMessage("boom");

		Assertions.assertThat(test.scan(Scannable.Attr.TERMINATED)).isFalse();
		test.onComplete();
		Assertions.assertThat(test.scan(Scannable.Attr.TERMINATED)).isTrue();

		Assertions.assertThat(test.scan(Scannable.Attr.CANCELLED)).isFalse();
		test.cancel();
		Assertions.assertThat(test.scan(Scannable.Attr.CANCELLED)).isTrue();
    }
}