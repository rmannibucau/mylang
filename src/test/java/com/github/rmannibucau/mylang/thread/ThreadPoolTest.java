package com.github.rmannibucau.mylang.thread;

import com.github.rmannibucau.mylang.thread.api.ThreadPool;
import com.github.rmannibucau.mylang.thread.api.ThreadPoolManager;
import org.apache.openejb.junit.ApplicationComposer;
import org.apache.openejb.testing.Classes;
import org.apache.openejb.testing.Default;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.enterprise.context.Dependent;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

@Default
@Classes(cdi = true)
@RunWith(ApplicationComposer.class)
public class ThreadPoolTest {
    @Inject
    @ThreadPool("test")
    private ExecutorService executorService;

    @Test
    public void ensurePoolIsCreatedAndUsable() throws ExecutionException {
        assertNotNull(executorService);
        try {
            assertEquals("foo", executorService.submit(() -> "foo").get());
        } catch (final InterruptedException e) {
            Thread.interrupted();
            fail();
        }
        assertEquals(2, ThreadPoolExecutor.class.cast(executorService).getCorePoolSize());
        assertEquals(4, ThreadPoolExecutor.class.cast(executorService).getMaximumPoolSize());
    }

    @Dependent
    public static class Registration {
        public void register(@Observes final ThreadPoolManager mgr) {
            mgr.register("test").withMax(4).withCore(2).add();
        }
    }
}
