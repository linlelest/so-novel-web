package com.pcdd.sonovel.web;

import java.util.concurrent.ConcurrentHashMap;

/** Maps download-id → file-relative-path for browser download resolution */
public class DownloadTracker {
    private static final ConcurrentHashMap<String, String> map = new ConcurrentHashMap<>();

    public static void put(String id, String path) { map.put(id, path); }
    public static String getAndRemove(String id) { return map.remove(id); }
}
