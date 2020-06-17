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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junitpioneer.jupiter.TempDirectory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(TempDirectory.class)
class WebResourcesTest {

    @Test
    void testOfPath(@TempDirectory.TempDir Path tempDir) throws IOException {
        Path path = createSamplePath(tempDir);

        WebResource resource = WebResources.of(path);
        verifyPath(resource, path);
    }

    @Test
    void testOfFile(@TempDirectory.TempDir Path tempDir) throws IOException {
        Path path = createSamplePath(tempDir);

        WebResource resource = WebResources.of(path.toFile());
        verifyPath(resource, path);
    }

    @Test
    void testOfFileURL(@TempDirectory.TempDir Path tempDir) throws IOException {
        Path path = createSamplePath(tempDir);

        WebResource resource = WebResources.of(path.toUri().toURL());
        verifyPath(resource, path);
    }

    @Test
    void testOfJarURL(@TempDirectory.TempDir Path tempDir) throws IOException {
        Path path = createSamplePath(tempDir);
        Path jarPath = tempDir.resolve("test.jar");

        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jarPath))) {
            JarEntry entry = new JarEntry("test.txt");
            output.putNextEntry(entry);
            try (InputStream input = Files.newInputStream(path)) {
                Streams.copy(input, output);
            }
            output.closeEntry();
        }

        URL url = new URL(String.format("jar:file:%s!/%s", jarPath.toString(), "test.txt"));
        WebResource resource = WebResources.of(url);

        assertThat(resource.getContentType()).contains("text/plain");
        assertThat(resource.getContentLength()).contains(Files.size(path));
        assertThat(Streams.toString(resource.getContent())).isEqualTo("awesome");
        assertThat(resource.getETag().isPresent()).isTrue();
        long lastModified = url.openConnection().getLastModified();
        assertThat(resource.getLastModifiedDate()).contains(Instant.ofEpochMilli(lastModified));
    }

    @Test
    void shouldCreateWeakEtag(@TempDirectory.TempDir Path tempDir) throws IOException {
        Path path = createSamplePath(tempDir);

        WebResource resource = WebResources.of(path);

        assertThat(resource.getETag().isPresent()).isTrue();
        String etag = resource.getETag().get();
        assertThat(etag).startsWith("W/\"");
        assertThat(etag).endsWith("\"");
    }


    private void verifyPath(WebResource resource, Path path) throws IOException {
        assertThat(resource.getName()).isEqualTo("test.txt");
        assertThat(resource.getContentType()).contains("text/plain");
        assertThat(resource.getLastModifiedDate()).contains(Files.getLastModifiedTime(path).toInstant());
        assertThat(resource.getContentLength()).contains(Files.size(path));
        assertThat(Streams.toString(resource.getContent())).isEqualTo("awesome");
        assertThat(resource.getETag()).contains(WebResources.etag(path));
    }

    private Path createSamplePath(@TempDirectory.TempDir Path tempDir) throws IOException {
        Path path = tempDir.resolve("test.txt");
        Files.write(path, "awesome".getBytes(StandardCharsets.UTF_8));
        return path;
    }

}
