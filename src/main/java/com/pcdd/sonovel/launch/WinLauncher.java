package com.pcdd.sonovel.launch;

import cn.hutool.core.lang.Console;
import com.pcdd.sonovel.core.AppConfigLoader;
import com.pcdd.sonovel.web.WebServer;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.InetAddress;
import java.net.URI;
import java.nio.file.*;

/**
 * Windows 托盘启动器 — 双击启动弹窗 + 系统托盘驻留 + 开机自启
 */
public class WinLauncher {

    private static TrayIcon trayIcon;

    public static void launch() {
        // Force Chinese locale so AWT native menus render Chinese characters
        System.setProperty("user.language", "zh");
        System.setProperty("user.region", "CN");

        int port = AppConfigLoader.APP_CONFIG.getWebPort() > 0 ? AppConfigLoader.APP_CONFIG.getWebPort() : 7765;
        String host = getLocalHost();
        String url = "http://" + host + ":" + port;
        String loginUrl = url + "/login.html";

        // Phase 1: startup dialog on EDT (blocking via invokeAndWait to capture return value)
        boolean openBrowser;
        try {
            final boolean[] ref = new boolean[1];
            SwingUtilities.invokeAndWait(() -> ref[0] = showStartupDialog(host, port, loginUrl));
            openBrowser = ref[0];
        } catch (Exception e) {
            Console.error("Startup dialog failed: {}", e.getMessage());
            openBrowser = false;
        }

        // Phase 2: tray icon on EDT (non-blocking, EDT stays free to process menu events)
        SwingUtilities.invokeLater(() -> {
            trayIcon = createTrayIcon(url, loginUrl);
            addTrayToSystemTray(trayIcon);
        });

        if (openBrowser) openBrowser(loginUrl);

        // Phase 3: keep main thread alive — EDT remains free to handle tray popup menu
        try { Thread.sleep(Long.MAX_VALUE); } catch (InterruptedException ignored) {}
    }

