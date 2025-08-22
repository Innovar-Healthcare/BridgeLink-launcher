package com.innovarhealthcare.launcher;

import org.apache.commons.lang3.SystemUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    public String getJavaHomeBuilder(){
        String path = "jre8/bin/java";
        if(javaHome.equals("Java 17")){
            path = "jre/bin/java";
            if (SystemUtils.IS_OS_MAC){
                path = "../jre.bundle/Contents/Home/bin/java";
            }
        }

        return path;
    }

}
