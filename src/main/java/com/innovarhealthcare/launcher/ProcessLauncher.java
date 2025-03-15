package com.innovarhealthcare.launcher;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class ProcessLauncher {
    public void launch(JavaConfig javaConfig, CodeBase codeBase) throws IOException{

        ProcessBuilder consoleBuilder = new ProcessBuilder(
                "java", "-cp", "lib/java-console.jar", "com.innovarhealthcare.launcher.JavaConsoleDialog"
        );
        Process consoleProcess = consoleBuilder.start();

        String classpathString = String.join(File.pathSeparator, codeBase.getClasspath());

        String mainClass = codeBase.getMainClass();

        List<String> command = new ArrayList<>();
        command.add("java");
        command.add(javaConfig.getMaxHeapSizeBuilder());
        command.add(javaConfig.getJavaHomeBuilder());
        command.add("-cp");
        command.add(classpathString);
        command.add(mainClass);
        command.add(codeBase.getHost());

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.inheritIO(); // Redirect output to console

        Process targetProcess = processBuilder.start();

        // Pipe the logs from TargetApp to ConsoleApp
//        OutputStream consoleInput = consoleProcess.getOutputStream();
        new Thread(() -> {
            try (InputStream inputStream = targetProcess.getInputStream();
                 OutputStream outputStream = consoleProcess.getOutputStream()) {

                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

//    public void start(){
//        try {
//            // Path to the Java executable
//            String javaHome = System.getProperty("java.home");
//            String javaExec = javaHome + "/bin/java";
//
//            // Define the classpath (adjust to include your compiled classes or jars)
//            String classpath = "path/to/your/classes/or/jars";
//
//            // Command to start the Console Application
//            ProcessBuilder consoleBuilder = new ProcessBuilder(
//                    javaExec, "-cp", classpath, "ConsoleApp"
//            );
//            Process consoleProcess = consoleBuilder.start();
//
//            // Command to start the Target Application
//            ProcessBuilder targetBuilder = new ProcessBuilder(
//                    javaExec, "-cp", classpath, "TargetApp"
//            );
//            Process targetProcess = targetBuilder.start();
//
//            // Pipe the logs from TargetApp to ConsoleApp
//            OutputStream consoleInput = consoleProcess.getOutputStream();
//            new Thread(() -> {
//                try (InputStream inputStream = targetProcess.getInputStream();
//                     OutputStream outputStream = consoleProcess.getOutputStream()) {
//
//                    byte[] buffer = new byte[8192];
//                    int bytesRead;
//                    while ((bytesRead = inputStream.read(buffer)) != -1) {
//                        outputStream.write(buffer, 0, bytesRead);
//                    }
//                }catch (Exception e) {
//                    e.printStackTrace();
//                }
//            }).start();
//
//            System.out.println("Both applications have been started.");
//
//            // Optionally, wait for both processes to complete
//            int targetExitCode = targetProcess.waitFor();
//            consoleInput.close(); // Close the console input after the target app ends
//            int consoleExitCode = consoleProcess.waitFor();
//
//            System.out.println("TargetApp exited with code: " + targetExitCode);
//            System.out.println("ConsoleApp exited with code: " + consoleExitCode);
//
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
}
