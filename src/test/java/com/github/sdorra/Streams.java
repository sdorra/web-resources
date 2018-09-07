package com.github.sdorra;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class Streams {

    private Streams() {

    }

    static void copy(InputStream source, OutputStream sink) throws IOException {
        byte[] buf = new byte[8192];
        int n;
        while ((n = source.read(buf)) > 0) {
            sink.write(buf, 0, n);
        }
    }

    static String toString(InputStream inputStream) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Streams.copy(inputStream, output);
        return output.toString("UTF-8");
    }

}
