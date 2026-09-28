package com.example.batchapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "batch")
public class BatchAppProperties {

    /** Dossier où sont écrits/lus les rapports (I/O disque réel). */
    private String reportsDir = "/app/reports";

    /** Nombre de lignes (fausses transactions) générées par rapport. */
    private int recordsPerBatch = 2000;

    /** Nombre de rapports conservés avant purge des plus anciens. */
    private int retentionCount = 5;

    /** Tous les N cycles, un rapport est délibérément corrompu (test ERROR). */
    private int corruptEveryCycles = 10;

    /** Seuil (%) d'espace disque libre en dessous duquel un WARN réel est émis. */
    private double lowSpaceWarnThresholdPercent = 20.0;

    /** Fichier de log JSON dédié (lu par Sentrix Agent, format: json). */
    private String jsonLogFile = "/var/log/batch-report-app/app.json";

    public String getReportsDir() { return reportsDir; }
    public void setReportsDir(String reportsDir) { this.reportsDir = reportsDir; }

    public int getRecordsPerBatch() { return recordsPerBatch; }
    public void setRecordsPerBatch(int recordsPerBatch) { this.recordsPerBatch = recordsPerBatch; }

    public int getRetentionCount() { return retentionCount; }
    public void setRetentionCount(int retentionCount) { this.retentionCount = retentionCount; }

    public int getCorruptEveryCycles() { return corruptEveryCycles; }
    public void setCorruptEveryCycles(int corruptEveryCycles) { this.corruptEveryCycles = corruptEveryCycles; }

    public double getLowSpaceWarnThresholdPercent() { return lowSpaceWarnThresholdPercent; }
    public void setLowSpaceWarnThresholdPercent(double lowSpaceWarnThresholdPercent) {
        this.lowSpaceWarnThresholdPercent = lowSpaceWarnThresholdPercent;
    }

    public String getJsonLogFile() { return jsonLogFile; }
    public void setJsonLogFile(String jsonLogFile) { this.jsonLogFile = jsonLogFile; }
}
