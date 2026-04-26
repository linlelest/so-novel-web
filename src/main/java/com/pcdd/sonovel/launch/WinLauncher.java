package com.pcdd.sonovel.launch;

import cn.hutool.core.lang.Console;
import com.pcdd.sonovel.core.AppConfigLoader;

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
        SwingUtilities.invokeLater(WinLauncher::startGui);
    }

    private static void startGui() {
        int port = AppConfigLoader.APP_CONFIG.getWebPort() > 0 ? AppConfigLoader.APP_CONFIG.getWebPort() : 7765;
        String host = getLocalHost();
        String url = "http://" + host + ":" + port;
        String loginUrl = url + "/login.html";

        boolean openBrowser = showStartupDialog(host, port, loginUrl);

        trayIcon = createTrayIcon(url, loginUrl);
        addTrayToSystemTray(trayIcon);

        if (openBrowser) openBrowser(loginUrl);

        // 保持 EDT 存活，等待系统托盘事件
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
            // 移除托盘图标后强制退出 JVM
            try { SystemTray.getSystemTray().remove(trayIcon); } catch (Exception ignored) {}
            Runtime.getRuntime().halt(0);
        });
        popup.add(exitItem);

        TrayIcon ti = new TrayIcon(image, "SoNovel Web - " + url, popup);
        ti.setImageAutoSize(true);
        ti.addActionListener(e -> openBrowser(loginUrl));
        return ti;
    }

    private static Image loadTrayIcon() {
        // 1. 尝试从 classpath 加载 logo.ico
        try (InputStream is = WinLauncher.class.getResourceAsStream("/static/logo.ico")) {
            if (is != null) {
                byte[] data = is.readAllBytes();
                Image img = Toolkit.getDefaultToolkit().createImage(data);
                MediaTracker tracker = new MediaTracker(new Panel());
                tracker.addImage(img, 0);
                tracker.waitForID(1000); // 等待最多1秒
                if (img.getWidth(null) > 0) return img;
            }
        } catch (Exception ignored) {}

        // 2. 尝试从文件系统加载 (同级目录)
        try {
            Path p = Paths.get(System.getProperty("user.dir"), "logo.ico");
            if (Files.exists(p)) {
                byte[] data = Files.readAllBytes(p);
                Image img = Toolkit.getDefaultToolkit().createImage(data);
                MediaTracker tracker = new MediaTracker(new Panel());
                tracker.addImage(img, 0);
                tracker.waitForID(500);
                if (img.getWidth(null) > 0) return img;
            }
        } catch (Exception ignored) {}

        // 3. 绘制后备图标 (紫色圆+S)
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
            if (!SystemTray.isSupported()) { Console.error("系统托盘不支持"); return; }
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
            // launch4j 构建后 runtime 在 exe 同级目录
            Path javaExe = Paths.get(workDir, "runtime", "bin", "javaw.exe");
            Path jar = Paths.get(workDir, "app.jar");

            if (!Files.exists(javaExe)) {
                // fallback to system java
                javaExe = Paths.get(System.getProperty("java.home"), "bin", "javaw.exe");
                if (!Files.exists(javaExe)) return;
            }
            if (!Files.exists(jar)) return;

            String cmd = "\"" + javaExe + "\" -jar \"" + jar + "\"";

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
