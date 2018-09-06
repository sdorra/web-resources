package com.github.sdorra;

import com.github.sdorra.spotter.ContentType;
import com.github.sdorra.spotter.ContentTypes;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

public final class WebResources {

    private WebResources(){}

    public static WebResource of(URL url)  throws IOException {
        URLConnection connection = url.openConnection();
        return builder(() -> url.openConnection().getInputStream())
                .withContentLength(connection.getContentLengthLong())
                .withLastModifiedDate(connection.getLastModified())
                .withContentType(ContentTypes.detect(url.getPath()))
                .build();
    }

    public static WebResource of(File file) throws IOException {
        return of(file.toPath());
    }

    public static WebResource of(Path path) throws IOException {
        return builder(() -> Files.newInputStream(path))
                .withContentLength(Files.size(path))
                .withLastModifiedDate(Files.getLastModifiedTime(path).toInstant())
                .withContentType(ContentTypes.detect(path.toString()))
                .build();
    }

    public static Builder builder(ContentProvider contentProvider) {
        return new Builder(contentProvider);
    }

    public static class Builder {

        private final WebResourceImpl resource;

        private Builder(ContentProvider contentProvider) {
            this.resource = new WebResourceImpl(contentProvider);
        }

        public Builder withContentLength(Long length) {
            resource.contentLength = length;
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
