package com.github.sdorra.internal;

import com.github.sdorra.ContentTypeResolver;
import org.apache.tika.Tika;

public class TikaContentTypeResolver extends ContentTypeResolver {

    private final Tika tika = new Tika();

    @Override
    public String detect(String name) {
        return tika.detect(name);
    }
}
