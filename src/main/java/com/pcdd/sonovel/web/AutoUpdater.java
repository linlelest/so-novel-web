package com.pcdd.sonovel.web;

import cn.hutool.core.lang.Console;
import cn.hutool.json.JSONObject;
import com.pcdd.sonovel.web.servlet.UpdateService;

import java.time.*;
import java.util.concurrent.*;

/**
 * 程序内部定时检测更新。每周日 UTC 07:00 自动检测并安装更新。
 * WebServer 启动时调用 {@link #start()} 即可。
 */
public class AutoUpdater {

    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "auto-updater");
        t.setDaemon(true);
        return t;
    });

    private AutoUpdater() {
    }

    /**
     * 启动定时器。计算到下一个周日 UTC 07:00 的延迟，然后按 7 天周期执行。
     */
    public static void start() {
        long delayMs = computeDelayToNextSunday7amUTC();
        Console.log("[auto-updater] 下次自动检测更新: {} ({} 天后)", formatUntil(delayMs), delayMs / 86_400_000);
        scheduler.scheduleAtFixedRate(AutoUpdater::checkAndUpdate, delayMs, 7 * 24 * 3600_000L, TimeUnit.MILLISECONDS);
    }

    private static void checkAndUpdate() {
        Console.log("[auto-updater] 开始检测更新...");
        try {
            JSONObject r = UpdateService.checkUpdate();
            String err = r.getStr("error");
            if (err != null) {
                Console.error("[auto-updater] 检测失败: {}", err);
                return;
            }

            boolean hasUpdate = r.getBool("hasUpdate");
            if (!hasUpdate) {
                Console.log("[auto-updater] 当前已是最新版: {}", r.getStr("currentVersion"));
                return;
            }

            Console.log("[auto-updater] 发现新版本: {} → {}", r.getStr("currentVersion"), r.getStr("latestVersion"));
            Console.log("[auto-updater] 开始自动下载安装...");

            boolean ok = UpdateService.applyUpdate();
            if (ok) {
                Console.log("[auto-updater] 更新成功，服务器已进入维护模式，等待重启...");
            } else {
                Console.error("[auto-updater] 更新执行失败");
            }
        } catch (Exception e) {
            Console.error("[auto-updater] 自动更新异常", e);
        }
    }

    /**
     * 计算到下一个周日 UTC 07:00 的毫秒数
     */
    private static long computeDelayToNextSunday7amUTC() {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        ZonedDateTime next = now.with(DayOfWeek.SUNDAY)
                .withHour(7).withMinute(0).withSecond(0).withNano(0);
        if (!now.isBefore(next)) {
            next = next.plusWeeks(1);
        }
        return Duration.between(now, next).toMillis();
    }

    private static String formatUntil(long ms) {
        long days = ms / 86_400_000;
        long hours = (ms % 86_400_000) / 3_600_000;
        return days + "天" + hours + "小时";
    }
}
