package com.github.sdorra.internal;

import com.github.sdorra.ContentTypeResolver;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

abstract class ContentTypeResolverTestBase {

    abstract ContentTypeResolver create();

    @Test
    public void testText() {
        assertThat(create().detect("hello.txt")).isEqualTo("text/plain");
    }

    @Test
    public void testImage() {
        assertThat(create().detect("hello.jpg")).isEqualTo("image/jpeg");
    }

}
