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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Optional;

/**
 * {@link WebResources} provides util methods to create {@link WebResource}.
 */
public final class WebResources {

    private WebResources(){}

    /**
     * Creates a {@link WebResource} from a {@link URL}, if the url is a file url {@link #of(Path)} is internally used.
     * Warning: This method should not be used with an external url.
     *
     * @param url source url
     *
     * @return web resource
     *
     * @throws IOException if web resource could not be created
     */
    public static WebResource of(URL url) throws IOException {
        if ("file".equals(url.getProtocol())) {
            try {
                return of(Paths.get(url.toURI()));
            } catch (URISyntaxException ex) {
                throw new IOException("failed to get path from uri", ex);
            }
        }

        URLConnection connection = url.openConnection();
        String name = name(url);
        long size = connection.getContentLengthLong();
        long lastModified = connection.getLastModified();

        return builder(name, () -> url.openConnection().getInputStream())
                .withContentLength(size)
                .withLastModifiedDate(lastModified)
                .withContentType(ContentTypeResolver.resolve(url.getPath()))
                .withETag(etag(name, size, lastModified))
                .build();
    }

    private static String name(URL url) {
        String path = url.getPath();
        int index = path.lastIndexOf('/');
        if (index > 0) {
            return path.substring(index + 1);
        }
        return path;
    }

    /**
     * Creates a {@link WebResource} from a file.
     *
     * @param file source file
     *
     * @return web resource
     *
     * @throws IOException if web resource could not be created
     */
    public static WebResource of(File file) throws IOException {
        return of(file.toPath());
    }

    /**
     * Creates a {@link WebResource} from a path.
     *
     * @param path source path
     *
     * @return web resource
     *
     * @throws IOException if web resource could not be created
     */
    public static WebResource of(Path path) throws IOException {
        String name = path.getFileName().toString();
        return builder(name, () -> Files.newInputStream(path))
                .withContentLength(Files.size(path))
                .withLastModifiedDate(Files.getLastModifiedTime(path).toInstant())
                .withContentType(ContentTypeResolver.resolve(path.toString()))
                .withETag(etag(path))
                .build();
    }

    /**
     * Creates builder for a {@link WebResource}.
     *
     * @param name name of the resource
     * @param contentThrowingSupplier content provider
     *
     * @return web resource builder
     */
    public static Builder builder(String name, ThrowingSupplier<InputStream, IOException> contentThrowingSupplier) {
        return new Builder(name, contentThrowingSupplier);
    }

    /**
     * Creates an etag from the given file path.
     * Note: This method is only visible for testing
     *
     * @param path file path
     *
     * @return etag
     *
     * @throws IOException If an input or output exception occurs
     */
    static String etag(Path path) throws IOException {
        String name = path.getFileName().toString();
        long size = Files.size(path);
        long lastModified = Files.getLastModifiedTime(path).toMillis();
        return etag(name, size, lastModified);
    }

    private static String etag(String name, long size, long lastModifid) {
        return String.format("%s_%s_%s", name, size, lastModifid);
    }

    /**
     * Web resource builder.
     */
    public static class Builder {

        private final WebResourceImpl resource;

        private Builder(String name, ThrowingSupplier<InputStream, IOException> contentSupplier) {
            this.resource = new WebResourceImpl(name, contentSupplier);
        }

        /**
         * Sets length of web resource content.
         *
         * @param length content length
         *
         * @return {@code this}
         */
        public Builder withContentLength(Long length) {
            if (length != null && length >= 0) {
                resource.contentLength = length;
            }
            return this;
        }

        /**
         * Sets the content type of the web resource.
         *
         * @param contentType content type
         *
         * @return {@code this}
         */
        public Builder withContentType(String contentType) {
            resource.contentType = contentType;
            return this;
        }

        /**
         * Sets the etag of the web resource.
         *
         * @param etag etag of resource
         *
         * @return {@code this}
         */
        public Builder withETag(String etag) {
            resource.etag = etag;
            return this;
        }

        /**
         * Sets the last modified date of the web resource.
         *
         * @param lastModifiedDate in milliseconds
         *
         * @return {@code this}
         */
        public Builder withLastModifiedDate(long lastModifiedDate) {
            if (lastModifiedDate != -1) {
                resource.lastModifiedDate = Instant.ofEpochMilli(lastModifiedDate);
            }
            return this;
        }

        /**
         * Sets the last modified date of the web resource.
         *
         * @param lastModifiedDate last modified date
         *
         * @return {@code this}
         */
        public Builder withLastModifiedDate(Instant lastModifiedDate) {
            resource.lastModifiedDate = lastModifiedDate;
            return this;
        }

        /**
         * Creates web resource.
         *
         * @return web resource
         */
        public WebResource build() {
            return resource;
        }

    }

    private static class WebResourceImpl implements WebResource {

        private final String name;
        private final ThrowingSupplier<InputStream, IOException> contentSupplier;
        private Long contentLength;
        private String contentType;
        private String etag;
        private Instant lastModifiedDate;

        private WebResourceImpl(String name, ThrowingSupplier<InputStream, IOException> contentSupplier) {
            this.name = name;
            this.contentSupplier = contentSupplier;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public InputStream getContent() throws IOException {
            return contentSupplier.get();
        }

        @Override
        public Optional<Long> getContentLength() {
            return Optional.ofNullable(contentLength);
        }

        @Override
        public Optional<String> getContentType() {
            return Optional.ofNullable(contentType);
        }

        @Override
        public Optional<String> getETag() {
            return Optional.ofNullable(etag);
        }

        @Override
        public Optional<Instant> getLastModifiedDate() {
            return Optional.ofNullable(lastModifiedDate);
        }
    }

}
