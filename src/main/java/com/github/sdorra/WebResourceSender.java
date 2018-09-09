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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

/**
 * Serves {@link WebResource} over http, using the servlet api.
 */
public final class WebResourceSender {

    private static final int BUFFER_SIZE = 8192;

    private boolean gzip = false;
    private int bufferSize = BUFFER_SIZE;
    private long expires = -1;

    private WebResourceSender(){}

    /**
     * Creates new {@link WebResourceSender}.
     *
     * @return new web resource sender
     */
    public static WebResourceSender create() {
        return new WebResourceSender();
    }

    /**
     * Enables gzip compression.
     *
     * @return {@code this}
     */
    public WebResourceSender withGZIP() {
        gzip = true;
        return this;
    }

    /**
     * Sets the size of used buffers.
     *
     * @param bufferSize size of buffer
     *
     * @return {@code this}
     */
    public WebResourceSender withBufferSize(int bufferSize) {
        if (bufferSize <= 0) {
            throw new IllegalArgumentException("buffer size must be greater than zero");
        }
        this.bufferSize = bufferSize;
        return this;
    }

    /**
     * Sets the default expiration date for the resources.
     *
     * @param count count
     * @param unit time unit
     *
     * @return {@code this}
     */
    public WebResourceSender withExpires(long count, TimeUnit unit) {
        if (count <= 0) {
            throw new IllegalArgumentException("count must be greater than zero");
        }

        if (unit == null) {
            throw new IllegalArgumentException("time unit is required");
        }
        this.expires = unit.toMillis(count);
        return this;
    }

    /**
     * Creates a {@link WebResource} for the path and calls {@link #resource(WebResource)}.
     *
     * @param path path
     *
     * @return sender
     *
     * @throws IOException
     */
    public Sender resource(Path path) throws IOException {
        return resource(WebResources.of(path));
    }

    /**
     * Creates a {@link WebResource} for the file and calls {@link #resource(WebResource)}.
     *
     * @param file path
     *
     * @return sender
     *
     * @throws IOException
     */
    public Sender resource(File file) throws IOException {
        return resource(WebResources.of(file));
    }

    /**
     * Creates a {@link WebResource} for the url and calls {@link #resource(WebResource)}.
     *
     * @param url url
     *
     * @return sender
     *
     * @throws IOException
     */
    public Sender resource(URL url) throws IOException {
        return resource(WebResources.of(url));
    }

    /**
     * Creates sender for the given web resource.
     *
     * @param webResource web resource
     *
     * @return sender
     *
     * @throws IOException
     */
    public Sender resource(WebResource webResource) {
        return new Sender(webResource);
    }

    /**
     * Sends a web resource to client using the servlet api.
     */
    public final class Sender {

        private final WebResource resource;
        private final String contentType;

        private Sender(WebResource resource) {
            this.resource = resource;
            contentType = getContentType();
        }

        private String getContentType() {
            Optional<String> optional = resource.getContentType();
            return optional.orElseGet(() -> ContentTypeResolver.resolve(resource.getName()));
        }

        /**
         * Sends the resource to the client. The methods will check the request method, if the request method is head
         * the resource will be send without content.
         *
         * @param request http servlet request
         * @param response http servlet response
         *
         * @throws IOException
         */
        public void send(HttpServletRequest request, HttpServletResponse response) throws IOException {
            process(request, response, !isHeadRequest(request));
        }

        /**
         * Sends the resource to the client with content.
         *
         * @param request http servlet request
         * @param response http servlet response
         *
         * @throws IOException
         */
        public void get(HttpServletRequest request, HttpServletResponse response) throws IOException {
            process(request, response, true);
        }

        /**
         * Sends the resource to the client without content.
         *
         * @param request http servlet request
         * @param response http servlet response
         *
         * @throws IOException
         */
        public void head(HttpServletRequest request, HttpServletResponse response) throws IOException {
            process(request, response, false);
        }

        private boolean isHeadRequest(HttpServletRequest request) {
            return "HEAD".equalsIgnoreCase(request.getMethod());
        }

        private void process(HttpServletRequest request, HttpServletResponse response, boolean content) throws IOException {
            // Validate request headers for caching ---------------------------------------------------

            // If-None-Match header should contain "*" or ETag. If so, then return 304.
            String ifNoneMatch = request.getHeader("If-None-Match");
            Optional<String> eTag = resource.getETag();
            if (ifNoneMatch != null && matches(ifNoneMatch, eTag)) {
                response.setHeader("ETag", eTag.get()); // Required in 304.
                response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                return;
            }

            // If-Modified-Since header should be greater than LastModified. If so, then return 304.
            // This header is ignored if any If-None-Match header is specified.
            long ifModifiedSince = request.getDateHeader("If-Modified-Since");
            if (ifNoneMatch == null && greaterOrEqual(ifModifiedSince, resource.getLastModifiedDate())) {
                setDateHeader(response, "Last-Modified", resource.getLastModifiedDate()); // Required in 304.
                response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                return;
            }

            // Validate request headers for resume ----------------------------------------------------

            // If-Match header should contain "*" or ETag. If not, then return 412.
            String ifMatch = request.getHeader("If-Match");
            if (ifMatch != null && !matches(ifMatch, eTag)) {
                response.sendError(HttpServletResponse.SC_PRECONDITION_FAILED);
                return;
            }

            // If-Unmodified-Since header should be greater than LastModified. If not, then return 412.
            long ifUnmodifiedSince = request.getDateHeader("If-Unmodified-Since");
            if (lessOrEqual(ifUnmodifiedSince, resource.getLastModifiedDate())) {
                response.sendError(HttpServletResponse.SC_PRECONDITION_FAILED);
                return;
            }

            response.setStatus(HttpServletResponse.SC_OK);
            sendHeaders(request, response);

            if (content) {
                if (isGZIPEnabled(request)) {
                    sendContentCompressed(resource, response);
                } else {
                    sendContent(resource, response);
                }
            }
        }

