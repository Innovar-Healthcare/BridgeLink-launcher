/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.innovarhealthcare.launcher;

import java.util.Map;

/**
 *
 * @author thait
 */
public class ExtensionInfo {
    private String name;
    private Map<String, String> mapJars;

    public ExtensionInfo() {
    }

    public ExtensionInfo(String name, Map<String, String> mapJars) {
        this.name = name;
        this.mapJars = mapJars;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, String> getMapJars() {
        return mapJars;
    }

    public void setMapJars(Map<String, String> mapJars) {
        this.mapJars = mapJars;
    }
    
    
}
