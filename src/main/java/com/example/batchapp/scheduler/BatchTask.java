package com.example.batchapp.scheduler;

import com.example.batchapp.service.ReportGeneratorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class BatchTask {

    private static final Logger log = LoggerFactory.getLogger(BatchTask.class);

    private final ReportGeneratorService reportGeneratorService;
    private final AtomicLong cycleCounter = new AtomicLong(0);

    public BatchTask(ReportGeneratorService reportGeneratorService) {
        this.reportGeneratorService = reportGeneratorService;
    }

    @Scheduled(fixedRate = 120_000, initialDelay = 5_000)
    public void runCycle() {
        long cycle = cycleCounter.incrementAndGet();
        log.info("=== Debut du cycle de reporting #{} ===", cycle);
        try {
            reportGeneratorService.runCycle(cycle);
        } catch (Exception e) {
            log.error("Erreur inattendue durant le cycle #{}", cycle, e);
        }
        log.info("=== Fin du cycle de reporting #{} ===", cycle);
    }
}
