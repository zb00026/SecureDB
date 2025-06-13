package com.verlake.dam.service.metrics;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for tracking and recording notification processing metrics.
 */
@Service
@Slf4j
public class NotificationMetricsService {
    private final AtomicLong totalProcessingTime = new AtomicLong(0);
    private final AtomicLong totalBatchProcessingTime = new AtomicLong(0);
    private final AtomicInteger pushNotificationSuccess = new AtomicInteger(0);
    private final AtomicInteger pushNotificationFailures = new AtomicInteger(0);
    private final AtomicInteger emailSuccess = new AtomicInteger(0);
    private final AtomicInteger emailFailures = new AtomicInteger(0);
    private final AtomicInteger batchFailures = new AtomicInteger(0);
    private final AtomicInteger jobFailures = new AtomicInteger(0);
    private final AtomicInteger processingFailures = new AtomicInteger(0);
    private final AtomicInteger totalBatches = new AtomicInteger(0);
    private final AtomicInteger totalJobs = new AtomicInteger(0);

    /**
     * Records the processing time for a single notification task.
     */
    public void recordProcessingTime(Duration duration) {
        totalProcessingTime.addAndGet(duration.toMillis());
    }

    /**
     * Records the processing time for a batch of notifications.
     */
    public void recordBatchProcessingTime(Duration duration) {
        totalBatchProcessingTime.addAndGet(duration.toMillis());
        totalBatches.incrementAndGet();
    }

    /**
     * Records the size of a processed batch.
     */
    public void recordBatchSize(int size) {
        // Can be used for calculating average batch size
    }

    /**
     * Increments the count of successful push notifications.
     */
    public void incrementPushNotificationSuccess() {
        pushNotificationSuccess.incrementAndGet();
    }

    /**
     * Increments the count of failed push notifications.
     */
    public void incrementPushNotificationFailures() {
        pushNotificationFailures.incrementAndGet();
    }

    /**
     * Increments the count of successful emails.
     */
    public void incrementEmailSuccess() {
        emailSuccess.incrementAndGet();
    }

    /**
     * Increments the count of failed emails.
     */
    public void incrementEmailFailures() {
        emailFailures.incrementAndGet();
    }

    /**
     * Increments the count of failed batches.
     */
    public void incrementBatchFailures() {
        batchFailures.incrementAndGet();
    }

    /**
     * Increments the count of failed jobs.
     */
    public void incrementJobFailures() {
        jobFailures.incrementAndGet();
    }

    /**
     * Increments the count of processing failures.
     */
    public void incrementProcessingFailures() {
        processingFailures.incrementAndGet();
    }

    /**
     * Records the completion of a job.
     */
    public void recordJobCompletion() {
        totalJobs.incrementAndGet();
    }

    /**
     * Gets the current metrics snapshot.
     */
    public MetricsSnapshot getMetricsSnapshot() {
        return new MetricsSnapshot(
            totalProcessingTime.get(),
            totalBatchProcessingTime.get(),
            pushNotificationSuccess.get(),
            pushNotificationFailures.get(),
            emailSuccess.get(),
            emailFailures.get(),
            batchFailures.get(),
            jobFailures.get(),
            processingFailures.get(),
            totalBatches.get(),
            totalJobs.get()
        );
    }

    /**
     * Data class for holding metrics snapshot.
     */
    public record MetricsSnapshot(
        long totalProcessingTime,
        long totalBatchProcessingTime,
        int pushNotificationSuccess,
        int pushNotificationFailures,
        int emailSuccess,
        int emailFailures,
        int batchFailures,
        int jobFailures,
        int processingFailures,
        int totalBatches,
        int totalJobs
    ) {}
} 