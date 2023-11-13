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

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

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
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WebResourceSenderTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private WebResource resource;

    @BeforeEach
    void before() {
        when(resource.getName()).thenReturn("hello.txt");
    }

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
    void testHttpMethodHead() throws IOException {
        when(request.getMethod()).thenReturn("HEAD");

        WebResourceSender.create()
                .resource(resource)
                .send(request, response);

        verify(response, never()).getOutputStream();
    }

    @Test
    void testHead() throws IOException {
        when(request.getMethod()).thenReturn("HEAD");

        WebResourceSender.create()
                .resource(resource)
                .head(request, response);

        verify(response, never()).getOutputStream();
    }

    @Test
    void testCopyContent() throws IOException {
        when(resource.getContent()).thenReturn(inputStream("hello"));
        try (CapturingOutputStream output = new CapturingOutputStream()) {
            when(response.getOutputStream()).thenReturn(output);

            WebResourceSender.create()
                    .resource(resource)
                    .get(request, response);

            assertThat(output.getValue()).isEqualTo("hello");
        }
    }

    @Test
    void testCopyContentWithGZIPButWithoutAcceptHeader() throws IOException {
        when(resource.getContent()).thenReturn(inputStream("hello"));
        try (CapturingOutputStream output = new CapturingOutputStream()) {
            when(response.getOutputStream()).thenReturn(output);

            WebResourceSender.create()
                    .withGZIP()
                    .resource(resource)
                    .send(request, response);

            assertThat(output.getValue()).isEqualTo("hello");
        }
    }

    @Test
    void testCopyContentWithBufferSize() throws IOException {
        when(resource.getContent()).thenReturn(inputStream("hello"));
        try (CapturingOutputStream output = new CapturingOutputStream()) {
            when(response.getOutputStream()).thenReturn(output);

            WebResourceSender.create()
                    .withBufferSize(16)
                    .resource(resource)
                    .send(request, response);

            assertThat(output.getValue()).isEqualTo("hello");
        }
    }

    @Test
    void testWithInvalidBufferSize() {
        assertThrows(IllegalArgumentException.class, () -> WebResourceSender.create().withBufferSize(-1));
    }

    @Test
    void testDisabledGZipCompressionForSmallFiles() throws IOException {
        when(request.getHeader("Accept-Encoding")).thenReturn("gzip");
        when(resource.getContentLength()).thenReturn(Optional.of(5L));
        when(resource.getContent()).thenReturn(inputStream("hello"));

        try (CapturingOutputStream output = new CapturingOutputStream()) {
            when(response.getOutputStream()).thenReturn(output);

            WebResourceSender.create()
                    .withGZIP()
                    .withGZIPMinLength(10L)
                    .resource(resource)
                    .send(request, response);

            verify(response, never()).setHeader("Content-Encoding", "gzip");
            assertThat(output.getValue()).isEqualTo("hello");
        }
    }

    @Test
    void testGZipCompressionOnText() throws IOException {
        verifyCompressed("text/plain");
    }

    @Test
    void testGZipCompressionOnCSS() throws IOException {
        verifyCompressed("text/css");
    }

    @Test
    void testGZipCompressionOnJavaScript() throws IOException {
        verifyCompressed("application/javascript");
    }

    @Test
    void testGZipCompressionOnSvg() throws IOException {
        verifyCompressed("image/svg+xml");
    }

    private void verifyCompressed(String contentType) throws IOException {
        when(request.getHeader("Accept-Encoding")).thenReturn("gzip");
        when(resource.getContentType()).thenReturn(Optional.of(contentType));
        when(resource.getContent()).thenReturn(inputStream("hello"));
        when(resource.getContentLength()).thenReturn(Optional.of(42L));

        try (CapturingOutputStream output = new CapturingOutputStream()) {
            when(response.getOutputStream()).thenReturn(output);

            WebResourceSender.create()
                    .withGZIP()
                    .resource(resource)
                    .send(request, response);

            verify(response, never()).setHeader("Content-Length", "42");
            verify(response).setHeader("Content-Encoding", "gzip");
            String content = readGZIP(output.buffer.toByteArray());
            assertThat(content).isEqualTo("hello");
        }
    }

    @Test
    void testContentWithEnabledGZIPonImage() throws IOException {
       verifyNonCompressed("image/png");
    }

    @Test
    void testContentWithEnabledGZIPonVideo() throws IOException {
        verifyNonCompressed("video/avi");
    }

    private void verifyNonCompressed(String contentType) throws IOException {
        when(request.getHeader("Accept-Encoding")).thenReturn("gzip");
        when(resource.getContentType()).thenReturn(Optional.of(contentType));
        when(resource.getContent()).thenReturn(inputStream("hello"));

        try (CapturingOutputStream output = new CapturingOutputStream()) {
            when(response.getOutputStream()).thenReturn(output);

            WebResourceSender.create()
                    .withGZIP()
                    .resource(resource)
                    .send(request, response);

            verify(response, never()).setHeader("Content-Encoding", "gzip");
            assertThat(output.getValue()).isEqualTo("hello");
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
        void testContentTypeForResourceWithoutContentType() throws IOException {
            WebResourceSender.create()
                    .resource(resource)
                    .head(request, response);

            verify(response).setHeader("Content-Type", "text/plain");
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
            when(resource.getContentType()).thenReturn(Optional.of("image/jpeg"));

            WebResourceSender.create()
                    .resource(resource)
                    .send(request, response);

            verify(response).setHeader("Content-Type", "image/jpeg");
        }

        @Test
        void testContentLengthHeader() throws IOException {
            when(resource.getContentLength()).thenReturn(Optional.of(42L));

            WebResourceSender.create()
                    .resource(resource)
                    .send(request, response);

            verify(response).setHeader("Content-Length", "42");
        }

        @Test
        void testContentDispositionHeader() throws IOException {
            WebResourceSender.create()
                    .resource(resource)
                    .send(request, response);

            verify(response).setHeader("Content-Disposition", "inline;filename=\"hello.txt\"");
        }

        @Test
        void testContentDispositionHeaderForImage() throws IOException {
            when(resource.getName()).thenReturn("hello.png");
            when(resource.getContentType()).thenReturn(Optional.of("image/png"));

            WebResourceSender.create()
                    .resource(resource)
                    .send(request, response);

            verify(response).setHeader("Content-Disposition", "attachment;filename=\"hello.png\"");
        }

        @Test
        void testContentDispositionHeaderForImageWhichIsAcceptedByTheBrowser() throws IOException {
            when(resource.getName()).thenReturn("hello.png");
            when(resource.getContentType()).thenReturn(Optional.of("image/png"));

            when(request.getHeader("Accept")).thenReturn("image/png");

            WebResourceSender.create()
                    .resource(resource)
                    .send(request, response);

            verify(response).setHeader("Content-Disposition", "inline;filename=\"hello.png\"");
        }

        @Test
        void testCacheControl() throws IOException {
            WebResourceSender.create()
                    .withCacheControl(CacheControl.create().noCache().noStore().mustRevalidate())
                    .resource(resource)
                    .send(request, response);

            assertHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        }

        @Test
        void testCacheControlWithoutCacheControl() throws IOException {
            WebResourceSender.create()
                    .resource(resource)
                    .send(request, response);

            assertHeaderIsAbsent("Cache-Control");
        }

        @Test
        void testCacheControlWithEmptyCacheControl() throws IOException {
            WebResourceSender.create()
                    .withCacheControl(CacheControl.create())
                    .resource(resource)
                    .send(request, response);

            assertHeaderIsAbsent("Cache-Control");
        }

        @Test
        void testCacheControlWithNullCacheControl() {
            assertThrows(IllegalArgumentException.class, () -> WebResourceSender.create().withCacheControl(null));
        }

        private void assertHeader(String name, String expected) {
            ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);

            verify(response, atLeastOnce()).setHeader(nameCaptor.capture(), valueCaptor.capture());

            int index = nameCaptor.getAllValues().indexOf(name);
            assertThat(index).isGreaterThan(0);

            String value = valueCaptor.getAllValues().get(index);
            assertThat(value).isEqualTo(expected);
        }

        private void assertHeaderIsAbsent(String name) {
            ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
            verify(response, atLeastOnce()).setHeader(nameCaptor.capture(), anyString());

            int index = nameCaptor.getAllValues().indexOf(name);
            assertThat(index).isEqualTo(-1);
        }

        @Test
        void testExpires() throws IOException {
            WebResourceSender.create()
                    .withExpires(1, TimeUnit.HOURS)
                    .resource(resource)
                    .send(request, response);

            ArgumentCaptor<Long> captor = ArgumentCaptor.forClass(Long.class);
            verify(response).setDateHeader(anyString(), captor.capture());

            long time = captor.getValue();

            long min = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(59l);
            long max = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(61l);
            assertThat(time).isBetween(min, max);
        }

        @Test
        void testExpiresWithInvalidCount() {
            assertThrows(IllegalArgumentException.class, () -> WebResourceSender.create()
                    .withExpires(-1, TimeUnit.HOURS));
        }

        @Test
        void testExpiresWithoutUnit() {
            assertThrows(IllegalArgumentException.class, () -> WebResourceSender.create()
                    .withExpires(3, null));
        }

    }

    @Test
    void testWithPath(@TempDir Path tempDir) throws IOException {
        Path path = createSample(tempDir);
        verifyPath(WebResourceSender.create().resource(path), path);
    }

    @Test
    void testWithFile(@TempDir Path tempDir) throws IOException {
        Path path = createSample(tempDir);
        verifyPath(WebResourceSender.create().resource(path.toFile()), path);
    }

    @Test
    void testWithURL(@TempDir Path tempDir) throws IOException {
        Path path = createSample(tempDir);
        verifyPath(WebResourceSender.create().resource(path.toUri().toURL()), path);
    }

    private Path createSample(@TempDir Path tempDir) throws IOException {
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

        String getValue() {
            return buffer.toString();
        }

        @Override
        public boolean isReady() {
            return false;
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {

        }
    }

}
