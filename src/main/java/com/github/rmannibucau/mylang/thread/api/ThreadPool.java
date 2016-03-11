package com.github.rmannibucau.mylang.thread.api;

import javax.enterprise.util.Nonbinding;
import javax.inject.Qualifier;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Qualifier
@Target({METHOD, FIELD, PARAMETER})
@Retention(RUNTIME)
public @interface ThreadPool {
    /**
     * @return the name of the pool to use.
     */
    @Nonbinding
    String value();
}
