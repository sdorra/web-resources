package com.github.sdorra;

import java.io.IOException;
import java.io.InputStream;

@FunctionalInterface
public interface ContentProvider {
    InputStream get() throws IOException;
}
