package com.github.rmannibucau.mylang.future;

import com.github.rmannibucau.mylang.future.api.Futureable;
import org.apache.openejb.junit.ApplicationComposer;
import org.apache.openejb.testing.Classes;
import org.apache.openejb.testing.Default;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@Default
@Classes(cdi = true)
@RunWith(ApplicationComposer.class)
public class FutureableTest {
    @Inject
    private Service service;

    @Test
    public void future() {
        final CompletableFuture<String> future = service.thatSLong(1000);
        int count = 0;
        for (int i = 0; i < 1000; i++) {
            if (future.isDone()) {
                break;
            }
            count++;
        }
        try {
            assertEquals("done", future.get());
        } catch (final InterruptedException e) {
            Thread.interrupted();
            fail();
        } catch (final ExecutionException e) {
            fail(e.getMessage());
        }
        assertEquals(1000, count);
    }

    @ApplicationScoped
    public static class Service {
        @Futureable("default")
        public CompletableFuture<String> thatSLong(final long sleep) {
            try {
                Thread.sleep(sleep);
                return CompletableFuture.completedFuture("done");
            } catch (final InterruptedException e) {
                Thread.interrupted();
                throw new IllegalStateException(e);
            }
        }
    }
}
