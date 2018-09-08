package com.github.sdorra.internal;

import com.github.sdorra.ContentTypeResolver;
import com.github.sdorra.spotter.ContentTypes;

public class SpotterContentTypeResolver extends ContentTypeResolver {
    @Override
    public String detect(String name) {
        return ContentTypes.detect(name).getRaw();
    }
}
