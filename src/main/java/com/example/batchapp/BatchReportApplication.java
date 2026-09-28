package com.example.batchapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Application "cobaye" n°2 pour Sentrix Agent : simule un job de génération
 * de rapports (écriture/lecture/purge de fichiers réels sur disque) et
 * journalise ses avertissements/erreurs au format JSON, afin de tester des
 * aspects de l'agent que loadtest-app ne couvre pas (I/O disque, parsing JSON).
 */
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class BatchReportApplication {

    public static void main(String[] args) {
        SpringApplication.run(BatchReportApplication.class, args);
    }
}
