package com.example.batchapp.service;

import com.example.batchapp.config.BatchAppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Simule un job de reporting : écrit un rapport CSV (I/O disque réel), relit
 * un rapport plus ancien pour en calculer un total, et purge les rapports
 * excédentaires. Un rapport sur N est délibérément corrompu pour tester la
 * remontée d'erreurs.
 */
@Service
public class ReportGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(ReportGeneratorService.class);

    private final BatchAppProperties properties;
    private final JsonLogWriter jsonLog;

    public ReportGeneratorService(BatchAppProperties properties, JsonLogWriter jsonLog) {
        this.properties = properties;
        this.jsonLog = jsonLog;
    }

    public void runCycle(long cycle) {
        try {
            Files.createDirectories(Paths.get(properties.getReportsDir()));
        } catch (IOException e) {
            log.error("Impossible de creer le dossier de rapports {} : {}",
                    properties.getReportsDir(), e.getMessage());
            return;
        }

        Path written = writeReport(cycle);
        checkDiskSpace();
        readAndAggregatePreviousReport(cycle);
        purgeOldReports();

        log.info("Cycle #{} termine (rapport ecrit: {})", cycle, written.getFileName());
        jsonLog.info("Cycle de reporting termine",
                Map.of("cycle", cycle, "reportFile", written.getFileName().toString()));
    }

    // ── Écriture ────────────────────────────────────────────────────────────

    private Path writeReport(long cycle) {
        Path file = Paths.get(properties.getReportsDir(), "report-" + cycle + ".csv");
        boolean corruptThisOne = properties.getCorruptEveryCycles() > 0
                && cycle % properties.getCorruptEveryCycles() == 0;

        long start = System.nanoTime();
        try (var writer = Files.newBufferedWriter(file, StandardCharsets_UTF8())) {
            writer.write("id,amount,label");
            writer.newLine();

            int n = properties.getRecordsPerBatch();
            for (int i = 0; i < n; i++) {
                double amount = ThreadLocalRandom.current().nextDouble(1, 500);
                writer.write(i + "," + String.format(Locale.US, "%.2f", amount) + ",txn-" + i);
                writer.newLine();
            }

            if (corruptThisOne) {
                // Ligne volontairement invalide, pour tester la gestion d'erreur
                // a la lecture. Ce n'est PAS un vrai bug applicatif.
                writer.write("CORRUPTED_LINE_not_a_number,???,broken");
                writer.newLine();
            }
        } catch (IOException e) {
            log.error("Echec d'ecriture du rapport {} : {}", file, e.getMessage());
        }
        long durationMs = (System.nanoTime() - start) / 1_000_000;

        log.info("Rapport ecrit : {} ({} lignes, {} ms){}",
                file.getFileName(), properties.getRecordsPerBatch(), durationMs,
                corruptThisOne ? " [corruption simulee ajoutee]" : "");
        jsonLog.info("Rapport ecrit",
                Map.of(
                        "reportFile", file.getFileName().toString(),
                        "records", properties.getRecordsPerBatch(),
                        "durationMs", durationMs,
                        "corruptionSimulee", corruptThisOne
                ));

        return file;
    }

    // ── Lecture + agrégation ───────────────────────────────────────────────

    private void readAndAggregatePreviousReport(long cycle) {
        long previousCycle = cycle - 1;
        if (previousCycle < 1) {
            return; // rien à relire au tout premier cycle
        }
        Path file = Paths.get(properties.getReportsDir(), "report-" + previousCycle + ".csv");
        if (!Files.exists(file)) {
            return;
        }

        long start = System.nanoTime();
        double total = 0;
        int lines = 0;
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets_UTF8())) {
            String line = reader.readLine(); // header
            while ((line = reader.readLine()) != null) {
                lines++;
                String[] parts = line.split(",");
                // parts[1] = amount -> NumberFormatException si ligne corrompue
                total += Double.parseDouble(parts[1]);
            }
        } catch (NumberFormatException e) {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            log.error("Echec de lecture du rapport {} : ligne corrompue detectee apres {} ms",
                    file.getFileName(), durationMs, e);
            jsonLog.error("Rapport illisible : ligne corrompue (donnee non numerique)",
                    Map.of(
                            "reportFile", file.getFileName().toString(),
                            "linesReadBeforeFailure", lines,
                            "exception", e.getClass().getSimpleName() + ": " + e.getMessage()
                    ));
            return;
        } catch (IOException e) {
            log.error("Echec de lecture du rapport {} : {}", file, e.getMessage());
            return;
        }
        long durationMs = (System.nanoTime() - start) / 1_000_000;

        log.info("Rapport relu : {} ({} lignes, total={}, {} ms)",
                file.getFileName(), lines, String.format(Locale.US, "%.2f", total), durationMs);
        jsonLog.info("Rapport relu",
                Map.of(
                        "reportFile", file.getFileName().toString(),
                        "lines", lines,
                        "total", Math.round(total * 100) / 100.0,
                        "durationMs", durationMs
                ));
    }

    // ── Purge / rétention ──────────────────────────────────────────────────

    private void purgeOldReports() {
        try (var files = Files.list(Paths.get(properties.getReportsDir()))) {
            List<Path> reports = files
                    .filter(p -> p.getFileName().toString().startsWith("report-"))
                    .sorted(Comparator.comparing(this::creationTimeOrZero))
                    .toList();

            int excess = reports.size() - properties.getRetentionCount();
            if (excess <= 0) {
                return;
            }
            for (int i = 0; i < excess; i++) {
                Files.deleteIfExists(reports.get(i));
            }
            log.info("Retention : {} ancien(s) rapport(s) supprime(s) ({} conserve(s))",
                    excess, properties.getRetentionCount());
            jsonLog.info("Purge de retention",
                    Map.of(
                            "supprimes", excess,
                            "conserves", properties.getRetentionCount()
                    ));
        } catch (IOException e) {
            log.error("Echec de la purge des rapports : {}", e.getMessage());
        }
    }

    private FileTime creationTimeOrZero(Path p) {
        try {
            return Files.getLastModifiedTime(p);
        } catch (IOException e) {
            return FileTime.fromMillis(0);
        }
    }

    // ── Espace disque ────────────────────────────────────────────────────────

    private void checkDiskSpace() {
        try {
            FileStore store = Files.getFileStore(Paths.get(properties.getReportsDir()));
            long total = store.getTotalSpace();
            long usable = store.getUsableSpace();
            if (total <= 0) return;

            double freePercent = (double) usable / total * 100;
            if (freePercent < properties.getLowSpaceWarnThresholdPercent()) {
                log.warn("Espace disque faible sur {} : {}% libre (seuil: {}%)",
                        properties.getReportsDir(),
                        String.format(Locale.US, "%.1f", freePercent),
                        properties.getLowSpaceWarnThresholdPercent());
                jsonLog.warn("Espace disque faible",
                        Map.of(
                                "path", properties.getReportsDir(),
                                "freePercent", Math.round(freePercent * 10) / 10.0,
                                "thresholdPercent", properties.getLowSpaceWarnThresholdPercent()
                        ));
            }
        } catch (IOException e) {
            log.error("Impossible de lire l'espace disque pour {} : {}",
                    properties.getReportsDir(), e.getMessage());
        }
    }

    private static java.nio.charset.Charset StandardCharsets_UTF8() {
        return java.nio.charset.StandardCharsets.UTF_8;
    }
}
