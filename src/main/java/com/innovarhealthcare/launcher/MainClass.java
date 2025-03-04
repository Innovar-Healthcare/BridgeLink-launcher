/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.innovarhealthcare.launcher;

/**
 *
 * @author thait
 */
public class MainClass {
    public static void main(String[] args) {
        SSLBypass.disableSSLVerification(); 
         
        MirthJNLP mirth = new MirthJNLP();
        String url = "https://192.168.1.100:8443/webstart.jnlp";
        mirth.launchMirthFromURL(url);
    }
}
