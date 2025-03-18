package com.innovarhealthcare.launcher;

public class BundledJava {
    private String version;
    public BundledJava(String path, String version) { this.version = version; }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    @Override public String toString() { return version; }
}
