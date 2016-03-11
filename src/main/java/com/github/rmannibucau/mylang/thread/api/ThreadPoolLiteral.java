package com.github.rmannibucau.mylang.thread.api;

import javax.enterprise.util.AnnotationLiteral;

public class ThreadPoolLiteral extends AnnotationLiteral<ThreadPool> implements ThreadPool {
    private final String value;

    public ThreadPoolLiteral(final String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }
}
