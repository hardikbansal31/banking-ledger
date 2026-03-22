package com.bankingcore.bankingledger.service;

import com.bankingcore.bankingledger.domain.entity.ScheduledPayment;
import com.bankingcore.bankingledger.domain.enums.ScheduledPaymentStatus;
import com.bankingcore.bankingledger.domain.repository.ScheduledPaymentRepository;
import com.bankingcore.bankingledger.dto.request.TransactionRequest;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * TransferJob — Quartz Job implementation for scheduled payments.
 *
 * WHY NOT @RequiredArgsConstructor?
 *   Quartz instantiates Job objects itself — it doesn't use Spring's
 *   constructor injection. We must use @Autowired field injection here,
 *   or configure a SpringBeanJobFactory (which we do in QuartzConfig).
 *   SpringBeanJobFactory makes Quartz use Spring to create Job instances,
 *   enabling normal @Autowired injection.
 *
 * JOB DATA:
 *   Quartz stores job parameters in a JobDataMap (persisted to DB in
 *   JDBC mode, in-memory in dev). We store:
 *     scheduledPaymentId : UUID of the ScheduledPayment record
 *     sourceAccountNumber
 *     destinationAccountNumber
 *     amount
 *     currency
 *     description
 *     ownerUsername : needed by LedgerService ownership check
 */
@Slf4j
@Component
public class TransferJob implements Job {

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private DistributedLockService lockService;

    @Autowired
    private ScheduledPaymentRepository scheduledPaymentRepository;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobDataMap data = context.getMergedJobDataMap();

        String scheduledPaymentId  = data.getString("scheduledPaymentId");
        String sourceAccountNumber = data.getString("sourceAccountNumber");
        String destAccountNumber   = data.getString("destinationAccountNumber");
        String amountStr           = data.getString("amount");
        String currency            = data.getString("currency");
        String description         = data.getString("description");
        String ownerUsername       = data.getString("ownerUsername");

        log.info("TransferJob fired: scheduledPaymentId='{}' {} → {} amount={} {}",
                scheduledPaymentId, sourceAccountNumber, destAccountNumber,
                amountStr, currency);

        // Build a unique idempotency key per execution:
        // same job + same fire time = same key → prevents double execution
        // if Quartz fires the job twice (e.g. after a cluster failover)
        String idempotencyKey = "scheduled:" + scheduledPaymentId + ":"
                + context.getFireTime().toInstant().toEpochMilli();

        TransactionRequest.Transfer request = new TransactionRequest.Transfer();
        request.setSourceAccountNumber(sourceAccountNumber);
        request.setDestinationAccountNumber(destAccountNumber);
        request.setAmount(new BigDecimal(amountStr));
        request.setCurrency(currency);
        request.setDescription("[Scheduled] " + description);
        request.setIdempotencyKey(idempotencyKey);

        try {
            // Acquire distributed lock then execute — same path as a manual transfer
            String lockKey = DistributedLockService.transferLockKey(
                    sourceAccountNumber, destAccountNumber);

            lockService.executeWithLock(lockKey,
                    () -> ledgerService.transfer(request, ownerUsername));

            // Update the ScheduledPayment record
            scheduledPaymentRepository.findById(UUID.fromString(scheduledPaymentId))
                    .ifPresent(sp -> {
                        sp.setLastExecutedAt(Instant.now());
                        sp.setExecutionCount(sp.getExecutionCount() + 1);
                        scheduledPaymentRepository.save(sp);
                    });

            log.info("TransferJob completed successfully: scheduledPaymentId='{}'",
                    scheduledPaymentId);

        } catch (Exception ex) {
            // Mark the scheduled payment as FAILED
            scheduledPaymentRepository.findById(UUID.fromString(scheduledPaymentId))
                    .ifPresent(sp -> {
                        sp.setStatus(ScheduledPaymentStatus.FAILED);
                        scheduledPaymentRepository.save(sp);
                    });

            log.error("TransferJob FAILED: scheduledPaymentId='{}' reason='{}'",
                    scheduledPaymentId, ex.getMessage());

            // Wrap in JobExecutionException — tells Quartz whether to re-fire
            // refireImmediately = false — don't retry immediately on failure
            throw new JobExecutionException(ex, false);
        }
    }
}