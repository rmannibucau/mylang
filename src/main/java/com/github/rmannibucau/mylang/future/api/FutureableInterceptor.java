package com.github.rmannibucau.mylang.future.api;

import com.github.rmannibucau.mylang.thread.api.ThreadPoolManager;

import javax.annotation.Priority;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Inject;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static java.util.Optional.ofNullable;

@Interceptor
@Futureable("")
@Priority(Interceptor.Priority.APPLICATION)
public class FutureableInterceptor implements Serializable {
    @Inject
    private ThreadPoolManager manager;

    @Inject
    private BeanManager beanManager;

    private transient ConcurrentMap<Method, Executor> configByMethod = new ConcurrentHashMap<>();

    @AroundInvoke
    public Object invoke(final InvocationContext ic) throws Exception {
        // validate usage
        final Class<?> returnType = ic.getMethod().getReturnType();
        if (!CompletionStage.class.isAssignableFrom(returnType) && !Future.class.isAssignableFrom(returnType)) {
            throw new IllegalArgumentException("Return type should be a CompletableStage or Future");
        }

        // in case of serialization reinit internal cache
        if (configByMethod == null) {
            synchronized (this) {
                if (configByMethod == null) {
                    configByMethod = new ConcurrentHashMap<>();
                }
            }
        }

        final AtomicReference<Supplier<?>> cancelHook = new AtomicReference<>();
        return CompletableFuture.supplyAsync(() -> {
            try {
                final Object proceed = ic.proceed();
                if (CompletionStage.class.isInstance(proceed)) {
                    final CompletableFuture completableFuture = CompletionStage.class.cast(proceed).toCompletableFuture();
                    cancelHook.set(() -> completableFuture.cancel(true));
                    return completableFuture.get();
                } else {
                    final Future<?> future = Future.class.cast(proceed);
                    cancelHook.set(() -> future.cancel(true));
                    return future.get();
                }
            } catch (final InvocationTargetException e) {
                throw rethrow(e.getCause());
            } catch (final Exception e) {
                throw rethrow(e);
            }
        }, getOrCreatePool(ic))
            .whenComplete((r, e) -> ofNullable(e).filter(CancellationException.class::isInstance)
                .ifPresent(ce -> ofNullable(cancelHook.get()).ifPresent(Supplier::get)));
    }

    private RuntimeException rethrow(final Throwable cause) {
        if (RuntimeException.class.isInstance(cause)) {
            return RuntimeException.class.cast(cause);
        }
        return new IllegalStateException(cause);
    }

    private Executor getOrCreatePool(final InvocationContext ic) {
        return configByMethod.computeIfAbsent(ic.getMethod(), m -> {
            final AnnotatedType<?> annotatedType = beanManager.createAnnotatedType(m.getDeclaringClass());
            return manager.getOrCreatePool(
                annotatedType.getMethods().stream().filter(am -> am.getJavaMember().equals(m))
                    .findFirst()
                    .map(am -> am.getAnnotation(Futureable.class))
                    .orElseGet(() -> annotatedType.getAnnotation(Futureable.class)).value());
        });
    }
}
