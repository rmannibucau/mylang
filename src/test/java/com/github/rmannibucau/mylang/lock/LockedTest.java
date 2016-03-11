package com.github.rmannibucau.mylang.lock;

import com.github.rmannibucau.mylang.lock.api.Locked;
import org.apache.openejb.junit.ApplicationComposer;
import org.apache.openejb.testing.Classes;
import org.apache.openejb.testing.Default;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.github.rmannibucau.mylang.lock.api.Locked.Operation.WRITE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@Default
@Classes(cdi = true)
@RunWith(ApplicationComposer.class)
public class LockedTest {
    @Inject
    private LockService service;

    @Test
    public void simpleNotConcurrent() {
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
    public void concurrentTimeout() {
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
    public static class LockService {
        private final Map<String, String> entries = new HashMap<>();

        @Locked(timeout = 1, timeoutUnit = TimeUnit.SECONDS)
        public String read(final String k) {
            return entries.get(k);
        }

        @Locked(timeout = 1, timeoutUnit = TimeUnit.SECONDS, operation = WRITE)
        public void write(final String k, final String v) {
            entries.put(k, v);
        }

        @Locked(operation = WRITE)
        public void force() {
            try {
                Thread.sleep(TimeUnit.SECONDS.toMillis(5));
            } catch (final InterruptedException e) {
                Thread.interrupted();
                fail();
            }
        }
    }
}