    private static boolean showStartupDialog(String host, int port, String url) {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JLabel titleLabel = new JLabel("SoNovel Web 已启动");
        titleLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 16));
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JTextArea infoArea = new JTextArea(
                "服务地址:  " + url + "\n"
                        + "本地访问:  http://127.0.0.1:" + port + "\n\n"
                        + "首次使用请注册管理员账号。\n程序将在系统托盘后台运行。");
        infoArea.setEditable(false); infoArea.setOpaque(false);
        infoArea.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));

        JCheckBox autoStartCheck = new JCheckBox("开机自动启动");
        autoStartCheck.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        autoStartCheck.setSelected(isAutoStartEnabled());
        autoStartCheck.addActionListener(e -> setAutoStart(autoStartCheck.isSelected()));

        JButton openBtn = new JButton("打开网页");
        openBtn.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
        JButton closeBtn = new JButton("确定");
        closeBtn.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        btnPanel.add(openBtn); btnPanel.add(closeBtn);
        panel.add(titleLabel, BorderLayout.NORTH);
        panel.add(infoArea, BorderLayout.CENTER);
        panel.add(autoStartCheck, BorderLayout.SOUTH);

        JDialog dialog = new JDialog((Frame) null, "SoNovel Web", true);
        dialog.setLayout(new BorderLayout());
        dialog.add(panel, BorderLayout.CENTER);
        dialog.add(btnPanel, BorderLayout.SOUTH);

        final boolean[] shouldOpen = {false};
        openBtn.addActionListener(e -> { shouldOpen[0] = true; dialog.dispose(); });
        closeBtn.addActionListener(e -> dialog.dispose());

        dialog.pack(); dialog.setLocationRelativeTo(null);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setVisible(true);
        return shouldOpen[0];
    }

    private static TrayIcon createTrayIcon(String url, String loginUrl) {
        Image image = loadTrayIcon();

        PopupMenu popup = new PopupMenu();

        MenuItem openItem = new MenuItem("打开网页");
        openItem.addActionListener(e -> openBrowser(loginUrl));
        popup.add(openItem);
        popup.addSeparator();

        CheckboxMenuItem autoStartItem = new CheckboxMenuItem("开机自启");
        autoStartItem.setState(isAutoStartEnabled());
        autoStartItem.addItemListener(e -> setAutoStart(autoStartItem.getState()));
        popup.add(autoStartItem);
        popup.addSeparator();

        MenuItem exitItem = new MenuItem("退出");
        exitItem.addActionListener(e -> {
            try { SystemTray.getSystemTray().remove(trayIcon); } catch (Exception ignored) {}
            WebServer.shutdown();
            Runtime.getRuntime().halt(0);
        });
        popup.add(exitItem);

        TrayIcon ti = new TrayIcon(image, "SoNovel Web - " + url, popup);
        ti.setImageAutoSize(true);
        ti.addActionListener(e -> openBrowser(loginUrl));
        return ti;
    }

    private static Image loadTrayIcon() {
        // 1. PNG via ImageIO — most reliable across AWT/JDK versions
        try (InputStream is = WinLauncher.class.getResourceAsStream("/static/logo.png")) {
            if (is != null) {
                BufferedImage bi = ImageIO.read(is);
                if (bi != null) return bi;
            }
        } catch (Exception ignored) {}

        // 2. ICO via Toolkit with correct MediaTracker waitForID(id, ms) timeout
        try (InputStream is = WinLauncher.class.getResourceAsStream("/static/logo.ico")) {
            if (is != null) {
                byte[] data = is.readAllBytes();
                Image img = Toolkit.getDefaultToolkit().createImage(data);
                MediaTracker tracker = new MediaTracker(new Panel());
                tracker.addImage(img, 0);
                tracker.waitForID(0, 1000); // wait for image ID 0, timeout 1000ms
                if (img.getWidth(null) > 0) return img;
            }
        } catch (Exception ignored) {}

        // 3. File-system PNG (packaged alongside exe)
        try {
            Path p = Paths.get(System.getProperty("user.dir"), "logo.png");
            if (Files.exists(p)) {
                BufferedImage bi = ImageIO.read(p.toFile());
                if (bi != null) return bi;
            }
        } catch (Exception ignored) {}

        // 4. File-system ICO with correct MediaTracker
        try {
            Path p = Paths.get(System.getProperty("user.dir"), "logo.ico");
            if (Files.exists(p)) {
                byte[] data = Files.readAllBytes(p);
                Image img = Toolkit.getDefaultToolkit().createImage(data);
                MediaTracker tracker = new MediaTracker(new Panel());
                tracker.addImage(img, 0);
                tracker.waitForID(0, 500);
                if (img.getWidth(null) > 0) return img;
            }
        } catch (Exception ignored) {}

        // 5. Fallback drawn icon (purple circle + "S")
        BufferedImage bi = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = bi.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(93, 93, 129)); g.fillOval(0, 0, 16, 16);
        g.setColor(Color.WHITE); g.setFont(new Font("Arial", Font.BOLD, 10));
        g.drawString("S", 3, 12); g.dispose();
        return bi;
    }

    private static void addTrayToSystemTray(TrayIcon ti) {
        try {
            if (!SystemTray.isSupported()) {
                Console.error("系统托盘不支持");
                // Show visible error dialog since tray icon is the entire UI
                JOptionPane.showMessageDialog(null,
                        "系统不支持托盘图标，请在控制台查看服务信息。\n访问: http://127.0.0.1:" + AppConfigLoader.APP_CONFIG.getWebPort() + "/login.html",
                        "系统托盘不可用",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            SystemTray.getSystemTray().add(ti);
        } catch (Exception e) {
            Console.error("添加托盘失败: {}", e.getMessage());
        }
    }

    private static String getLocalHost() {
        try { return InetAddress.getLocalHost().getHostAddress(); }
        catch (Exception e) { return "127.0.0.1"; }
    }

    private static void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            } else {
                Runtime.getRuntime().exec("rundll32 url.dll,FileProtocolHandler " + url);
            }
        } catch (Exception ignored) {}
    }

    // === 开机自启 ===
    private static boolean isAutoStartEnabled() {
        try {
            Process p = Runtime.getRuntime().exec("reg query HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run /v SoNovelWeb");
            p.waitFor(); return p.exitValue() == 0;
        } catch (Exception e) { return false; }
    }

    private static void setAutoStart(boolean enable) {
        try {
            String workDir = System.getProperty("user.dir");
            // Prefer sonovel.exe (Launch4j wrapper), fall back to app.jar + javaw
            Path exe = Paths.get(workDir, "sonovel.exe");
            String cmd;
            if (Files.exists(exe)) {
                cmd = "\"" + exe.toAbsolutePath() + "\"";
            } else {
                Path javaExe = Paths.get(workDir, "runtime", "bin", "javaw.exe");
                Path jar = Paths.get(workDir, "app.jar");
                if (!Files.exists(javaExe)) {
                    javaExe = Paths.get(System.getProperty("java.home"), "bin", "javaw.exe");
                    if (!Files.exists(javaExe)) return;
                }
                if (!Files.exists(jar)) return;
                cmd = "\"" + javaExe + "\" -jar \"" + jar + "\"";
            }

            if (enable) {
                new ProcessBuilder("reg", "add",
                        "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run",
                        "/v", "SoNovelWeb", "/t", "REG_SZ", "/d", cmd, "/f").start().waitFor();
            } else {
                new ProcessBuilder("reg", "delete",
                        "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run",
                        "/v", "SoNovelWeb", "/f").start().waitFor();
            }
        } catch (Exception e) {
            Console.error("开机自启设置失败: {}", e.getMessage());
        }
    }
}
