/*
 * Copyright (c) 2011-2016 Pivotal Software Inc, All Rights Reserved.
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

import org.junit.Test;
import reactor.test.TestSubscriber;

public class MonoDematerializerTest {

	Signal<Integer> error = Signal.error(new RuntimeException("Forced failure"));

	@Test
	public void singleCompletion() {
		TestSubscriber<Integer> ts = TestSubscriber.create();

		Mono<Integer> dematerialize = Mono.just(Signal.<Integer>complete())
		                                  .dematerialize();

		dematerialize.subscribe(ts);

		ts.assertNoValues()
		  .assertNoError()
		  .assertComplete();
	}

	@Test
	public void singleError() {
		TestSubscriber<Integer> ts = TestSubscriber.create();

		Mono<Integer> dematerialize = Mono.just(error)
		                                  .dematerialize();

		dematerialize.subscribe(ts);

		ts.assertNoValues()
		  .assertError(RuntimeException.class)
		  .assertNotComplete();
	}

	@Test
	public void immediateCompletion() {
		TestSubscriber<Integer> ts = TestSubscriber.create(0);

		Mono<Integer> dematerialize = Mono.just(Signal.<Integer>complete())
		                                  .dematerialize();

		dematerialize.subscribe(ts);

		ts.assertNoValues()
		  .assertNoError()
		  .assertComplete();
	}

	@Test
	public void immediateError() {
		TestSubscriber<Integer> ts = TestSubscriber.create(0);

		Mono<Integer> dematerialize = Mono.just(error)
		                                  .dematerialize();

		dematerialize.subscribe(ts);

		ts.assertNoValues()
		  .assertError(RuntimeException.class)
		  .assertNotComplete();
	}

	@Test
	public void completeAfterSingleSignal() {
		TestSubscriber<Integer> ts = TestSubscriber.create(0);

		Mono<Integer> dematerialize = Mono.just(Signal.next(1))
		                                  .dematerialize();

		dematerialize.subscribe(ts);

		ts.assertNoValues()
		  .assertNoError()
		  .assertNotComplete();

		ts.request(1);

		ts.assertValues(1)
		  .assertNoError()
		  .assertComplete();
	}

	@Test
	public void errorAfterSingleSignal() {
		TestSubscriber<Integer> ts = TestSubscriber.create(0);

		Mono<Integer> dematerialize = Mono.just(error)
		                                  .dematerialize();

		dematerialize.subscribe(ts);

		ts.assertNoValues()
		  .assertError(RuntimeException.class)
		  .assertNotComplete();
	}

	//@Test
	//TODO
	public void neverEnding() {
		TestSubscriber<Integer> ts = TestSubscriber.create();

		Flux<Integer> dematerialize = Mono.just(Signal.next(1))
		                                  .concatWith(Mono.never())
		                                  .dematerialize();

		dematerialize.subscribe(ts);

		ts.assertValues(1)
		  .assertNoError()
		  .assertComplete();
	}

}
