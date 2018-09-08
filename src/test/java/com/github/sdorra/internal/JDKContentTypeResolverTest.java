package com.github.sdorra.internal;

import com.github.sdorra.ContentTypeResolver;

class JDKContentTypeResolverTest extends ContentTypeResolverTestBase {
    @Override
    ContentTypeResolver create() {
        return new JDKContentTypeResolver();
    }
}
