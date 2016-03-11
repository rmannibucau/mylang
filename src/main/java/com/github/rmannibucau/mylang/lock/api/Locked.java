package com.github.rmannibucau.mylang.lock.api;

import javax.enterprise.util.Nonbinding;
import javax.interceptor.InterceptorBinding;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@InterceptorBinding
@Retention(RUNTIME)
@Target({TYPE, METHOD})
public @interface Locked {
    @Nonbinding
    boolean fair() default false;

    @Nonbinding
    Operation operation() default Operation.READ;

    @Nonbinding
    Class<? extends LockFactory> factory() default LockFactory.class;

    @Nonbinding
    long timeout() default 0L;

    @Nonbinding
    TimeUnit timeoutUnit() default TimeUnit.MILLISECONDS;

    enum Operation {
        READ, WRITE;
    }
}
