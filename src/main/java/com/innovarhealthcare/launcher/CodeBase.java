package com.innovarhealthcare.launcher;

import java.util.List;

public class CodeBase {
    private List<String> classpath;
    private String mainClass;
    private String host;

    public CodeBase(List<String> classpath, String mainClass, String host) {
        this.classpath = classpath;
        this.mainClass = mainClass;
        this.host = host;
    }

    public List<String> getClasspath() {
        return classpath;
    }

    public void setClasspath(List<String> classpath) {
        this.classpath = classpath;
    }

    public String getMainClass() {
        return mainClass;
    }

    public void setMainClass(String mainClass) {
        this.mainClass = mainClass;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }
}
