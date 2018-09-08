package com.github.sdorra.internal;

import com.github.sdorra.ContentTypeResolver;

class SpotterContentTypeResolverTest extends ContentTypeResolverTestBase {
    @Override
    ContentTypeResolver create() {
        return new SpotterContentTypeResolver();
    }
}
