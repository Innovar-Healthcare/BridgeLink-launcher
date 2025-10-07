/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.innovarhealthcare.launcher;

/**
 * Class to hold JAR information including href and SHA-256 hash
 * 
 * @author thait
 */
public class JarInfo {
    private String href;
    private String sha256;

    public JarInfo(String href, String sha256) {
        this.href = href;
        this.sha256 = sha256;
    }

    public String getHref() {
        return href;
    }

    public void setHref(String href) {
        this.href = href;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    @Override
    public String toString() {
        return "JarInfo{" +
                "href='" + href + '\'' +
                ", sha256='" + sha256 + '\'' +
                '}';
    }
}