package com.github.sdorra;

import com.github.sdorra.spotter.ContentType;
import com.github.sdorra.spotter.ContentTypes;

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

public final class WebResources {

    private WebResources(){}

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


        return builder(() -> url.openConnection().getInputStream())
                .withContentLength(size)
                .withLastModifiedDate(lastModified)
                .withContentType(ContentTypes.detect(url.getPath()))
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

    public static WebResource of(File file) throws IOException {
        return of(file.toPath());
    }

    public static WebResource of(Path path) throws IOException {
        return builder(() -> Files.newInputStream(path))
                .withContentLength(Files.size(path))
                .withLastModifiedDate(Files.getLastModifiedTime(path).toInstant())
                .withContentType(ContentTypes.detect(path.toString()))
                .withETag(etag(path))
                .build();
    }

    public static Builder builder(ContentProvider contentProvider) {
        return new Builder(contentProvider);
    }

    static String etag(Path path) throws IOException {
        String name = path.getFileName().toString();
        long size = Files.size(path);
        long lastModified = Files.getLastModifiedTime(path).toMillis();
        return etag(name, size, lastModified);
    }

    private static String etag(String name, long size, long lastModifid) {
        return String.format("%s_%s_%s", name, size, lastModifid);
    }

    public static class Builder {

        private final WebResourceImpl resource;

        private Builder(ContentProvider contentProvider) {
            this.resource = new WebResourceImpl(contentProvider);
        }

        public Builder withContentLength(Long length) {
            if (length != null && length >= 0) {
                resource.contentLength = length;
            }
            return this;
        }

        public Builder withContentType(ContentType contentType) {
            resource.contentType = contentType.getRaw();
            return this;
        }

        public Builder withContentType(String contentType) {
            resource.contentType = contentType;
            return this;
        }

        public Builder withETag(String etag) {
            resource.etag = etag;
            return this;
        }

        public Builder withLastModifiedDate(long lastModifiedDate) {
            if (lastModifiedDate != -1) {
                resource.lastModifiedDate = Instant.ofEpochMilli(lastModifiedDate);
            }
            return this;
        }

        public Builder withLastModifiedDate(Instant lastModifiedDate) {
            resource.lastModifiedDate = lastModifiedDate;
            return this;
        }

        public WebResource build() {
            return resource;
        }

    }

    private static class WebResourceImpl implements WebResource {

        private final ContentProvider provider;
        private Long contentLength;
        private String contentType;
        private String etag;
        private Instant lastModifiedDate;

        private WebResourceImpl(ContentProvider provider) {
            this.provider = provider;
        }

        @Override
        public InputStream getContent() throws IOException {
            return provider.get();
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
