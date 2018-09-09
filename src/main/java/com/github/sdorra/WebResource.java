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
package com.github.sdorra;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Optional;

/**
 * {@link WebResource} is a resource which could be send by the {@link WebResourceSender}.
 * To create a {@link WebResource} one of the methods of {@link WebResources} should be used.
 */
public interface WebResource {

    /**
     * Returns the name of the resource.
     *
     * @return name
     */
    String getName();

    /**
     * Returns the content of the resource as input stream.
     *
     * @return input stream of content
     *
     * @throws IOException If an input or output exception occurs
     */
    InputStream getContent() throws IOException;

    /**
     * Returns optional content length
     *
     * @return optional content length
     */
    Optional<Long> getContentLength();

    /**
     * Returns optional content type
     *
     * @return optional content type
     */
    Optional<String> getContentType();

    /**
     * Returns optional etag
     *
     * @return optional etag
     */
    Optional<String> getETag();

    /**
     * Returns optional last modified date
     *
     * @return optional last modified date
     */
    Optional<Instant> getLastModifiedDate();

}
