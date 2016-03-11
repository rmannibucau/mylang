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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

import static com.github.rmannibucau.mylang.lock.api.Locked.Operation.READ;
import static java.util.Optional.of;

@Locked
@Interceptor
@Priority(Interceptor.Priority.LIBRARY_BEFORE)
public class LockedInterceptor implements Serializable {
    @Inject
    private Locks locks;

    @AroundInvoke
    public Object invoke(final InvocationContext ic) throws Exception {
        final Lock l = locks.lockAcquirer(ic).get();
        try {
            return ic.proceed();
        } finally {
            l.unlock();
        }
    }

    @ApplicationScoped
    @Typed(Locks.class)
    static class Locks implements LockFactory {
        private final ConcurrentMap<String, ReadWriteLock> locks = new ConcurrentHashMap<>();

        // read or write
        private final ConcurrentMap<Method, Supplier<Lock>> lockOperations = new ConcurrentHashMap<>();

        @Inject
        private BeanManager beanManager;

        Supplier<Lock> lockAcquirer(final InvocationContext ic) {
            return lockOperations.computeIfAbsent(ic.getMethod(), method -> {
                final Class declaringClass = method.getDeclaringClass();
                final AnnotatedType<Object> annotatedType = beanManager.createAnnotatedType(declaringClass);
                final Optional<AnnotatedMethod<? super Object>> annotatedMethod = annotatedType.getMethods().stream()
                    .filter(am -> am.getJavaMember().equals(method))
                    .findFirst();

                final Locked locked = annotatedMethod
                    .map(am -> am.getAnnotation(Locked.class))
                    .orElseGet(() -> annotatedType.getAnnotation(Locked.class));

                final LockFactory factory = of(locked.factory()).filter(f -> f != LockFactory.class)
                    .map(c -> LockFactory.class.cast(beanManager.getReference(beanManager.resolve(beanManager.getBeans(c)), LockFactory.class, null)))
                    .orElse(this);

                final ReadWriteLock writeLock = factory.newLock(annotatedMethod.orElseThrow(() -> new IllegalStateException("No annotated method for " + method)), locked.fair());
                final long timeout = locked.timeoutUnit().toMillis(locked.timeout());
                final Lock lock = locked.operation() == READ ? writeLock.readLock() : writeLock.writeLock();

                if (timeout > 0) {
                    return () -> {
                        try {
                            if (!lock.tryLock(timeout, TimeUnit.MILLISECONDS)) {
                                throw new IllegalStateException("Can't lock for " + method + " in " + timeout + "ms");
                            }
                        } catch (final InterruptedException e) {
                            Thread.interrupted();
                            throw new IllegalStateException("Locking interrupted", e);
                        }
                        return lock;
                    };
                }
                return () -> {
                    lock.lock();
                    return lock;
                };
            });
        }

        @Override
        public ReadWriteLock newLock(final AnnotatedMethod<?> method, final boolean fair) {
            return locks.computeIfAbsent(method.getJavaMember().getDeclaringClass().getName(), key -> new ReentrantReadWriteLock(fair));
        }
    }
}
