package com.bankingcore.bankingledger.service;

import com.bankingcore.bankingledger.domain.entity.Account;
import com.bankingcore.bankingledger.domain.entity.ScheduledPayment;
import com.bankingcore.bankingledger.domain.enums.ScheduledPaymentStatus;
import com.bankingcore.bankingledger.domain.repository.AccountRepository;
import com.bankingcore.bankingledger.domain.repository.ScheduledPaymentRepository;
import com.bankingcore.bankingledger.dto.request.ScheduledPaymentRequest;
import com.bankingcore.bankingledger.dto.response.ScheduledPaymentResponse;
import com.bankingcore.bankingledger.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * ScheduledPaymentService — creates, manages, and cancels recurring payments.
 *
 * Every scheduled payment corresponds to one Quartz job + trigger.
 * The job fires on the cron schedule and calls LedgerService.transfer().
 *
 * Quartz job key format: "transfer-{scheduledPaymentId}"
 * Quartz group         : "scheduled-transfers"
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledPaymentService {

    private static final String JOB_GROUP = "scheduled-transfers";

    private final ScheduledPaymentRepository scheduledPaymentRepository;
    private final AccountRepository          accountRepository;
    private final Scheduler                  scheduler;

    // ── Create ────────────────────────────────────────────────────────────────

    @Transactional
    public ScheduledPaymentResponse create(ScheduledPaymentRequest.Create request,
                                           String ownerUsername) {
        log.info("Creating scheduled payment: {} → {} {} {} cron='{}'",
                request.getSourceAccountNumber(),
                request.getDestinationAccountNumber(),
                request.getAmount(), request.getCurrency(),
                request.getCronExpression());

        Account source = accountRepository
                .findByAccountNumber(request.getSourceAccountNumber())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Account", request.getSourceAccountNumber()));
        Account destination = accountRepository
                .findByAccountNumber(request.getDestinationAccountNumber())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Account", request.getDestinationAccountNumber()));

        if (!source.getOwner().getUsername().equals(ownerUsername)) {
            throw new AccessDeniedException(
                    "You do not own source account: " + source.getAccountNumber());
        }

        // Validate cron expression before persisting
        try {
            CronExpression.validateExpression(request.getCronExpression());
        } catch (Exception ex) {
            throw new IllegalArgumentException(
                    "Invalid cron expression: '" + request.getCronExpression()
                            + "' — " + ex.getMessage());
        }

        String jobKey = "transfer-" + UUID.randomUUID();

        ScheduledPayment payment = ScheduledPayment.builder()
                .sourceAccount(source)
                .destinationAccount(destination)
                .amount(request.getAmount())
                .currency(request.getCurrency().toUpperCase())
                .description(request.getDescription())
                .cronExpression(request.getCronExpression())
                .jobKey(jobKey)
                .status(ScheduledPaymentStatus.ACTIVE)
                .build();

        payment = scheduledPaymentRepository.save(payment);
        scheduleQuartzJob(payment, ownerUsername);

        log.info("Scheduled payment created: id='{}' jobKey='{}'",
                payment.getId(), jobKey);
        return toResponse(payment);
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ScheduledPaymentResponse> getMyScheduledPayments(String ownerUsername) {
        Account owner = accountRepository.findAll().stream()
                .filter(a -> a.getOwner().getUsername().equals(ownerUsername))
                .findFirst()
                .map(Account::getOwner)
                .map(user -> (Account) null)
                .orElse(null);

        // Simpler: find all scheduled payments whose source account owner = username
        return scheduledPaymentRepository.findAll().stream()
                .filter(sp -> sp.getSourceAccount().getOwner()
                        .getUsername().equals(ownerUsername))
                .map(this::toResponse)
                .toList();
    }

    // ── Cancel ────────────────────────────────────────────────────────────────

    @Transactional
    public ScheduledPaymentResponse cancel(UUID scheduledPaymentId, String ownerUsername) {
        log.info("Cancelling scheduled payment id='{}'", scheduledPaymentId);

        ScheduledPayment payment = scheduledPaymentRepository.findById(scheduledPaymentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ScheduledPayment", scheduledPaymentId));

        if (!payment.getSourceAccount().getOwner().getUsername().equals(ownerUsername)) {
            throw new AccessDeniedException("You do not own this scheduled payment.");
        }

        try {
            scheduler.deleteJob(JobKey.jobKey(payment.getJobKey(), JOB_GROUP));
            log.info("Quartz job deleted: '{}'", payment.getJobKey());
        } catch (SchedulerException ex) {
            log.warn("Could not delete Quartz job '{}': {}",
                    payment.getJobKey(), ex.getMessage());
        }

        payment.setStatus(ScheduledPaymentStatus.CANCELLED);
        return toResponse(scheduledPaymentRepository.save(payment));
    }

    // ── Quartz job registration ───────────────────────────────────────────────

    private void scheduleQuartzJob(ScheduledPayment payment, String ownerUsername) {
        JobDetail job = JobBuilder.newJob(
                        com.bankingcore.bankingledger.service.TransferJob.class)
                .withIdentity(payment.getJobKey(), JOB_GROUP)
                .withDescription("Scheduled transfer: " + payment.getDescription())
                // Store all data needed by TransferJob.execute()
                .usingJobData("scheduledPaymentId",    payment.getId().toString())
                .usingJobData("sourceAccountNumber",   payment.getSourceAccount().getAccountNumber())
                .usingJobData("destinationAccountNumber", payment.getDestinationAccount().getAccountNumber())
                .usingJobData("amount",                payment.getAmount().toPlainString())
                .usingJobData("currency",              payment.getCurrency())
                .usingJobData("description",           payment.getDescription())
                .usingJobData("ownerUsername",         ownerUsername)
                .storeDurably()
                .build();

        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(payment.getJobKey() + "-trigger", JOB_GROUP)
                .withSchedule(CronScheduleBuilder
                        .cronSchedule(payment.getCronExpression())
                        .withMisfireHandlingInstructionFireAndProceed())
                .build();

        try {
            scheduler.scheduleJob(job, trigger);

            // Update nextFireAt from Quartz
            Date nextFire = scheduler.getTrigger(
                            TriggerKey.triggerKey(payment.getJobKey() + "-trigger", JOB_GROUP))
                    .getNextFireTime();
            if (nextFire != null) {
                payment.setNextFireAt(nextFire.toInstant());
                scheduledPaymentRepository.save(payment);
            }

            log.info("Quartz job scheduled: '{}' cron='{}'",
                    payment.getJobKey(), payment.getCronExpression());
        } catch (SchedulerException ex) {
            log.error("Failed to schedule Quartz job '{}': {}",
                    payment.getJobKey(), ex.getMessage());
            throw new RuntimeException("Failed to schedule payment: " + ex.getMessage(), ex);
        }
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    private ScheduledPaymentResponse toResponse(ScheduledPayment sp) {
        return ScheduledPaymentResponse.builder()
                .id(sp.getId())
                .sourceAccountNumber(sp.getSourceAccount().getAccountNumber())
                .destinationAccountNumber(sp.getDestinationAccount().getAccountNumber())
                .amount(sp.getAmount())
                .currency(sp.getCurrency())
                .description(sp.getDescription())
                .cronExpression(sp.getCronExpression())
                .status(sp.getStatus())
                .lastExecutedAt(sp.getLastExecutedAt())
                .nextFireAt(sp.getNextFireAt())
                .executionCount(sp.getExecutionCount())
                .createdAt(sp.getCreatedAt())
                .build();
    }
}