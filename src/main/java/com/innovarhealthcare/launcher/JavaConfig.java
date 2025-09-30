package com.innovarhealthcare.launcher;

import org.apache.commons.lang3.SystemUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;


public class JavaConfig {
    private String maxHeapSize;
    private String javaHome;
    private String jvmOptions;

    public JavaConfig(String maxHeapSize, String javaHome, String jvmOptions) {
        this.maxHeapSize = maxHeapSize;
        this.javaHome = javaHome;
        this.jvmOptions = jvmOptions;
    }

    public String getMaxHeapSize() {
        return maxHeapSize;
    }

    public void setMaxHeapSize(String maxHeapSize) {
        this.maxHeapSize = maxHeapSize;
    }

    public String getJavaHome() {
        return javaHome;
    }

    public void setJavaHome(String javaHome) {
        this.javaHome = javaHome;
    }

    public String getJvmOptions() { return jvmOptions;  }

    public void setJvmOptions(String jvmOptions) { this.jvmOptions = jvmOptions; }

    public List<String> getJvmOptionsList() {
        List<String> result = new ArrayList<>();
        Matcher matcher = Pattern.compile("\"([^\"]*)\"|(\\S+)").matcher(getJvmOptions());
        while (matcher.find()) {
            if (matcher.group(1) != null) {
                result.add(matcher.group(1)); // Content with spaces in ""
            } else {
                result.add(matcher.group(2)); // Just string without spaces
            }
        }
        return result;
    }


    public String getMaxHeapSizeBuilder(){
        return "-Xmx" + maxHeapSize;
    }

    public String getJavaHomeBuilder() {
        final boolean win = SystemUtils.IS_OS_WINDOWS;
        final boolean mac = SystemUtils.IS_OS_MAC;
        final String exe = win ? "java.exe" : "java";

        // Choose the expected bundled layout
        Path candidate;
        if ("Java 17".equals(javaHome)) {
            if (mac) {
                // Relative to Contents/Resources/app → ../jre.bundle/Contents/Home/bin/java
                candidate = Paths.get("..", "jre.bundle", "Contents", "Home", "bin", exe);
            } else {
                // Relative jre for other platforms
                candidate = Paths.get("jre", "bin", exe);
            }
        } else {
            // Legacy JRE 8 relative path
            candidate = Paths.get("jre8", "bin", exe);
        }

        // Prefer the chosen candidate if present and executable
        if (Files.isExecutable(candidate)) {
            return candidate.toString();
        }

        // Fallback 1: try "jre/bin/java" relative to app
        Path alt1 = Paths.get("jre", "bin", exe);
        if (Files.isExecutable(alt1)) {
            return alt1.toString();
        }

        // Fallback 2: JAVA_HOME if set
        String javaHomeEnv = System.getenv("JAVA_HOME");
        if (javaHomeEnv != null && !javaHomeEnv.isEmpty()) {
            Path alt2 = Paths.get(javaHomeEnv, "bin", exe);
            if (Files.isExecutable(alt2)) {
                return alt2.toString();
            }
        }

        // Fallback 3: rely on PATH
        return exe;
    }

}
