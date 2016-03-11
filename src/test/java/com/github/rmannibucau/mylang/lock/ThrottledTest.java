package com.github.rmannibucau.mylang.lock;

import com.github.rmannibucau.mylang.lock.api.Throttled;
import com.github.rmannibucau.mylang.lock.api.Throttling;
import org.apache.openejb.junit.ApplicationComposer;
import org.apache.openejb.testing.Classes;
import org.apache.openejb.testing.Default;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

@Default
@Classes(cdi = true)
@RunWith(ApplicationComposer.class)
public class ThrottledTest {
    @Inject
    private Service service;

    @Inject
    private Service2 service2;

    @Test
    public void permits() {
        {// failling case now
            final AtomicReference<Exception> failed = new AtomicReference<>();
            final CountDownLatch latch = new CountDownLatch(2);
            final Thread[] concurrents = new Thread[]{
                new Thread() {
                    @Override
                    public void run() {
                        service2.heavy(latch::countDown);
                    }
                },
                new Thread() {
                    @Override
                    public void run() {
                        try {
                            latch.await();
                        } catch (final InterruptedException e) {
                            Thread.interrupted();
                            fail();
                        }
                        try {
                            service2.call("failed");
                            fail();
                        } catch (final IllegalStateException ise) {
                            failed.set(ise);
                        }
                    }
                }
            };
            Stream.of(concurrents).forEach(Thread::start);
            latch.countDown();
            waitForThreads(concurrents);
            assertNotNull(failed.get());
            assertThat(failed.get(), instanceOf(IllegalStateException.class));
        }
        { // passing
            final CountDownLatch latch = new CountDownLatch(1);
            final Thread[] concurrents = new Thread[]{
                new Thread() {
                    @Override
                    public void run() {
                        try {
                            latch.await();
                        } catch (final InterruptedException e) {
                            Thread.interrupted();
                            fail();
                        }
                        service2.call("1");
                    }
                },
                new Thread() {
                    @Override
                    public void run() {
                        try {
                            latch.await();
                        } catch (final InterruptedException e) {
                            Thread.interrupted();
                            fail();
                        }
                        service2.call("2");
                    }
                }
            };
            Stream.of(concurrents).forEach(Thread::start);
            latch.countDown();
            waitForThreads(concurrents);
            assertEquals(new HashSet<>(asList("1", "2")), new HashSet<>(service2.getCalled()));
        }
    }

    private void waitForThreads(final Thread[] concurrents) {
        Stream.of(concurrents).forEach(t -> {
            try {
                t.join();
            } catch (final InterruptedException e) {
                Thread.interrupted();
                fail();
            }
        });
    }

    @Test
    public void simpleNotConcurrent() { // ~lock case
        final CountDownLatch synchro = new CountDownLatch(1);
        final Thread writer = new Thread() {
            @Override
            public void run() {
                service.write("test", "value");
                synchro.countDown();
            }
        };

        final CountDownLatch end = new CountDownLatch(1);
        final AtomicReference<String> val = new AtomicReference<>();
        final Thread reader = new Thread() {
            @Override
            public void run() {
                try {
                    synchro.await(1, TimeUnit.MINUTES);
                } catch (final InterruptedException e) {
                    Thread.interrupted();
                    fail();
                }
                val.set(service.read("test"));
                end.countDown();
            }
        };

        reader.start();
        writer.start();
        try {
            end.await(1, TimeUnit.MINUTES);
        } catch (final InterruptedException e) {
            Thread.interrupted();
            fail();
        }
        assertEquals("value", val.get());
    }

    @Test
    public void concurrentTimeout() { // ~lock case
        final AtomicBoolean doAgain = new AtomicBoolean(true);
        final CountDownLatch endWriter = new CountDownLatch(1);
        final Thread writer = new Thread() {
            @Override
            public void run() {
                while (doAgain.get()) {
                    service.write("test", "value");
                    service.force();
                }
                endWriter.countDown();
            }
        };

        final CountDownLatch endReader = new CountDownLatch(1);
        final Thread reader = new Thread() {
            @Override
            public void run() {
                while (doAgain.get()) {
                    try {
                        service.read("test");
                    } catch (final IllegalStateException e) {
                        doAgain.set(false);
                    }
                }
                endReader.countDown();
            }
        };

        reader.start();
        writer.start();
        try {
            endReader.await(1, TimeUnit.MINUTES);
            endWriter.await(1, TimeUnit.MINUTES);
        } catch (final InterruptedException e) {
            Thread.interrupted();
            fail();
        }
        assertEquals("value", service.read("test"));
    }

    @ApplicationScoped
    public static class Service {
        private final Map<String, String> entries = new HashMap<>();

        @Throttled(timeout = 1, timeoutUnit = TimeUnit.SECONDS)
        public String read(final String k) {
            return entries.get(k);
        }

        @Throttled(timeout = 1, timeoutUnit = TimeUnit.SECONDS)
        public void write(final String k, final String v) {
            entries.put(k, v);
        }

        @Throttled
        public void force() {
            try {
                Thread.sleep(TimeUnit.SECONDS.toMillis(5));
            } catch (final InterruptedException e) {
                Thread.interrupted();
                fail();
            }
        }
    }

    @Throttling(permits = 2)
    @ApplicationScoped
    public static class Service2 {
        private final Collection<String> called = new ArrayList<>();

        @Throttled(timeout = 750)
        public void call(final String k) {
            synchronized (called) {
                called.add(k);
            }
            try {
                Thread.sleep(1000);
            } catch (final InterruptedException e) {
                Thread.interrupted();
            }
        }

        @Throttled(weight = 2)
        public void heavy(final Runnable inTask) {
            inTask.run();
            try {
                Thread.sleep(5000);
            } catch (final InterruptedException e) {
                Thread.interrupted();
            }
        }

        public Collection<String> getCalled() {
            return called;
        }
    }
}
