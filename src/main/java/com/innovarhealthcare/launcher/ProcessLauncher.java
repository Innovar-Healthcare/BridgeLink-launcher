package com.innovarhealthcare.launcher;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ProcessLauncher {
    public void launch(JavaConfig javaConfig, CodeBase codeBase) throws IOException{

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

        Process process = processBuilder.start();

    }
}
