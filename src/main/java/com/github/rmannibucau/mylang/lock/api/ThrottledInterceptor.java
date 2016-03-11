package com.github.rmannibucau.mylang.lock.api;

import javax.annotation.Priority;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Typed;
import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Inject;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static java.util.Optional.ofNullable;

@Throttled
@Interceptor
@Priority(Interceptor.Priority.LIBRARY_BEFORE)
public class ThrottledInterceptor implements Serializable {
    @Inject
    private LocalCache metadata;

    @AroundInvoke
    public Object invoke(final InvocationContext ic) throws Exception {
        return metadata.getOrCreateInvocation(ic).invoke(ic);
    }

    private static Semaphore onInterruption(final InterruptedException e) {
        Thread.interrupted();
        throw new IllegalStateException("acquire() interrupted", e);
    }

    @ApplicationScoped
    @Typed(LocalCache.class)
    static class LocalCache implements SemaphoreFactory {
        private final ConcurrentMap<String, Semaphore> semaphores = new ConcurrentHashMap<>();
        private final ConcurrentMap<Method, Invocation> providers = new ConcurrentHashMap<>();

        @Inject
        private BeanManager beanManager;

        Invocation getOrCreateInvocation(final InvocationContext ic) {
            return providers.computeIfAbsent(ic.getMethod(), method -> {
                final Class declaringClass = method.getDeclaringClass();
                final AnnotatedType<Object> annotatedType = beanManager.createAnnotatedType(declaringClass);
                final Optional<AnnotatedMethod<? super Object>> annotatedMethod = annotatedType.getMethods().stream()
                    .filter(am -> am.getJavaMember().equals(method))
                    .findFirst();

                final Throttled config = annotatedMethod
                    .map(am -> am.getAnnotation(Throttled.class))
                    .orElseGet(() -> annotatedType.getAnnotation(Throttled.class));
                final Optional<Throttling> sharedConfig =
                    ofNullable(annotatedMethod.map(am -> am.getAnnotation(Throttling.class))
                        .orElseGet(() -> annotatedType.getAnnotation(Throttling.class)));

                final SemaphoreFactory factory = sharedConfig.map(Throttling::factory).filter(f -> f != SemaphoreFactory.class)
                    .map(c -> SemaphoreFactory.class.cast(beanManager.getReference(beanManager.resolve(beanManager.getBeans(c)), SemaphoreFactory.class, null)))
                    .orElse(this);

                final Semaphore semaphore = factory.newSemaphore(
                    annotatedMethod.orElseThrow(() -> new IllegalStateException("No annotated method for " + method)),
                    sharedConfig.map(Throttling::name).orElseGet(declaringClass::getName),
                    sharedConfig.map(Throttling::fair).orElse(false), sharedConfig.map(Throttling::permits).orElse(1));
                final long timeout = config.timeoutUnit().toMillis(config.timeout());
                final int weigth = config.weight();
                return new Invocation(semaphore, weigth, timeout);
            });
        }

        @Override
        public Semaphore newSemaphore(final AnnotatedMethod<?> method, final String name, final boolean fair, final int permits) {
            return semaphores.computeIfAbsent(name, key -> new Semaphore(permits, fair));
        }
    }

    private static final class Invocation {
        private final int weight;
        private final Semaphore semaphore;
        private final long timeout;

        private Invocation(final Semaphore semaphore, final int weight, final long timeout) {
            this.semaphore = semaphore;
            this.weight = weight;
            this.timeout = timeout;
        }

        Object invoke(final InvocationContext context) throws Exception {
            if (timeout > 0) {
                try {
                    if (!semaphore.tryAcquire(weight, timeout, TimeUnit.MILLISECONDS)) {
                        throw new IllegalStateException("Can't acquire " + weight + " permits for " + context.getMethod() + " in " + timeout + "ms");
                    }
                } catch (final InterruptedException e) {
                    return onInterruption(e);
                }
            } else {
                try {
                    semaphore.acquire(weight);
                } catch (final InterruptedException e) {
                    return onInterruption(e);
                }
            }
            try {
                return context.proceed();
            } finally {
                semaphore.release(weight);
            }
        }
    }
}
