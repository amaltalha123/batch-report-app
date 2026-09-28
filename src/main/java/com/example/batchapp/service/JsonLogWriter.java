package com.example.batchapp.service;

import com.example.batchapp.config.BatchAppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Écrit des lignes de log au format JSON (une ligne = un objet JSON), dans le
 * fichier configuré par {@code batch.json-log-file}.
 *
 * Sentrix Agent (format: json) filtre volontairement les niveaux INFO/DEBUG/
 * TRACE : seuls WARN, ERROR et FATAL sont donc écrits ici. Les logs INFO
 * restent uniquement dans la console (via SLF4J classique), pour le confort
 * du développeur en local.
 */
@Service
public class JsonLogWriter {

    private static final Logger console = LoggerFactory.getLogger("JsonLogWriter.console");

    private final BatchAppProperties properties;
    private final Object lock = new Object();
    private boolean directoryReady = false;

    public JsonLogWriter(BatchAppProperties properties) {
        this.properties = properties;
    }

    public void info(String message, Map<String, Object> fields) {
        write("INFO", message, fields);
    }

    public void warn(String message, Map<String, Object> fields) {
        write("WARN", message, fields);
    }

    public void error(String message, Map<String, Object> fields) {
        write("ERROR", message, fields);
    }

    private void write(String level, String message, Map<String, Object> fields) {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("timestamp", Instant.now().toString());
        line.put("level", level);
        line.put("message", message);
        if (fields != null) {
            line.putAll(fields);
        }

        String json = toJson(line);
        console.info("[{}] {}", level, message); // visible aussi dans `docker compose logs`

        synchronized (lock) {
            try {
                ensureDirectory();
                Path path = Paths.get(properties.getJsonLogFile());
                Files.writeString(path, json + System.lineSeparator(),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                console.error("Impossible d'ecrire le log JSON dans {} : {}",
                        properties.getJsonLogFile(), e.getMessage());
            }
        }
    }

    private void ensureDirectory() throws IOException {
        if (directoryReady) {
            return;
        }
        Path parent = Paths.get(properties.getJsonLogFile()).getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        directoryReady = true;
    }

    /**
     * Sérialisation JSON minimaliste (pas de dépendance Jackson nécessaire) :
     * suffisante pour des valeurs simples (String, Number, Boolean).
     */
    private String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(escape(entry.getKey())).append("\":");
            sb.append(toJsonValue(entry.getValue()));
        }
        return sb.append('}').toString();
    }

    private String toJsonValue(Object value) {
        if (value == null) return "null";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        return '"' + escape(value.toString()) + '"';
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }
}
