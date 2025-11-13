package tech.scytalesystems.cache_sync_starter.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import tech.scytalesystems.cache_sync_starter.dto.CacheAction;
import tech.scytalesystems.cache_sync_starter.dto.CacheMessage;
import tech.scytalesystems.cache_sync_starter.sync.CacheSyncService;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * @author Gathariki Ngigi
 * Created on 13/11/2025
 * Time 1300h
 * <p>Aspect that intercepts @CacheEvict annotations and publishes
 * cache invalidation messages to other application instances.
 */
@Aspect
public record CacheEvictAspect(CacheSyncService cacheSyncService) {
    private static final Logger log = LoggerFactory.getLogger(CacheEvictAspect.class);
    private static final SpelExpressionParser PARSER = new SpelExpressionParser();

    @SuppressWarnings("unused")
    @Around("@annotation(org.springframework.cache.annotation.CacheEvict)")
    public Object aroundCacheEvict(ProceedingJoinPoint pjp) throws Throwable {
        // Don't publish during remote evictions to prevent loops
        if (CacheSyncService.isRemoteEviction()) return pjp.proceed();

        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        CacheEvict cacheEvict = AnnotationUtils.findAnnotation(method, CacheEvict.class);

        if (cacheEvict == null) return pjp.proceed();

        // Execute the method
        Object result = pjp.proceed();

        // Publish cache invalidation message after successful execution
        try {
            publishCacheEviction(pjp, cacheEvict);
        } catch (Exception e) {
            log.warn("Failed to publish cache eviction for method: {}", method.getName(), e);
        }

        return result;
    }

    private void publishCacheEviction(ProceedingJoinPoint pjp, CacheEvict cacheEvict) {
        String[] cacheNames = resolveCacheNames(cacheEvict);

        for (String cacheName : cacheNames) {
            CacheMessage.CacheMessageBuilder builder = CacheMessage.builder()
                    .cacheName(cacheName);

            if (cacheEvict.allEntries()) {
                // Clear entire cache
                builder.action(CacheAction.CLEAR)
                        .keys(new ArrayList<>());
            } else {
                // Evict specific keys
                List<String> keys = extractKeys(pjp, cacheEvict);
                builder.action(CacheAction.EVICT)
                        .keys(keys);
            }

            cacheSyncService.publish(builder.build());
        }
    }

    private String[] resolveCacheNames(CacheEvict cacheEvict) {
        // Try 'value' first, then 'cacheNames'
        String[] names = cacheEvict.value();
        if (names.length == 0) names = cacheEvict.cacheNames();

        return names;
    }

    private List<String> extractKeys(ProceedingJoinPoint pjp, CacheEvict cacheEvict) {
        List<String> keys = new ArrayList<>();
        String keyExpression = cacheEvict.key();

        if (keyExpression.isEmpty()) {
            // No key expression - use all arguments as keys
            Object[] args = pjp.getArgs();
            for (Object arg : args) {
                if (arg != null) keys.add(arg.toString());
            }
        } else {
            // Evaluate SpEL expression
            try {
                EvaluationContext context = createEvaluationContext(pjp);
                Object keyValue = PARSER.parseExpression(keyExpression).getValue(context);

                if (keyValue != null) keys.add(keyValue.toString());
            } catch (Exception e) {
                log.warn("Failed to evaluate key expression: {}", keyExpression, e);

                // Fallback to using arguments
                Arrays.stream(pjp.getArgs())
                        .filter(Objects::nonNull)
                        .forEach(arg -> keys.add(arg.toString()));
            }
        }

        return keys;
    }

    private EvaluationContext createEvaluationContext(ProceedingJoinPoint pjp) {
        StandardEvaluationContext context = new StandardEvaluationContext();

        // Add method arguments
        Object[] args = pjp.getArgs();
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        String[] paramNames = signature.getParameterNames();

        for (int i = 0; i < args.length; i++) {
            context.setVariable("p" + i, args[i]);
            context.setVariable("a" + i, args[i]);

            if (paramNames != null && i < paramNames.length) context.setVariable(paramNames[i], args[i]);
        }

        // Add target object
        context.setVariable("target", pjp.getTarget());
        context.setVariable("this", pjp.getTarget());

        return context;
    }
}
