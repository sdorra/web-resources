package com.github.sdorra;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;


class ContentTypeResolverTest {

    @Test
    public void testResolve() {
        String type = ContentTypeResolver.resolve("hello.txt");
        assertThat(type).isEqualTo("text/plain");
    }

}
