package com.innovarhealthcare.launcher;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.FileWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.List;

public class ProcessLauncher {
    private static final String LOG_FILE = "process-launcher-debug.log";
    private static final boolean DEBUG = false;
    
    // Overloaded method for backward compatibility
    public void launch(JavaConfig javaConfig, Credential credential, CodeBase codeBase, boolean isShowConsole) throws Exception {
        launch(javaConfig, credential, codeBase, isShowConsole, null, null);
    }
    
    public void launch(JavaConfig javaConfig, Credential credential, CodeBase codeBase, boolean isShowConsole, String iconPath) throws Exception {
        launch(javaConfig, credential, codeBase, isShowConsole, iconPath, null);
    }
    
    public void launch(JavaConfig javaConfig, Credential credential, CodeBase codeBase, boolean isShowConsole, String iconPath, String connectionName) throws Exception {
        log("🚀 ProcessLauncher.launch() started");
        log("📋 Parameters - iconPath: '" + iconPath + "', connectionName: '" + connectionName + "', isShowConsole: " + isShowConsole);
        
        log("🖥️ Operating System Detection:");
        log("   - IS_OS_MAC: " + SystemUtils.IS_OS_MAC);
        log("   - IS_OS_WINDOWS: " + SystemUtils.IS_OS_WINDOWS);
        log("   - IS_OS_LINUX: " + SystemUtils.IS_OS_LINUX);
        log("   - OS Name: " + System.getProperty("os.name"));
        log("   - OS Version: " + System.getProperty("os.version"));
        
        List<String> command = new ArrayList<>();
        command.add(javaConfig.getJavaHomeBuilder());
        command.add(javaConfig.getMaxHeapSizeBuilder());
        if(StringUtils.isNotBlank(javaConfig.getJvmOptions()))
            command.addAll(javaConfig.getJvmOptionsList());

        log("🔧 Building platform-specific commands...");

        if (SystemUtils.IS_OS_MAC){
            log("🍎 Configuring macOS dock settings...");
            // Use dynamic icon path if provided, otherwise fall back to default
            String dockIcon = (iconPath != null && !iconPath.trim().isEmpty()) ? iconPath : "icon.png";
            log("   - Dock icon path: '" + dockIcon + "'");
            command.add("-Xdock:icon=" + dockIcon);
            
            // Set the dock tooltip/hover name - this is what shows when you hover over the dock icon
            String dockName = (connectionName != null && !connectionName.trim().isEmpty()) 
                ? connectionName + " - BridgeLink Administrator" 
                : "BridgeLink Administrator";
            log("   - Setting dock name to: '" + dockName + "'");
            command.add("-Xdock:name=" + dockName);
            
            // Additional properties for application identification
            log("   - Adding additional macOS properties...");
            command.add("-Dcom.apple.mrj.application.apple.menu.about.name=BridgeLink Administrator");
            command.add("-Dapple.awt.application.name=BridgeLink Administrator");
            
            // This property is crucial for the hover tooltip to work properly
            command.add("-Djava.awt.headless=false");
            command.add("-Dapple.laf.useScreenMenuBar=true");
            
            // Set application bundle identifier for better macOS integration
            command.add("-Dcom.apple.mrj.application.bundle.identifier=com.innovarhealthcare.bridgelink");
            
            // Ensure the application appears properly in Activity Monitor and dock
            command.add("-XX:+UnlockExperimentalVMOptions");
            command.add("-XX:+UseG1GC");
            
            log("   - macOS dock configuration completed");
            
            if("Java 17".equals(javaConfig.getJavaHome())){
                log("   - Adding Java 17 specific options for macOS");
                command.add("--add-opens=java.desktop/com.apple.eawt=ALL-UNNAMED");
            }
        } else if (SystemUtils.IS_OS_WINDOWS) {
            log("🪟 Configuring Windows taskbar settings...");
            // Simple icon path configuration for Windows
            if (iconPath != null && !iconPath.trim().isEmpty()) {
                log("   - Using custom icon: '" + iconPath + "'");
                // Convert the icon path to a Windows-friendly format
                String windowsIconPath = iconPath.replace('/', '\\');
                command.add("-Dbridgelink.icon.path=" + windowsIconPath);
            } else {
                log("   - No custom icon specified");
                command.add("-Dbridgelink.icon.path=");
            }
            
            log("   - Windows configuration completed");
        } else if (SystemUtils.IS_OS_LINUX) {
            log("🐧 Configuring Linux taskbar/dock settings...");
            // Linux taskbar/dock tooltip configuration
            if (iconPath != null && !iconPath.trim().isEmpty()) {
                log("   - Using custom icon: '" + iconPath + "'");
                command.add("-Dapp.icon.path=" + iconPath);
                command.add("-Dawt.useSystemAAFontSettings=on");
                command.add("-Dswing.aatext=true");
            } else {
                log("   - Using default icon: 'icon.png'");
                command.add("-Dapp.icon.path=icon.png");
            }
            // Set application name for window manager and taskbar tooltip
            log("   - Setting Linux app name and tooltip");
            command.add("-Dapp.name=BridgeLink Administrator");
            command.add("-Dapp.tooltip=BridgeLink Administrator");
            command.add("-Djava.awt.Window.locationByPlatform=true");
            command.add("-Djava.awt.headless=false");
            // Set WM_CLASS for better window manager recognition and tooltip
            command.add("-Dawt.toolkit=sun.awt.X11.XToolkit");
            command.add("-Dcom.sun.java.swing.plaf.gtk.UIManager=com.sun.java.swing.plaf.gtk.GTKLookAndFeel");
            log("   - Linux taskbar/dock configuration completed");
        } else {
            log("❓ Unknown operating system - no platform-specific configuration applied");
        }

        log("☕ Adding Java 17 specific options (if applicable)...");
        if("Java 17".equals(javaConfig.getJavaHome())){
            log("   - Java version detected as Java 17, adding module options");
            command.add("--add-modules=java.sql.rowset");
            command.add("--add-exports=java.base/com.sun.crypto.provider=ALL-UNNAMED");
            command.add("--add-exports=java.base/sun.security.provider=ALL-UNNAMED");
            command.add("--add-opens=java.base/java.lang=ALL-UNNAMED");
            command.add("--add-opens=java.base/java.lang.reflect=ALL-UNNAMED");
            command.add("--add-opens=java.base/java.math=ALL-UNNAMED");
            command.add("--add-opens=java.base/java.net=ALL-UNNAMED");
            command.add("--add-opens=java.base/java.security=ALL-UNNAMED");
            command.add("--add-opens=java.base/java.security.cert=ALL-UNNAMED");
            command.add("--add-opens=java.base/java.text=ALL-UNNAMED");
            command.add("--add-opens=java.base/java.util=ALL-UNNAMED");
            command.add("--add-opens=java.base/sun.security.pkcs=ALL-UNNAMED");
            command.add("--add-opens=java.base/sun.security.rsa=ALL-UNNAMED");
            command.add("--add-opens=java.base/sun.security.x509=ALL-UNNAMED");
            command.add("--add-opens=java.desktop/java.awt=ALL-UNNAMED");
            command.add("--add-opens=java.desktop/java.awt.color=ALL-UNNAMED");
            command.add("--add-opens=java.desktop/java.awt.font=ALL-UNNAMED");
            command.add("--add-opens=java.desktop/javax.swing=ALL-UNNAMED");
            command.add("--add-opens=java.xml/com.sun.org.apache.xalan.internal.xsltc.trax=ALL-UNNAMED");
        } else {
            log("   - Java version is not Java 17, skipping module options");
        }

        log("📦 Adding application classpath and main class...");
        log("📝 Classpath contains " + codeBase.getClasspath().size() + " JAR files:");
        for (int i = 0; i < codeBase.getClasspath().size(); i++) {
            String jarPath = codeBase.getClasspath().get(i);
            File jarFile = new File(jarPath);
            log("   [" + (i + 1) + "] " + jarPath + " (exists: " + jarFile.exists() + ", size: " + jarFile.length() + " bytes)");
        }
        command.add("-cp");
        String fullClasspath = String.join(File.pathSeparator, codeBase.getClasspath());
        log("📝 Full classpath: " + fullClasspath);
        command.add(fullClasspath);
        
        // Use the standard main class for all platforms
        command.add(codeBase.getMainClass());
        
        command.add(codeBase.getHost());
        command.add(codeBase.getVersion());

        log("🔐 Adding credentials (if provided)...");
        if(StringUtils.isNotBlank(credential.getUsername())){
            log("   - Username provided: '" + credential.getUsername() + "'");
            command.add(credential.getUsername());
        } else {
            log("   - No username provided");
        }

        if(StringUtils.isNotBlank(credential.getPassword())){
            log("   - Password provided: [REDACTED - length: " + credential.getPassword().length() + "]");
            command.add(credential.getPassword());
        } else {
            log("   - No password provided");
        }

        log("🔍 FINAL COMMAND ANALYSIS:");
        log("📏 Total command parts: " + command.size());
        for (int i = 0; i < command.size(); i++) {
            String part = command.get(i);
            if (part.startsWith("-Xdock:") || part.startsWith("-Dapp.") || part.startsWith("-Dapple.awt.") || part.startsWith("-Dcom.apple.")) {
                log("   [" + i + "] 🎯 " + part + " ← IMPORTANT FOR DOCK/TASKBAR");
            } else {
                log("   [" + i + "] " + part);
            }
        }

        ProcessBuilder targetPb = new ProcessBuilder(command);
        targetPb.redirectErrorStream(true);

        log("🚀 Starting process...");
        // Debug: Print the command being executed (useful for troubleshooting)
        System.out.println("DEBUG: Executing command: " + String.join(" ", command));

        Process targetProcess;
        if(isShowConsole) {
            log("🖥️ Starting with console enabled...");
            
            // Build console command with same platform-specific icon settings as main process
            List<String> consoleCommand = new ArrayList<>();
            consoleCommand.add(javaConfig.getJavaHomeBuilder());
            consoleCommand.add("-Xmx256m");
            
            // Add platform-specific icon settings for console process
            if (SystemUtils.IS_OS_MAC && iconPath != null && !iconPath.trim().isEmpty()) {
                log("   - Adding macOS dock icon for console: '" + iconPath + "'");
                consoleCommand.add("-Xdock:icon=" + iconPath);
                
                String consoleDockName = (connectionName != null && !connectionName.trim().isEmpty()) 
                    ? connectionName + " - Console" 
                    : "BridgeLink Console";
                consoleCommand.add("-Xdock:name=" + consoleDockName);
                consoleCommand.add("-Dcom.apple.mrj.application.apple.menu.about.name=" + consoleDockName);
                consoleCommand.add("-Dapple.awt.application.name=" + consoleDockName);
                consoleCommand.add("-Djava.awt.headless=false");
            } else if (SystemUtils.IS_OS_WINDOWS && iconPath != null && !iconPath.trim().isEmpty()) {
                log("   - Adding Windows taskbar icon for console: '" + iconPath + "'");
                String windowsIconPath = iconPath.replace('/', '\\');
                consoleCommand.add("-Dbridgelink.icon.path=" + windowsIconPath);
            } else if (SystemUtils.IS_OS_LINUX && iconPath != null && !iconPath.trim().isEmpty()) {
                log("   - Adding Linux taskbar icon for console: '" + iconPath + "'");
                consoleCommand.add("-Dapp.icon.path=" + iconPath);
                
                String consoleAppName = (connectionName != null && !connectionName.trim().isEmpty()) 
                    ? connectionName + " - Console" 
                    : "BridgeLink Console";
                consoleCommand.add("-Dapp.name=" + consoleAppName);
                consoleCommand.add("-Dapp.tooltip=" + consoleAppName);
                consoleCommand.add("-Djava.awt.headless=false");
            }
            
            consoleCommand.add("-cp");
            consoleCommand.add("lib/java-console.jar");
            consoleCommand.add("com.innovarhealthcare.launcher.JavaConsoleDialog");
            
            ProcessBuilder consolePb = new ProcessBuilder(consoleCommand);

            // Start Console Process
            Process consoleProcess = consolePb.start();

            // Verify consoleProcess launched
            if (!consoleProcess.isAlive()) {
                log("❌ Console process failed to start");
                throw new IOException("Console process failed to start");
            } else {
                log("✅ Console process started successfully");
            }

            // Start Target Process
            targetProcess = targetPb.start();
            // Verify targetProcess launched
            if (!targetProcess.isAlive()) {
                log("❌ Target process failed to start");
                throw new IOException("Target process failed to start");
            } else {
                log("✅ Target process started successfully with PID: " + getProcessId(targetProcess));
            }

            // Pipe Target Process output to Console Process input in real-time
            Thread pipeThread = new Thread(() -> {
                try (OutputStream consoleInput = consoleProcess.getOutputStream();
                     InputStream targetOutput = targetProcess.getInputStream()) {
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = targetOutput.read(buffer)) != -1) {
                        consoleInput.write(buffer, 0, bytesRead);
                        consoleInput.flush(); // Ensure immediate delivery
                    }
                    consoleInput.flush(); // Final flush
                } catch (IOException e) {
                    log("❌ Error in pipe thread: " + e.getMessage());
                }
            });
            pipeThread.start();
        } else {
            log("🖥️ Starting without console...");
            targetProcess = targetPb.start();

            // Verify targetProcess launched
            if (!targetProcess.isAlive()) {
                log("❌ Target process failed to start");
                throw new IOException("Target process failed to start");
            } else {
                log("✅ Target process started successfully with PID: " + getProcessId(targetProcess));
            }
        }
        
        log("🎉 ProcessLauncher.launch() completed successfully!");
        log("⏰ Check the dock/taskbar now to see if the tooltip shows 'BridgeLink Administrator'");
    }
    
    private String getProcessId(Process process) {
        try {
            // Try to get PID using reflection for Java 9+
            java.lang.reflect.Method pidMethod = process.getClass().getMethod("pid");
            return String.valueOf(pidMethod.invoke(process));
        } catch (Exception e) {
            // Fallback for Java 8 and earlier
            String processString = process.toString();
            if (processString.contains("pid=")) {
                return processString.replaceAll(".*pid=(\\d+).*", "$1");
            }
            return "unknown";
        }
    }
    
    private void log(String message) {
        if(DEBUG){
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String logMessage = "[ProcessLauncher " + timestamp + "] " + message;

            // Print to console
            System.out.println(logMessage);

            // Append to log file
            try (PrintWriter out = new PrintWriter(new FileWriter(LOG_FILE, true))) {
                out.println(logMessage);
            } catch (IOException e) {
                System.err.println("ERROR: Could not write to log file - " + e.getMessage());
            }
        }
    }
}
