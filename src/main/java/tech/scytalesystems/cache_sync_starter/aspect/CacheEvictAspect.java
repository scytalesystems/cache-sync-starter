package tech.scytalesystems.cache_sync_starter.aspect;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.cache.annotation.CacheEvict;
import tech.scytalesystems.cache_sync_starter.dto.CacheAction;
import tech.scytalesystems.cache_sync_starter.dto.CacheMessage;
import tech.scytalesystems.cache_sync_starter.sync.CacheSyncService;

import java.util.List;

/**
 * @author Gathariki Ngigi
 * Created on 13/11/2025
 * Time 1317h
 * <p>Aspect for @CacheEvict annotation i.e, intercepts @CacheEvict
 */
@SuppressWarnings("all")
@Aspect
@RequiredArgsConstructor
public class CacheEvictAspect {
    private final CacheSyncService syncService;

    @AfterReturning("@annotation(cacheEvict)")
    public void afterEvict(JoinPoint joinPoint, CacheEvict cacheEvict) {
        if (cacheEvict == null) return;
        String[] names = cacheEvict.value();

        if (names.length == 0) return;

        String cacheName = names[0];

        if (cacheEvict.allEntries()) {
            syncService.publish(new CacheMessage(cacheName, null, CacheAction.CLEAR));
            return;
        }

        // Attempt to determine key(s):
        // if key is SpEL, we can't resolve easily here — we rely on common case where first arg is key.
        Object firstArg = joinPoint.getArgs().length > 0 ? joinPoint.getArgs()[0] : null;
        if (firstArg != null) syncService.publish(new CacheMessage(cacheName, List.of(String.valueOf(firstArg)), CacheAction.EVICT));
    }
}