        private boolean isGZIPEnabled(HttpServletRequest request) {
            if (gzip) {
                return isGZIPSupported(request) && isContentCompressable();
            }
            return false;
        }

        private boolean isGZIPSupported(HttpServletRequest request) {
            String acceptEncoding = request.getHeader("Accept-Encoding");
            return acceptEncoding != null && accepts(acceptEncoding, "gzip");
        }

        private boolean isContentCompressable() {
            return !contentType.startsWith("image") && !contentType.startsWith("video");
        }

        private boolean accepts(String acceptHeader, String toAccept) {
            String[] acceptValues = acceptHeader.split("\\s*(,|;)\\s*");
            Arrays.sort(acceptValues);
            return Arrays.binarySearch(acceptValues, toAccept) > -1
                    || Arrays.binarySearch(acceptValues, toAccept.replaceAll("/.*$", "/*")) > -1
                    || Arrays.binarySearch(acceptValues, "*/*") > -1;
        }

        private void sendContentCompressed(WebResource resource, HttpServletResponse response) throws IOException {
            response.setHeader("Content-Encoding", "gzip");
            try (InputStream source = resource.getContent(); OutputStream sink = gzipOutputStream(response)) {
                copy(source, sink);
            }
        }

        private GZIPOutputStream gzipOutputStream(HttpServletResponse response) throws IOException {
            return new GZIPOutputStream(response.getOutputStream(), bufferSize);
        }

        private void sendContent(WebResource resource, HttpServletResponse response) throws IOException {
            setLongHeader(response, "Content-Length", resource.getContentLength());
            try (InputStream source = resource.getContent(); OutputStream sink = response.getOutputStream()) {
                copy(source, sink);
            }
        }

        private long copy(InputStream source, OutputStream sink) throws IOException {
            long nread = 0L;
            byte[] buf = new byte[bufferSize];
            int n;
            while ((n = source.read(buf)) > 0) {
                sink.write(buf, 0, n);
                nread += n;
            }
            return nread;
        }

        private boolean greaterOrEqual(long dateHeader, Optional<Instant> lastModified) {
            if (dateHeader > 0 && lastModified.isPresent()) {
                long value = lastModified.get().truncatedTo(ChronoUnit.SECONDS).toEpochMilli();
                return dateHeader >= value;
            }
            return false;
        }

        private boolean lessOrEqual(long dateHeader, Optional<Instant> lastModified) {
            if (dateHeader > 0  && lastModified.isPresent()) {
                long value = lastModified.get().truncatedTo(ChronoUnit.SECONDS).toEpochMilli();
                return dateHeader <= value;
            }
            return false;
        }

        private boolean matches(String matchHeader, Optional<String> etag) {
            if (matchHeader != null && etag.isPresent()) {
                String value = etag.get();
                return "*".equals(value) || matchHeader.equals(value);
            }
            return false;
        }

        private void sendHeaders(HttpServletRequest request, HttpServletResponse response) {
            response.setHeader("Content-Disposition", getContentDispositionHeader(request));
            response.setHeader("Content-Type", contentType);
            setDateHeader(response, "Last-Modified", resource.getLastModifiedDate());
            setHeader(response, "ETag", resource.getETag());

            if (expires > 0) {
                response.setDateHeader("Expires", System.currentTimeMillis() + expires);
            }
        }

        private String getContentDispositionHeader(HttpServletRequest request) {
            String disposition = "inline";

            String acceptHeader = request.getHeader("Accept");
            if (contentType.startsWith("image") && ! (acceptHeader != null && accepts(acceptHeader, contentType))) {
                disposition = "attachment";
            }
            return String.format("%s;filename=\"%s\"", disposition, resource.getName());
        }

        private void setHeader(HttpServletResponse response, String name, Optional<String> value) {
            value.ifPresent(s -> response.setHeader(name, s));
        }

        private void setDateHeader(HttpServletResponse response, String name, Optional<Instant> value) {
            value.ifPresent(instant -> response.setDateHeader(name, toEpoch(instant)));
        }

        private void setLongHeader(HttpServletResponse response, String name, Optional<Long> value) {
            value.ifPresent(aLong -> response.setHeader(name, String.valueOf(aLong)));
        }

        private long toEpoch(Instant instant) {
            return instant.truncatedTo(ChronoUnit.SECONDS).toEpochMilli();
        }
    }
}
