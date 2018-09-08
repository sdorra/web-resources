package com.github.sdorra;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junitpioneer.jupiter.TempDirectory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
        assertThat(resource.getETag()).contains(WebResources.etag(path));
        assertThat(resource.getLastModifiedDate()).contains(Files.getLastModifiedTime(jarPath).toInstant());
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
