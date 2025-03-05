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
        
        String host = "https://192.168.1.100:8443";
        
        MirthJNLP mirth = new MirthJNLP(host);
        mirth.launchMirth();
        
//        String url = "https://192.168.1.100:8443/webstart.jnlp";
//        String url = "https://54.175.253.162:8443/webstart.jnlp";
        
//        String url = "https://ngconnect-test.hibridgehie.org:8443/webstart.jnlp";
        
    }
}