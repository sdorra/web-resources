package com.github.sdorra.internal;

import com.github.sdorra.ContentTypeResolver;

class TikaContentTypeResolverTest extends ContentTypeResolverTestBase {
    @Override
    ContentTypeResolver create() {
        return new TikaContentTypeResolver();
    }
}
