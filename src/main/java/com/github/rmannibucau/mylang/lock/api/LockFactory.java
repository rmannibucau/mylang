package com.github.rmannibucau.mylang.lock.api;

import javax.enterprise.inject.spi.AnnotatedMethod;
import java.util.concurrent.locks.ReadWriteLock;

public interface LockFactory {
    ReadWriteLock newLock(final AnnotatedMethod<?> method, final boolean fair);
}
