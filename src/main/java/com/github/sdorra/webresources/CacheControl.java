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

import java.util.concurrent.TimeUnit;

/**
 * CacheControl can be used to create the value for the Cache-Control header.
 * The Cache-Control general-header field is used to specify directives for caching mechanisms in both requests and
 * responses. Caching directives are unidirectional, meaning that a given directive in a request is not implying that
 * the same directive is to be given in the response.
 *
 * Documentation is copied from the excellent Mozilla Wiki.
 *
 * @since 1.1.0
 * @see <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Cache-Control">Mozilla Cache-Control</a>
 */
public final class CacheControl {

    private final StringBuilder value = new StringBuilder();

    private CacheControl() {
    }

    /**
     * Creates a new Cache-Control.
     *
     * @return new Cache-Control
     */
    public static CacheControl create() {
        return new CacheControl();
    }

    /**
     * The cache must verify the status of the stale resources before using it and expired ones should not be used.
     *
     * @return {@code this}
     */
    public CacheControl mustRevalidate() {
        return append("must-revalidate");
    }

    /**
     * Forces caches to submit the request to the origin server for validation before releasing a cached copy.
     *
     * @return {@code this}
     */
    public CacheControl noCache() {
        return append("no-cache");
    }

    /**
     * The cache should not store anything about the client request or server response.
     *
     * @return {@code this}
     */
    public CacheControl noStore() {
        return append("no-store");
    }

    /**
     * No transformations or conversions should be made to the resource. The Content-Encoding, Content-Range,
     * Content-Type headers must not be modified by a proxy. A non- transparent proxy might, for example,
     * convert between image formats in order to save cache space or to reduce the amount of traffic on a slow link.
     * The no-transform directive disallows this.
     *
     * @return {@code this}
     */
    public CacheControl noTransform() {
        return append("no-transform");
    }

    /**
     * Indicates that the response may be cached by any cache, even if the response would normally be non-cacheable
     * (e.g. if the response does not contain a max-age directive or the Expires header).
     *
     * @return {@code this}
     */
    public CacheControl publicCache() {
        return append("public");
    }

    /**
     * Indicates that the response is intended for a single user and must not be stored by a shared cache.
     * A private cache may store the response.
     *
     * @return {@code this}
     */
    public CacheControl privateCache() {
        return append("private");
    }

    /**
     * Same as must-revalidate, but it only applies to shared caches (e.g., proxies) and is ignored by a private cache.
     * @return {@code this}
     */
    public CacheControl proxyRevalidate() {
        return append("proxy-revalidate");
    }

    /**
     * Specifies the maximum amount of time a resource will be considered fresh. Contrary to Expires, this directive is
     * relative to the time of the request.
     *
     * @param duration duration
     * @param unit time unit
     *
     * @return {@code this}
     */
    public CacheControl maxAge(long duration, TimeUnit unit) {
        return append("max-age", duration, unit);
    }

    /**
     * Takes precedence over max-age or the Expires header, but it only applies to shared caches (e.g., proxies)
     * and is ignored by a private cache.
     *
     * @param duration duration
     * @param unit time unit
     *
     * @return {@code this}
     */
    public CacheControl sMaxAge(long duration, TimeUnit unit) {
        return append("s-maxage", duration, unit);
    }

    /**
     * Creates the string representation of the cache-control header.
     *
     * @return cache-control value as string
     */
    String build() {
        return value.toString();
    }

    /**
     * Returns {@code true} if no cache-control was added.
     *
     * @return {@code true} if it is empty
     */
    boolean isEmpty() {
        return value.length() == 0;
    }

    private CacheControl append(String cacheControl, long duration, TimeUnit unit) {
        return append( cacheControl + "=" + unit.toSeconds(duration) );
    }

    private CacheControl append(String cacheControl) {
        if (!isEmpty()) {
            value.append(", ");
        }
        value.append(cacheControl);
        return this;
    }
}
