package com.pcdd.sonovel.util;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存速率限制器
 *
 * @author pcdd
 */
public class RateLimiter {

    private static final ConcurrentHashMap<String, long[]> buckets = new ConcurrentHashMap<>();

    /**
     * 检查是否超出速率
     *
     * @param key     标识 (IP 或 API path)
     * @param maxReqs 最大请求数
     * @param windowMs 时间窗口 (毫秒)
     * @return true=允许, false=超限
     */
    public synchronized static boolean allow(String key, int maxReqs, long windowMs) {
        long now = System.currentTimeMillis();
        long[] arr = buckets.computeIfAbsent(key, k -> new long[0]);
        // 清除过期记录
        int validCount = 0;
        for (long t : arr) {
            if (now - t < windowMs) validCount++;
        }
        if (validCount >= maxReqs) return false;
        // 添加当前请求时间戳
        long[] newArr = new long[validCount + 1];
        int idx = 0;
        for (long t : arr) {
            if (now - t < windowMs) newArr[idx++] = t;
        }
        newArr[idx] = now;
        buckets.put(key, newArr);
        return true;
    }

}

