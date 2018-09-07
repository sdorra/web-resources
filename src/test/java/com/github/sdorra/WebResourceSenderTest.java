package com.github.sdorra;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junitpioneer.jupiter.TempDirectory;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@ExtendWith({MockitoExtension.class, TempDirectory.class})
@MockitoSettings(strictness = Strictness.LENIENT)
class WebResourceSenderTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private WebResource resource;

    @Test
    void testMatchIfModifiedSince() throws IOException {
        Instant instant = Instant.now();
        long epochMilli = instant.truncatedTo(ChronoUnit.SECONDS).toEpochMilli();

        when(request.getDateHeader("If-Modified-Since")).thenReturn(epochMilli);
        when(resource.getLastModifiedDate()).thenReturn(Optional.of(instant));

        WebResourceSender.create()
                .resource(resource)
                .send(request, response);

        verify(response).setDateHeader("Last-Modified", epochMilli);
        verify(response).setStatus(HttpServletResponse.SC_NOT_MODIFIED);
    }

    @Test
    void testMatchIfModifiedSinceWithNoneIfMatchHeader() throws IOException {
        Instant instant = Instant.now();
        long epochMilli = instant.truncatedTo(ChronoUnit.SECONDS).toEpochMilli();

        setEmptyStreams();

        when(request.getHeader("If-None-Match")).thenReturn("abc");
        when(request.getDateHeader("If-Modified-Since")).thenReturn(epochMilli);
        when(resource.getLastModifiedDate()).thenReturn(Optional.of(instant));

        WebResourceSender.create()
                .resource(resource)
                .send(request, response);

        verify(response).setStatus(HttpServletResponse.SC_OK);
    }

    @Test
    void testNonMatchingIfUnmodifiedSince() throws IOException {
        Instant instant = Instant.now();
        long epochMilli = instant.minus(1, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS).toEpochMilli();

        when(request.getDateHeader("If-Unmodified-Since")).thenReturn(epochMilli);
        when(resource.getLastModifiedDate()).thenReturn(Optional.of(instant));

        WebResourceSender.create()
                .resource(resource)
                .send(request, response);

        verify(response).sendError(HttpServletResponse.SC_PRECONDITION_FAILED);
    }

    @Test
    void testMatchtingIfNoneMatch() throws IOException {
        when(request.getHeader("If-None-Match")).thenReturn("abc");
        when(resource.getETag()).thenReturn(Optional.of("abc"));

        WebResourceSender.create()
                .resource(resource)
                .send(request, response);

        verify(response).setHeader("ETag", "abc");
        verify(response).setStatus(HttpServletResponse.SC_NOT_MODIFIED);
    }

    @Test
    void testNonMatchingIfMatch() throws IOException {
        when(request.getHeader("If-Match")).thenReturn("abc");
        when(resource.getETag()).thenReturn(Optional.of("def"));

        WebResourceSender.create()
                .resource(resource)
                .send(request, response);

        verify(response).sendError(HttpServletResponse.SC_PRECONDITION_FAILED);
    }

    @Test
    void testSuccessfulStatusCode() throws IOException {
        setEmptyStreams();

        WebResourceSender.create()
                .resource(resource)
                .send(request, response);

        verify(response).setStatus(HttpServletResponse.SC_OK);
    }

    @Test
    void testCopyContent() throws IOException {
        when(resource.getContent()).thenReturn(inputStream("hello"));
        try (CapturingOutputStream output = new CapturingOutputStream()) {
            when(response.getOutputStream()).thenReturn(output);

            WebResourceSender.create()
                    .resource(resource)
                    .send(request, response);

            assertThat(output.getValue()).isEqualTo("hello");
        }
    }

    @Test
    void testGZipCompression() throws IOException {
        when(resource.getContent()).thenReturn(inputStream("hello"));

        try (CapturingOutputStream output = new CapturingOutputStream()) {
            when(response.getOutputStream()).thenReturn(output);

            WebResourceSender.create()
                    .withGZIP()
                    .resource(resource)
                    .send(request, response);

            verify(response).setHeader("Content-Encoding", "gzip");
            String content = readGZIP(output.buffer.toByteArray());
            assertThat(content).isEqualTo("hello");
        }
    }

    private String readGZIP(byte[] data) throws IOException {
        try (InputStream input = new GZIPInputStream(new ByteArrayInputStream(data))) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            Streams.copy(input, baos);

            return baos.toString("UTF-8");
        }
    }

    @Nested
    class HeaderTests {

        @BeforeEach
        void setUpStreamMocks() throws IOException {
            setEmptyStreams();
        }

        @Test
        void testETagHeader() throws IOException {
            when(resource.getETag()).thenReturn(Optional.of("abc"));

            WebResourceSender.create()
                    .resource(resource)
                    .send(request, response);

            verify(response).setHeader("ETag", "abc");
        }

        @Test
        void testLastModifiedHeader() throws IOException {
            Instant instant = Instant.now();
            when(resource.getLastModifiedDate()).thenReturn(Optional.of(instant));

            WebResourceSender.create()
                    .resource(resource)
                    .send(request, response);

            verify(response).setDateHeader("Last-Modified", instant.truncatedTo(ChronoUnit.SECONDS).toEpochMilli());
        }

        @Test
        void testContentTypeHeader() throws IOException {
            when(resource.getContentType()).thenReturn(Optional.of("text/plain"));

            WebResourceSender.create()
                    .resource(resource)
                    .send(request, response);

            verify(response).setHeader("Content-Type", "text/plain");
        }

        @Test
        void testContentLengthHeader() throws IOException {
            when(resource.getContentLength()).thenReturn(Optional.of(42L));

            WebResourceSender.create()
                    .resource(resource)
                    .send(request, response);

            verify(response).setHeader("Content-Length", "42");
        }

    }

    @Test
    void testWithPath(@TempDirectory.TempDir Path tempDir) throws IOException {
        Path path = createSample(tempDir);
        verifyPath(WebResourceSender.create().resource(path), path);
    }

    @Test
    void testWithFile(@TempDirectory.TempDir Path tempDir) throws IOException {
        Path path = createSample(tempDir);
        verifyPath(WebResourceSender.create().resource(path.toFile()), path);
    }

    @Test
    void testWithURL(@TempDirectory.TempDir Path tempDir) throws IOException {
        Path path = createSample(tempDir);
        verifyPath(WebResourceSender.create().resource(path.toUri().toURL()), path);
    }

    private Path createSample(@TempDirectory.TempDir Path tempDir) throws IOException {
        Path path = tempDir.resolve("test.txt");
        Files.write(path, "hello".getBytes(StandardCharsets.UTF_8));
        return path;
    }

    private void verifyPath(WebResourceSender.Sender sender, Path path) throws IOException {
        try (CapturingOutputStream outputStream = new CapturingOutputStream()) {
            when(response.getOutputStream()).thenReturn(outputStream);

            sender.send(request, response);

            assertThat(outputStream.getValue()).isEqualTo("hello");
            FileTime lastModifiedTime = Files.getLastModifiedTime(path);
            long lastModified = lastModifiedTime.toInstant().truncatedTo(ChronoUnit.SECONDS).toEpochMilli();
            verify(response).setDateHeader("Last-Modified", lastModified);
            verify(response).setHeader("Content-Type", "text/plain");
        }
    }

    private void setEmptyStreams() throws IOException {
        when(resource.getContent()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(response.getOutputStream()).thenReturn(new CapturingOutputStream());
    }

    private InputStream inputStream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    private static class CapturingOutputStream extends ServletOutputStream {

        private ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        @Override
        public void write(int b) {
            buffer.write(b);
        }

        public String getValue() {
            return buffer.toString();
        }

    }

}
