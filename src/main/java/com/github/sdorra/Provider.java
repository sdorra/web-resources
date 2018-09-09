package com.github.sdorra;

@FunctionalInterface
public interface Provider<T, E extends Throwable> {

    T get() throws E;

}
