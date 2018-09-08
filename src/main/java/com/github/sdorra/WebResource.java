package com.github.sdorra;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Optional;

public interface WebResource {

    String getName();

    InputStream getContent() throws IOException;

    Optional<Long> getContentLength();

    Optional<String> getContentType();

    Optional<String> getETag();

    Optional<Instant> getLastModifiedDate();

}
