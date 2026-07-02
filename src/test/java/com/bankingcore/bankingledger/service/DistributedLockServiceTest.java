package com.bankingcore.bankingledger.service;

import com.bankingcore.bankingledger.exception.DistributedLockException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DistributedLockServiceTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock rLock;

    @InjectMocks
    private DistributedLockService lockService;

    // Guards against: Action not executing when the lock is successfully acquired
    @Test
    void executeWithLock_shouldExecuteActionAndUnlock_whenLockAcquired() throws InterruptedException {
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);

        Supplier<String> action = () -> "Success";

        String result = lockService.executeWithLock("account-123", action);

        assertThat(result).isEqualTo("Success");
        verify(rLock).unlock();
    }

    // Guards against: Deadlocks caused by continuing execution when lock acquisition times out
    @Test
    void executeWithLock_shouldThrowExceptionAndNotExecuteAction_whenLockAcquisitionFails() throws InterruptedException {
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(false);

        Supplier<String> action = mock(Supplier.class);

        assertThatThrownBy(() -> lockService.executeWithLock("account-123", action))
                .isInstanceOf(DistributedLockException.class);

        verify(action, never()).get();
        verify(rLock, never()).unlock();
    }

    // Guards against: Unreleased locks causing permanent denial of service after an internal exception
    @Test
    void executeWithLock_shouldUnlock_evenIfActionThrowsException() throws InterruptedException {
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);

        Supplier<String> action = () -> {
            throw new RuntimeException("Action Failed");
        };

        assertThatThrownBy(() -> lockService.executeWithLock("account-123", action))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Action Failed");

        // The critical assertion: unlock must still be called
        verify(rLock).unlock();
    }
}
