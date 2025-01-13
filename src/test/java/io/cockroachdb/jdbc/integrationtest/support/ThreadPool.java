package io.cockroachdb.jdbc.integrationtest.support;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ThreadPool {
    public static ThreadPool unboundedPool() {
        return new ThreadPool(Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors() * 2));
    }

    public static ThreadPool boundedPool(int numThreads) {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                numThreads / 2,
                numThreads,
                0L, TimeUnit.SECONDS,
                new LinkedBlockingDeque<>(numThreads));
        executor.setRejectedExecutionHandler((runnable, exec) -> {
            try {
                exec.getQueue().put(runnable);
                if (exec.isShutdown()) {
                    throw new RejectedExecutionException(
                            "Task " + runnable + " rejected from " + exec);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RejectedExecutionException("", e);
            }
        });
        return new ThreadPool(executor);
    }

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    private final ExecutorService executorService;

    private ThreadPool(ExecutorService executorService) {
        this.executorService = executorService;
    }

    public <T> Future<T> submit(Callable<T> callable) {
        return submitAfterDelay(callable, 0);
    }

    public <T> Future<T> submitAfterDelay(Callable<T> callable, long waitTimeMillis) {
        if (executorService.isShutdown() || executorService.isTerminated()) {
            throw new IllegalStateException("Thread pool shutdown");
        }

        return executorService.submit(() -> {
            try {
                Thread.sleep(waitTimeMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            return callable.call();
        });
    }

    public <T> Future<T> submitAndWait(Callable<T> callable, long waitTimeMillis) {
        if (executorService.isShutdown() || executorService.isTerminated()) {
            throw new IllegalStateException("Thread pool shutdown");
        }
        CountDownLatch countDownLatch = new CountDownLatch(1);

        Future<T> f = executorService.submit(() -> {
            countDownLatch.countDown();
            return callable.call();
        });
        if (waitTimeMillis > 0) {
            try {
                countDownLatch.await();
                Thread.sleep(waitTimeMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        return f;
    }

    public <T> T awaitFuture(Future<T> future) throws ExecutionException, TimeoutException {
        try {
            return future.get(20, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
