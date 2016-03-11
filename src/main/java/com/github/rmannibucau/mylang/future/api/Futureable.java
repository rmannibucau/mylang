package com.github.rmannibucau.mylang.future.api;

import javax.enterprise.util.Nonbinding;
import javax.interceptor.InterceptorBinding;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@InterceptorBinding
@Retention(RUNTIME)
@Target({TYPE, METHOD})
public @interface Futureable {
    /**
     * @return pool name.
     */
    @Nonbinding
    String value();
}
