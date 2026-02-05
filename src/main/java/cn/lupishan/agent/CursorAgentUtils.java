package cn.lupishan.agent;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.project.Project;
import org.jetbrains.plugins.terminal.ShellTerminalWidget;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Shared utility methods for Cursor Agent plugin
 */
public final class CursorAgentUtils {
    private static final String NOTIF_GROUP_ID = "cursor.agent.notifications";
    
    private CursorAgentUtils() {
        // Utility class - no instantiation
    }

    /**
     * Resolve the absolute path to cursor-agent executable
     */
    public static String resolveAgentAbsolutePath(File workDir) {
        String env = System.getenv("CURSOR_AGENT");
        if (env != null && !env.trim().isEmpty()) {
            String p = expandHome(env.trim());
            if (new File(p).canExecute()) return p;
        }
        
        // Windows-specific detection
        if (System.getProperty("os.name").toLowerCase().startsWith("windows")) {
            return resolveAgentPathWindows(workDir);
        }
        
        // Unix/Linux/macOS detection
        return resolveAgentPathUnix(workDir);
    }
    
    /**
     * Resolve cursor-agent path on Windows
     */
    private static String resolveAgentPathWindows(File workDir) {
        // Try 'where.exe' command (available on Windows XP+)
        try {
            Process proc = new ProcessBuilder("where.exe", "cursor-agent")
                    .directory(workDir)
                    .redirectErrorStream(true)
                    .start();
            proc.waitFor();
            byte[] out = proc.getInputStream().readAllBytes();
            String resolved = new String(out, StandardCharsets.UTF_8).trim();
            if (!resolved.isEmpty()) {
                // 'where' may return multiple paths, take the first one
                String firstPath = resolved.split("\r?\n")[0].trim();
                File exeFile = new File(firstPath);
                if (exeFile.exists() && exeFile.canExecute()) {
                    return firstPath;
                }
            }
        } catch (Exception ignore) {}
        
        // Try PowerShell Get-Command (more reliable, but requires PowerShell)
        try {
            Process proc = new ProcessBuilder("powershell.exe", "-Command", 
                    "Get-Command cursor-agent -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source")
                    .directory(workDir)
                    .redirectErrorStream(true)
                    .start();
            proc.waitFor();
            byte[] out = proc.getInputStream().readAllBytes();
            String resolved = new String(out, StandardCharsets.UTF_8).trim();
            if (!resolved.isEmpty() && new File(resolved).canExecute()) {
                return resolved;
            }
        } catch (Exception ignore) {}
        
        // Fallback: try common executable extensions directly
        String[] extensions = {".exe", ".cmd", ".bat"};
        for (String ext : extensions) {
            try {
                Process proc = new ProcessBuilder("where.exe", "cursor-agent" + ext)
                        .directory(workDir)
                        .redirectErrorStream(true)
                        .start();
                proc.waitFor();
                byte[] out = proc.getInputStream().readAllBytes();
                String resolved = new String(out, StandardCharsets.UTF_8).trim();
                if (!resolved.isEmpty()) {
                    String firstPath = resolved.split("\r?\n")[0].trim();
                    File exeFile = new File(firstPath);
                    if (exeFile.exists() && exeFile.canExecute()) {
                        return firstPath;
                    }
                }
            } catch (Exception ignore) {}
        }
        
        return null;
    }
    
    /**
     * Resolve cursor-agent path on Unix/Linux/macOS
     */
    private static String resolveAgentPathUnix(File workDir) {
        // Try multiple shells to maximize compatibility
        String[] shells = {
            "/bin/zsh",  // macOS default shell (handles oh-my-zsh)
            "/bin/bash", // Common alternative
            "/bin/sh"    // Fallback
        };
        
        for (String shell : shells) {
            if (!new File(shell).exists()) continue;
            try {
                Process proc = new ProcessBuilder(shell, "-lc", "command -v cursor-agent || true")
                        .directory(workDir)
                        .redirectErrorStream(true)
                        .start();
                proc.waitFor();
                byte[] out = proc.getInputStream().readAllBytes();
                String resolved = new String(out, StandardCharsets.UTF_8).trim();
                if (!resolved.isEmpty() && new File(resolved).canExecute()) return resolved;
            } catch (Exception ignore) {}
        }
        
        return null;
    }

    /**
     * Shell-quote a string for safe execution
     */
    public static String shQuote(String s) {
        if (s == null) return "''";
        return "'" + s.replace("'", "'\\''") + "'";
    }

    /**
     * Expand ~ to user home directory
     */
    public static String expandHome(String p) {
        if (p == null) return null;
        if (p.equals("~")) return System.getProperty("user.home");
        if (p.startsWith("~" + File.separator)) {
            return p.replaceFirst("~", System.getProperty("user.home"));
        }
        return p;
    }

    /**
     * Execute command safely in terminal widget
     */
    public static void exec(Project project, ShellTerminalWidget widget, String cmd) {
        try {
            widget.executeCommand(cmd);
        } catch (IOException e) {
            notify(project, "启动失败：" + e.getMessage(), NotificationType.ERROR);
        }
    }

    /**
     * Show notification to user
     */
    public static void notify(Project project, String msg, NotificationType type) {
        NotificationGroup group = NotificationGroupManager.getInstance().getNotificationGroup(NOTIF_GROUP_ID);
        Notification notification = (group != null)
                ? group.createNotification(msg, type)
                : new Notification(NOTIF_GROUP_ID, "Cursor CLI Terminal", msg, type);
        Notifications.Bus.notify(notification, project);
    }
}