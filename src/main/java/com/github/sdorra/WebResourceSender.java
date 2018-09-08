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
import java.util.zip.GZIPOutputStream;

public final class WebResourceSender {

    private static final int BUFFER_SIZE = 8192;

    private boolean gzip = false;
    private int bufferSize = BUFFER_SIZE;

    private WebResourceSender(){}

    public static WebResourceSender create() {
        return new WebResourceSender();
    }

    public WebResourceSender withGZIP() {
        gzip = true;
        return this;
    }

    public WebResourceSender withBufferSize(int bufferSize) {
        if (bufferSize <= 0) {
            throw new IllegalArgumentException("buffer size must be greater than zero");
        }
        this.bufferSize = bufferSize;
        return this;
    }

    public Sender resource(Path path) throws IOException {
        return resource(WebResources.of(path));
    }

    public Sender resource(File file) throws IOException {
        return resource(WebResources.of(file));
    }

    public Sender resource(URL url) throws IOException {
        return resource(WebResources.of(url));
    }

    public Sender resource(WebResource webResource) {
        return new Sender(webResource);
    }

    public final class Sender {

        private final WebResource resource;

        private Sender(WebResource resource) {
            this.resource = resource;
        }

        public void send(HttpServletRequest request, HttpServletResponse response) throws IOException {
            process(request, response, !isHeadRequest(request));
        }

        public void get(HttpServletRequest request, HttpServletResponse response) throws IOException {
            process(request, response, true);
        }

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
            sendHeaders(response);

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
                String acceptEncoding = request.getHeader("Accept-Encoding");
                return acceptEncoding != null && accepts(acceptEncoding, "gzip");
            }
            return false;
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

        private void sendHeaders(HttpServletResponse response) {
            response.setHeader("Content-Type", getContentType());
            setDateHeader(response, "Last-Modified", resource.getLastModifiedDate());
            setHeader(response, "ETag", resource.getETag());
        }

        private String getContentType() {
            Optional<String> contentType = resource.getContentType();
            return contentType.orElseGet(() -> ContentTypeResolver.resolve(resource.getName()));
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
