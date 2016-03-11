package com.github.rmannibucau.mylang.lock.api;

import javax.enterprise.inject.spi.AnnotatedMethod;
import java.util.concurrent.Semaphore;

public interface SemaphoreFactory {
    Semaphore newSemaphore(AnnotatedMethod<?> method, String name, boolean fair, int permits);
}
