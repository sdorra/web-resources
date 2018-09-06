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
import java.util.Optional;

public final class WebResourceSender {

    private WebResourceSender(){}

    private boolean gzip = false;

    public static WebResourceSender create() {
        return new WebResourceSender();
    }

    public WebResourceSender withGzip() {
        this.gzip = true;
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
            // Validate request headers for caching ---------------------------------------------------

            // If-None-Match header should contain "*" or ETag. If so, then return 304.
            String ifNoneMatch = request.getHeader("If-None-Match");
            if (ifNoneMatch != null && matches(ifNoneMatch, resource.getETag())) {
                response.setHeader("ETag", resource.getETag().get()); // Required in 304.
                response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                return;
            }

            // If-Modified-Since header should be greater than LastModified. If so, then return 304.
            // This header is ignored if any If-None-Match header is specified.
            /*long ifModifiedSince = request.getDateHeader("If-Modified-Since");
            if (ifNoneMatch == null && ifModifiedSince != -1 && ifModifiedSince + 1000 > lastModified) {
                response.setHeader("Last-Modified", lastModified); // Required in 304.
                response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                return;
            }*/


            // Validate request headers for resume ----------------------------------------------------

            // If-Match header should contain "*" or ETag. If not, then return 412.
            String ifMatch = request.getHeader("If-Match");
            if (ifMatch != null && !matches(ifMatch, resource.getETag())) {
                response.sendError(HttpServletResponse.SC_PRECONDITION_FAILED);
                return;
            }

            // If-Unmodified-Since header should be greater than LastModified. If not, then return 412.
            /*long ifUnmodifiedSince = request.getDateHeader("If-Unmodified-Since");
            if (ifUnmodifiedSince != -1 && ifUnmodifiedSince + 1000 <= lastModified) {
                response.sendError(HttpServletResponse.SC_PRECONDITION_FAILED);
                return;
            }*/

            // TODO ranges
            // TODO

            sendHeaders(response);
            try (InputStream source = resource.getContent(); OutputStream sink = response.getOutputStream()) {
                copy(source, sink);
            }
        }

        private static final int BUFFER_SIZE = 8192;

        private long copy(InputStream source, OutputStream sink) throws IOException {
            long nread = 0L;
            byte[] buf = new byte[BUFFER_SIZE];
            int n;
            while ((n = source.read(buf)) > 0) {
                sink.write(buf, 0, n);
                nread += n;
            }
            return nread;
        }

        private boolean matches(String ifNoneMatch, Optional<String> etag) {
            if (ifNoneMatch != null && etag.isPresent()) {
                String value = etag.get();
                return "*".equals(value) || ifNoneMatch.equals(value);
            }
            return false;
        }

        private void sendHeaders(HttpServletResponse response) {
            setHeader(response, "Content-Type", resource.getContentType());
            setLongHeader(response, "Content-Length", resource.getContentLength());
            setDateHeader(response, "Last-Modified", resource.getLastModifiedDate());
            setHeader(response, "Etag", resource.getETag());
        }

        private void setHeader(HttpServletResponse response, String name, Optional<String> value) {
            if (value.isPresent()) {
                response.setHeader(name, value.get());
            }
        }

        private void setDateHeader(HttpServletResponse response, String name, Optional<Instant> value) {
            if (value.isPresent()) {
                response.setDateHeader(name, toEpoch(value.get()));
            }
        }

        private void setLongHeader(HttpServletResponse response, String name, Optional<Long> value) {
            if (value.isPresent()) {
                response.setHeader(name, String.valueOf(value.get()));
            }
        }

        private long toEpoch(Instant instant) {
            return instant.truncatedTo(ChronoUnit.SECONDS).toEpochMilli();
        }
    }
}
