package com.innovarhealthcare.launcher;

import com.innovarhealthcare.launcher.interfaces.Progress;
import javafx.application.Platform;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ProcessLauncher {
    public void launch(JavaConfig javaConfig, CodeBase codeBase, boolean isShowConsole) throws Exception{
        List<String> command = new ArrayList<>();
        command.add("java");
        command.add(javaConfig.getMaxHeapSizeBuilder());
        command.add(javaConfig.getJavaHomeBuilder());
        command.add("-cp");
        command.add(String.join(File.pathSeparator, codeBase.getClasspath()));
        command.add(codeBase.getMainClass());
        command.add(codeBase.getHost());

        ProcessBuilder targetPb = new ProcessBuilder(command);
        targetPb.redirectErrorStream(true);

        Process targetProcess;
        if(isShowConsole) {
            ProcessBuilder consolePb = new ProcessBuilder(
                    "java",
                    javaConfig.getMaxHeapSizeBuilder(),
                    javaConfig.getJavaHomeBuilder(),
                    "-cp", "lib/java-console.jar",
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
