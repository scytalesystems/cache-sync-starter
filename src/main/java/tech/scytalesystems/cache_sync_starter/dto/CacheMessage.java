package tech.scytalesystems.cache_sync_starter.dto;

import java.util.List;

/**
 * @author Gathariki Ngigi
 * Created on 13/11/2025
 * Time 1303h
 */
public record CacheMessage (String cacheName, List<String> keys, CacheAction action){}