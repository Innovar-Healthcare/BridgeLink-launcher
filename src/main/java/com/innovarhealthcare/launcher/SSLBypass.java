package com.innovarhealthcare.launcher;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;

public class SSLBypass {
    public static void disableSSLVerification() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        }

                        public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        }

                        public X509Certificate[] getAcceptedIssuers() {
                            return null;
                        }
                    }
            };

            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

            // Disable hostname verification
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);

            System.out.println("SSL verification disabled. Trusting all certificates.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}