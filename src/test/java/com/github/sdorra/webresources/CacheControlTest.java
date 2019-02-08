/**
 * The MIT License
 * Copyright (c) 2018 Sebastian Sdorra
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.github.sdorra.webresources;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class CacheControlTest {

    @Test
    void shouldAppendValues() {
        CacheControl cacheControl = CacheControl.create().noCache().noStore().mustRevalidate();
        assertThat(cacheControl.build()).isEqualTo("no-cache, no-store, must-revalidate");
    }

    @Test
    void shouldAppendValuesWithDuration() {
        CacheControl cacheControl = CacheControl.create().publicCache().maxAge(1, TimeUnit.HOURS);
        assertThat(cacheControl.build()).isEqualTo("public, max-age=3600");
    }

    @Test
    void shouldAppendAllValues() {
        String cacheControler = CacheControl.create()
                .noStore()
                .noCache()
                .maxAge(1, TimeUnit.SECONDS)
                .mustRevalidate()
                .publicCache()
                .noTransform()
                .privateCache()
                .proxyRevalidate()
                .sMaxAge(1, TimeUnit.SECONDS)
                .build();
        String expected = "no-store, no-cache, max-age=1, must-revalidate, public, ";
        expected += "no-transform, private, proxy-revalidate, s-maxage=1";
        assertThat(cacheControler).isEqualTo(expected);
    }

    @Test
    void shouldBeEmpty() {
        assertThat(CacheControl.create().isEmpty()).isTrue();
    }

    @Test
    void shouldBeNotEmpty() {
        assertThat(CacheControl.create().privateCache().isEmpty()).isFalse();
    }
}