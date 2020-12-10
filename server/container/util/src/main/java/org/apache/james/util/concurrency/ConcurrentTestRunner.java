/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.util.concurrency;

import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.apache.james.util.concurrent.NamedThreadFactory;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import reactor.core.publisher.Mono;

public class ConcurrentTestRunner implements Closeable {

    public static final int DEFAULT_OPERATION_COUNT = 1;

    @FunctionalInterface
    public interface RequireOperation {
        RequireThreadCount operation(ConcurrentOperation operation);

        default RequireThreadCount reactorOperation(ReactorOperation reactorOperation) {
            return operation(reactorOperation.blocking());
        }

        default RequireThreadCount randomlyDistributedOperations(ConcurrentOperation firstOperation, ConcurrentOperation... operations) {
            Random random = createReproductibleRandom();
            ConcurrentOperation aggregateOperation = (threadNumber, step) -> selectRandomOperation(random, firstOperation, operations).execute(threadNumber, step);
            return operation(aggregateOperation);
        }

        default RequireThreadCount randomlyDistributedReactorOperations(ReactorOperation firstReactorOperation, ReactorOperation... reactorOperations) {
            Random random = createReproductibleRandom();
            ReactorOperation aggregateOperation = (threadNumber, step) -> selectRandomOperation(random, firstReactorOperation, reactorOperations).execute(threadNumber, step);
            return reactorOperation(aggregateOperation);
        }

        default Random createReproductibleRandom() {
            return new Random(2134);
        }

        default <OperationT> OperationT selectRandomOperation(Random random, OperationT firstReactorOperation, OperationT... reactorOperations) {
            int whichAction = random.nextInt(reactorOperations.length + 1);
            if (whichAction == 0) {
                return firstReactorOperation;
            } else {
                return reactorOperations[whichAction - 1];
            }
        }
    }

    @FunctionalInterface
    public interface RequireThreadCount {
        Builder threadCount(int threadCount);
    }

    public static class Builder {
        private final int threadCount;
        private final ConcurrentOperation operation;
        private Optional<Integer> operationCount;
        private Optional<Boolean> noErrorLogs;

        private Builder(int threadCount, ConcurrentOperation operation) {
            Preconditions.checkArgument(threadCount > 0, "Thread count should be strictly positive");
            Preconditions.checkNotNull(operation);

            this.threadCount = threadCount;
            this.operation = operation;
            this.operationCount = Optional.empty();
            this.noErrorLogs = Optional.empty();
        }

        public Builder operationCount(int operationCount) {
            Preconditions.checkArgument(operationCount > 0, "Operation count should be strictly positive");
            this.operationCount = Optional.of(operationCount);
            return this;
        }

        public Builder noErrorLogs() {
            this.noErrorLogs = Optional.of(true);
            return this;
        }

        private ConcurrentTestRunner build() {
            return new ConcurrentTestRunner(
                threadCount,
                operationCount.orElse(DEFAULT_OPERATION_COUNT),
                noErrorLogs.orElse(false),
                operation);
        }

        public ConcurrentTestRunner run() {
            ConcurrentTestRunner testRunner = build();
            testRunner.run();
            return testRunner;
        }

        public ConcurrentTestRunner runSuccessfullyWithin(Duration duration) throws InterruptedException, ExecutionException {
            return build()
                .runSuccessfullyWithin(duration);
        }

        public ConcurrentTestRunner runAcceptingErrorsWithin(Duration duration) throws InterruptedException, ExecutionException {
            return build()
                .runAcceptingErrorsWithin(duration);
        }
    }

    @FunctionalInterface
    public interface ConcurrentOperation {
        void execute(int threadNumber, int step) throws Exception;
    }

    @FunctionalInterface
    public interface ReactorOperation {
        Publisher<Void> execute(int threadNumber, int step) throws Exception;

        default ConcurrentOperation blocking() {
            return (threadNumber, step) -> Mono.from(execute(threadNumber, step))
                .then()
                .block();
        }
    }

    private class ConcurrentRunnableTask implements Runnable {
        private final int threadNumber;
        private final ConcurrentOperation concurrentOperation;
        private final boolean noErrorLogs;
        private Exception exception;

        public ConcurrentRunnableTask(int threadNumber, ConcurrentOperation concurrentOperation, boolean noErrorLogs) {
            this.threadNumber = threadNumber;
            this.concurrentOperation = concurrentOperation;
            this.noErrorLogs = noErrorLogs;
        }

        @Override
        public void run() {
            exception = null;
            countDownLatch.countDown();
            for (int i = 0; i < operationCount; i++) {
                try {
                    concurrentOperation.execute(threadNumber, i);
                } catch (Exception e) {
                    if (!noErrorLogs) {
                        LOGGER.error("Error caught during concurrent testing (iteration {}, threadNumber {})", i, threadNumber, e);
                    }
                    exception = e;
                }
            }
            if (exception != null) {
                throw new RuntimeException(exception);
            }
        }
    }

    public static class NotTerminatedException extends RuntimeException {

    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ConcurrentTestRunner.class);

    public static RequireOperation builder() {
        return operation -> threadCount -> new Builder(threadCount, operation);
    }

    private final int threadCount;
    private final int operationCount;
    private final boolean suppressLogger;
    private final CountDownLatch countDownLatch;
    private final ConcurrentOperation biConsumer;
    private final ExecutorService executorService;
    private final List<Future<?>> futures;

    private ConcurrentTestRunner(int threadCount, int operationCount, boolean suppressLogger, ConcurrentOperation biConsumer) {
        this.threadCount = threadCount;
        this.operationCount = operationCount;
        this.countDownLatch = new CountDownLatch(threadCount);
        this.suppressLogger = suppressLogger;
        this.biConsumer = biConsumer;
        ThreadFactory threadFactory = NamedThreadFactory.withClassName(getClass());
        this.executorService = Executors.newFixedThreadPool(threadCount, threadFactory);
        this.futures = new ArrayList<>();
    }

    public ConcurrentTestRunner run() {
        for (int i = 0; i < threadCount; i++) {
            futures.add(executorService.submit(new ConcurrentRunnableTask(i, biConsumer, suppressLogger)));
        }
        return this;
    }

    public ConcurrentTestRunner assertNoException() throws ExecutionException, InterruptedException {
        for (Future<?> future: futures) {
            future.get();
        }
        return this;
    }

    public ConcurrentTestRunner awaitTermination(Duration duration) throws InterruptedException {
        executorService.shutdown();
        boolean terminated = executorService.awaitTermination(duration.toMillis(), TimeUnit.MILLISECONDS);
        if (!terminated) {
            throw new NotTerminatedException();
        }
        return this;
    }

    public ConcurrentTestRunner runSuccessfullyWithin(Duration duration) throws InterruptedException, ExecutionException {
        return run()
            .awaitTermination(duration)
            .assertNoException();
    }

    public ConcurrentTestRunner runAcceptingErrorsWithin(Duration duration) throws InterruptedException, ExecutionException {
        return run()
            .awaitTermination(duration);
    }


    @Override
    public void close() throws IOException {
        executorService.shutdownNow();
    }
}
