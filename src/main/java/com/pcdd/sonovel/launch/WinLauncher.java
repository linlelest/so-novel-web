package com.pcdd.sonovel.launch;

import cn.hutool.core.lang.Console;
import com.pcdd.sonovel.core.AppConfigLoader;
import com.pcdd.sonovel.web.WebServer;
import lombok.SneakyThrows;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.InetAddress;
import java.net.URI;

/**
 * Windows 托盘启动器
 * <p>
 * 双击运行 → 弹窗显示地址 → 打开浏览器 → 系统托盘驻留
 *
 * @author pcdd
 */
public class WinLauncher {

    public static void launch() {
        // 必须在 EDT 线程启动 GUI
        SwingUtilities.invokeLater(WinLauncher::startGui);
    }

    private static void startGui() {
        int port = AppConfigLoader.APP_CONFIG.getWebPort() > 0 ? AppConfigLoader.APP_CONFIG.getWebPort() : 7765;
        String host = getLocalHost();
        String url = "http://" + host + ":" + port;
        String loginUrl = url + "/login.html";

        // 1. 启动弹窗
        boolean openBrowser = showStartupDialog(host, port, loginUrl);

        // 2. 创建系统托盘
        TrayIcon trayIcon = createTrayIcon(url, loginUrl);
        addTrayToSystemTray(trayIcon);

        // 3. 如果用户点击"打开网页"，启动浏览器
        if (openBrowser) {
            openBrowser(loginUrl);
        }
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
                        + "首次使用请注册管理员账号。\n"
                        + "程序将在系统托盘后台运行。");
        infoArea.setEditable(false);
        infoArea.setOpaque(false);
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
        btnPanel.add(openBtn);
        btnPanel.add(closeBtn);

        panel.add(titleLabel, BorderLayout.NORTH);
        panel.add(infoArea, BorderLayout.CENTER);
        panel.add(autoStartCheck, BorderLayout.SOUTH);

        JDialog dialog = new JDialog((Frame) null, "SoNovel Web", true);
        dialog.setLayout(new BorderLayout());
        dialog.add(panel, BorderLayout.CENTER);
        dialog.add(btnPanel, BorderLayout.SOUTH);

        final boolean[] shouldOpen = {false};

        openBtn.addActionListener(e -> {
            shouldOpen[0] = true;
            dialog.dispose();
        });
        closeBtn.addActionListener(e -> dialog.dispose());

        dialog.pack();
        dialog.setLocationRelativeTo(null);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setVisible(true);

        return shouldOpen[0];
    }

    private static TrayIcon createTrayIcon(String url, String loginUrl) {
        Image image = createTrayImage();

        PopupMenu popup = new PopupMenu();

        MenuItem openItem = new MenuItem("打开网页");
        openItem.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        openItem.addActionListener(e -> openBrowser(loginUrl));
        popup.add(openItem);

        popup.addSeparator();

        CheckboxMenuItem autoStartItem = new CheckboxMenuItem("开机自启");
        autoStartItem.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        autoStartItem.setState(isAutoStartEnabled());
        autoStartItem.addItemListener(e -> setAutoStart(autoStartItem.getState()));
        popup.add(autoStartItem);

        popup.addSeparator();

        MenuItem exitItem = new MenuItem("退出");
        exitItem.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        exitItem.addActionListener(e -> System.exit(0));
        popup.add(exitItem);

        TrayIcon trayIcon = new TrayIcon(image, "SoNovel Web - " + url, popup);
        trayIcon.setImageAutoSize(true);
        trayIcon.addActionListener(e -> openBrowser(loginUrl));

        return trayIcon;
    }

    private static Image createTrayImage() {
        // 创建一个 16x16 的简单图标
        BufferedImage bi = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = bi.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(93, 93, 129));
        g.fillOval(0, 0, 16, 16);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 10));
        g.drawString("S", 3, 12);
        g.dispose();
        return bi;
    }

    private static void addTrayToSystemTray(TrayIcon trayIcon) {
        try {
            SystemTray.getSystemTray().add(trayIcon);
        } catch (Exception e) {
            Console.error("无法添加系统托盘图标: {}", e.getMessage());
        }
    }

    private static String getLocalHost() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    @SneakyThrows
    private static void openBrowser(String url) {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(new URI(url));
        } else {
            Runtime.getRuntime().exec("rundll32 url.dll,FileProtocolHandler " + url);
        }
    }

    private static boolean isAutoStartEnabled() {
        try {
            Process p = Runtime.getRuntime().exec(
                    "reg query HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run /v SoNovelWeb");
            p.waitFor();
            return p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @SneakyThrows
    private static void setAutoStart(boolean enable) {
        String appPath = System.getProperty("java.class.path");
        // 找到 app.jar
        if (appPath == null) return;

        String jarPath = null;
        for (String part : appPath.split(System.getProperty("path.separator"))) {
            if (part.endsWith("app.jar") || part.endsWith("app-jar-with-dependencies.jar")) {
                jarPath = part;
                break;
            }
        }
        if (jarPath == null) return;

        String javaHome = System.getProperty("java.home");
        String javaw = javaHome + "\\bin\\javaw.exe";
        String cmd = javaw + " -jar \"" + jarPath + "\"";

        if (enable) {
            Runtime.getRuntime().exec(
                    "reg add HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run /v SoNovelWeb /t REG_SZ /d \""
                            + cmd.replace("\"", "\\\"") + "\" /f");
        } else {
            Runtime.getRuntime().exec(
                    "reg delete HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run /v SoNovelWeb /f");
        }
    }

}
