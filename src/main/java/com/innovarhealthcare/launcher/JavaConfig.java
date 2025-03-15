package com.innovarhealthcare.launcher;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;

public class JavaConfig {
    private String maxHeapSize;
    private String javaHome;

    public JavaConfig(String maxHeapSize, String javaHome) {
        this.maxHeapSize = maxHeapSize;
        this.javaHome = javaHome;
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

    public String getMaxHeapSizeBuilder(){
        return "-Xmx" + maxHeapSize;
    }

    public String getJavaHomeBuilder(){
        String path = "-Djava.library.path=jre";
        if(javaHome.equals("Java 17")){
            if(SystemUtils.IS_OS_WINDOWS){
                if(!StringUtils.equalsIgnoreCase(SystemUtils.OS_ARCH, "x64")){
//                    path = "-Djava.library.path=openjfx\\windows-x64\\17";
                    path = "-Djava.library.path=C:\\Program Files\\Mirth Connect Administrator Launcher\\mcadministrator\\windows-x64\\openjfx\\11";
                }
            } else if (SystemUtils.IS_OS_MAC){
                path = "-Djava.library.path=openjfx\\macos\\17";
            }
        }

        return path;
    }
}
