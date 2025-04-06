package de.malkusch.niu;

import dev.failsafe.Failsafe;
import dev.failsafe.FailsafeException;
import dev.failsafe.FailsafeExecutor;
import dev.failsafe.RetryPolicy;

import java.io.IOException;
import java.time.Duration;

interface Retry<T> {

    record Configuration(int retries, Duration delay) {

        static final Configuration DISABLED = new Configuration(0, Duration.ZERO);

        boolean isDisabled() {
            return this == DISABLED;
        }
    }

    static <T> Retry<T> build(Configuration configuration) {
        if (configuration.isDisabled()) {
            return new DisabledRetry<>();

        } else {
            return new FailSafeRetry<>(configuration);
        }
    }

    @FunctionalInterface
    interface Operation<T, E1 extends Throwable, E2 extends Throwable> {
        T execute() throws E1, E2;
    }

    <E1 extends Throwable, E2 extends Throwable> T retry(Operation<T, E1, E2> operation) throws E1, E2;

    final class DisabledRetry<T> implements Retry<T> {

        @Override
        public <E1 extends Throwable, E2 extends Throwable> T retry(Operation<T, E1, E2> operation) throws E1, E2 {
            return operation.execute();
        }
    }

    final class FailSafeRetry<T> implements Retry<T> {

        private final FailsafeExecutor<T> failsafe;

        FailSafeRetry(Configuration configuration) {
            failsafe = Failsafe.with( //
                    RetryPolicy.<T>builder() //
                            .handle(IOException.class) //
                            .withMaxRetries(configuration.retries()) //
                            .withDelay(configuration.delay()) //
                            .build());
        }

        @Override
        public <E1 extends Throwable, E2 extends Throwable> T retry(Operation<T, E1, E2> operation) throws E1, E2 {
            try {
                return failsafe.get(operation::execute);

            } catch (FailsafeException e) {
                var cause = e.getCause();
                throw (E1) cause;
            }
        }
    }
}
