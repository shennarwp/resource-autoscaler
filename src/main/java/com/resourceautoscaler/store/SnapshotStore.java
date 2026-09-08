package com.resourceautoscaler.store;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.resourceautoscaler.model.MetricsSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Persists {@link MetricsSnapshot} files as readable JSON on the local filesystem.
 * Stands alone so it can be used by both the exporter service and the mock repository
 * without introducing a dependency cycle.
 */
@Component
public class SnapshotStore {

    private final ObjectMapper objectMapper;
    private final Path snapshotDir;

    public SnapshotStore(@Value("${app.snapshot.dir:data/metrics}") String snapshotDir) {
        this.snapshotDir = Path.of(snapshotDir);
        this.objectMapper = new ObjectMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(SerializationFeature.INDENT_OUTPUT)
                .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public Path snapshotFile(String resourceId) {
        return snapshotDir.resolve(resourceId + ".json");
    }

    public Path write(MetricsSnapshot snapshot) throws IOException {
        Path file = snapshotFile(snapshot.resourceId());
        Files.createDirectories(snapshotDir);
        byte[] json = objectMapper.writeValueAsBytes(snapshot);
        Files.write(file, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return file;
    }

    public MetricsSnapshot read(String resourceId) {
        Path file = snapshotFile(resourceId);
        if (!Files.exists(file)) {
            return null;
        }
        try {
            return objectMapper.readValue(file.toFile(), MetricsSnapshot.class);
        } catch (IOException e) {
            return null;
        }
    }
}