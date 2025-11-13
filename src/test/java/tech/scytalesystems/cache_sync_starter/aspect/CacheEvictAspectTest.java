package tech.scytalesystems.cache_sync_starter.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.annotation.CacheEvict;
import tech.scytalesystems.cache_sync_starter.dto.CacheAction;
import tech.scytalesystems.cache_sync_starter.dto.CacheMessage;
import tech.scytalesystems.cache_sync_starter.sync.CacheSyncService;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * @author Gathariki Ngigi
 * Created on 13/11/2025
 * Time 2334h
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CacheEvictAspect Tests")
@SuppressWarnings("unused")
class CacheEvictAspectTest {

    @Mock
    private CacheSyncService cacheSyncService;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature signature;

    private CacheEvictAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new CacheEvictAspect(cacheSyncService);
    }

    @Test
    @DisplayName("Should publish message for @CacheEvict with key")
    @Disabled("Currently not supported")
    void testPublishForCacheEvictWithKey() throws Throwable {
        Method method = TestService.class.getMethod("evictUser", Long.class);
        CacheEvict annotation = method.getAnnotation(CacheEvict.class);

        when(joinPoint.proceed()).thenReturn(null);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.getArgs()).thenReturn(new Object[]{123L});

        aspect.aroundCacheEvict(joinPoint);

        ArgumentCaptor<CacheMessage> messageCaptor = ArgumentCaptor.forClass(CacheMessage.class);
        verify(cacheSyncService).publish(messageCaptor.capture());

        CacheMessage message = messageCaptor.getValue();
        assertEquals("users", message.getCacheName());
        assertEquals(CacheAction.EVICT, message.getAction());
        assertEquals(1, message.getKeys().size());
    }

    @Test
    @DisplayName("Should publish message for @CacheEvict with allEntries=true")
    void testPublishForClearCache() throws Throwable {
        Method method = TestService.class.getMethod("clearUsers");

        when(joinPoint.proceed()).thenReturn(null);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
//        when(joinPoint.getArgs()).thenReturn(new Object[]{});

        aspect.aroundCacheEvict(joinPoint);

        ArgumentCaptor<CacheMessage> messageCaptor = ArgumentCaptor.forClass(CacheMessage.class);
        verify(cacheSyncService).publish(messageCaptor.capture());

        CacheMessage message = messageCaptor.getValue();
        assertEquals("users", message.getCacheName());
        assertEquals(CacheAction.CLEAR, message.getAction());
        assertTrue(message.getKeys().isEmpty());
    }

    @Test
    @DisplayName("Should execute method and then publish")
    void testExecutionOrder() throws Throwable {
        Method method = TestService.class.getMethod("evictUser", Long.class);

        when(joinPoint.proceed()).thenReturn("result");
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.getArgs()).thenReturn(new Object[]{123L});

        Object result = aspect.aroundCacheEvict(joinPoint);

        assertEquals("result", result);
        verify(joinPoint).proceed();
        verify(cacheSyncService).publish(any(CacheMessage.class));
    }

    @Test
    @DisplayName("Should not publish if method throws exception")
    void testNoPublishOnException() throws Throwable {
        Method method = TestService.class.getMethod("evictUser", Long.class);

        when(joinPoint.proceed()).thenThrow(new RuntimeException("Test exception"));
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);

        assertThrows(RuntimeException.class, () -> aspect.aroundCacheEvict(joinPoint));

        verify(cacheSyncService, never()).publish(any());
    }

    // Test service class with @CacheEvict annotations
    static class TestService {
        @CacheEvict(value = "users", key = "#id")
        public void evictUser(Long id) {
        }

        @CacheEvict(value = "users", allEntries = true)
        public void clearUsers() {
        }
    }
}