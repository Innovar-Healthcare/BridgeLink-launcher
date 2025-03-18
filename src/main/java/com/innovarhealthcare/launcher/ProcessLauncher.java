package com.innovarhealthcare.launcher;

import org.apache.commons.lang3.SystemUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class ProcessLauncher {
    public void launch(JavaConfig javaConfig, CodeBase codeBase, boolean isShowConsole) throws Exception{
        List<String> command = new ArrayList<>();
        command.add(javaConfig.getJavaHomeBuilder());
        command.add(javaConfig.getMaxHeapSizeBuilder());

        if (SystemUtils.IS_OS_MAC){
            if("Java 17".equals(javaConfig.getJavaHome())){
                command.add("--add-opens=java.desktop/com.apple.eawt=ALL-UNNAMED");
            }
        }

        if("Java 17".equals(javaConfig.getJavaHome())){
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
        }

        command.add("-cp");
        command.add(String.join(File.pathSeparator, codeBase.getClasspath()));
        command.add(codeBase.getMainClass());
        command.add(codeBase.getHost());

        ProcessBuilder targetPb = new ProcessBuilder(command);
        targetPb.redirectErrorStream(true);

        Process targetProcess;
        if(isShowConsole) {
            ProcessBuilder consolePb = new ProcessBuilder(
                    javaConfig.getJavaHomeBuilder(),
                    "-Xmx256m",
                    "-cp",
                    "lib/java-console.jar",
                    "com.innovarhealthcare.launcher.JavaConsoleDialog"
            );

            // Start Console Process
            Process consoleProcess = consolePb.start();

            // Verify consoleProcess launched
            if (!consoleProcess.isAlive()) {
                throw new IOException("Console process failed to start");
            }

            // Start Target Process
            targetProcess = targetPb.start();
            // Verify targetProcess launched
            if (!targetProcess.isAlive()) {
                throw new IOException("Target process failed to start");
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
                }
            });
            pipeThread.start();
        } else {
            targetProcess = targetPb.start();

            // Verify targetProcess launched
            if (!targetProcess.isAlive()) {
                throw new IOException("Target process failed to start");
            }
        }
    }
}
