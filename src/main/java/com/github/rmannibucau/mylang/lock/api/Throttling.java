package com.github.rmannibucau.mylang.lock.api;

import javax.enterprise.util.Nonbinding;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Retention(RUNTIME)
@Target({TYPE, METHOD})
public @interface Throttling {
    @Nonbinding
    Class<? extends SemaphoreFactory> factory() default SemaphoreFactory.class;

    @Nonbinding
    boolean fair() default false;

    @Nonbinding
    int permits() default 1;

    @Nonbinding
    String name() default "";
}
