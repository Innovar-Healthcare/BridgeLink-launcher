package com.innovarhealthcare.launcher;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.Element;

import static java.rmi.server.LogStream.log;

public class MirthJNLPLauncher extends Application {
    private TextField urlField;
    private TextArea logArea;

    public static void main(String[] args) {
        SSLBypass.disableSSLVerification(); // Disable SSL certificate validation
        launch(args);
    }


    private static final String LOG_FILE = "mirth-launcher-debug.log";


    @Override
    public void start(Stage primaryStage) {
        urlField = new TextField("https://localhost:8443/webstart.jnlp");
        Button launchButton = new Button("Launch Mirth");
        logArea = new TextArea();
        logArea.setEditable(false);

        // Clear the log file at the start of the program
        try (PrintWriter out = new PrintWriter(LOG_FILE)) {
            out.print(""); // Clears the file
        } catch (IOException e) {
            System.err.println("ERROR: Could not clear log file - " + e.getMessage());
        }

        launchButton.setOnAction(e -> launchMirthFromURL(urlField.getText()));

        VBox root = new VBox(10, new Label("Enter JNLP URL:"), urlField, launchButton, new Label("Log:"), logArea);
        primaryStage.setScene(new Scene(root, 600, 400));
        primaryStage.setTitle("Mirth JNLP Launcher");
        primaryStage.show();
    }

    private void launchMirthFromURL(String jnlpUrl) {
        log("Fetching JNLP from: " + jnlpUrl);
        try {
            URL url = new URL(jnlpUrl);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(url.openStream());

            List<String> coreJars = new ArrayList<>();
            List<String> extensionJars = new ArrayList<>();
            String mirthVersion = "unknown";

            NodeList jarList = doc.getElementsByTagName("jar");
            if (jarList.getLength() == 0) {
                log("WARNING: No JAR files detected in JNLP!");
            }

            for (int i = 0; i < jarList.getLength(); i++) {
                Element jarElement = (Element) jarList.item(i);
                String jarPath = jarElement.getAttribute("href");
                log("Detected Core JAR: " + jarPath);
                coreJars.add(jarPath);
            }

            NodeList extensionList = doc.getElementsByTagName("extension");
            for (int i = 0; i < extensionList.getLength(); i++) {
                Element extElement = (Element) extensionList.item(i);
                String extJnlpPath = extElement.getAttribute("href");

                log("Found extension JNLP: " + extJnlpPath);
                parseExtensionJnlp(jnlpUrl.substring(0, jnlpUrl.lastIndexOf("/")) + "/" + extJnlpPath, extensionJars);
            }

            NodeList versionNodes = doc.getElementsByTagName("title");
            if (versionNodes.getLength() > 0) {
                mirthVersion = versionNodes.item(0).getTextContent().replaceAll("[^0-9.]", "");
            }

            log("Detected Mirth Version: " + mirthVersion);
            log("Final Core JARs: " + coreJars);

            downloadAndLaunch(jnlpUrl, coreJars, extensionJars, mirthVersion);
        } catch (Exception e) {
            log("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void parseExtensionJnlp(String extJnlpUrl, List<String> extensionJars) {
        log("Fetching Extension JNLP: " + extJnlpUrl);
        try {
            URL url = new URL(extJnlpUrl);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(url.openStream());

            NodeList resourceList = doc.getElementsByTagName("resources");
            if (resourceList.getLength() == 0) {
                log("WARNING: No <resources> tag found in extension JNLP: " + extJnlpUrl);
                return;
            }

            for (int i = 0; i < resourceList.getLength(); i++) {
                Element resourceElement = (Element) resourceList.item(i);
                NodeList jarList = resourceElement.getElementsByTagName("jar");

                if (jarList.getLength() == 0) {
                    log("WARNING: No JARs found in <resources> of extension JNLP: " + extJnlpUrl);
                }

                for (int j = 0; j < jarList.getLength(); j++) {
                    Element jarElement = (Element) jarList.item(j);
                    String jarPath = jarElement.getAttribute("href");

                    log("Found JAR in Extension JNLP: " + jarPath);
                    extensionJars.add(jarPath);
                }
            }

            log("Total JARs from " + extJnlpUrl + ": " + extensionJars);
        } catch (Exception e) {
            log("Error fetching extension JNLP: " + e.getMessage());
        }
    }

    private void downloadAndLaunch(String jnlpUrl, List<String> coreJars, List<String> extensionJars, String mirthVersion) {
        log("Starting download process for Mirth version: " + mirthVersion);

        String baseUrl = jnlpUrl.contains("/webstart") ? jnlpUrl.substring(0, jnlpUrl.indexOf("/webstart") + 9) : jnlpUrl;
        String cacheDir = "mirth-cache/" + mirthVersion;
        String cacheCoreDir = cacheDir + "/core";
        String cacheExtensionsDir = cacheDir + "/extensions";

        // Ensure directories exist
        new File(cacheCoreDir).mkdirs();
        new File(cacheExtensionsDir).mkdirs();

        List<File> localJars = new ArrayList<>();

        // ✅ Fix Core JAR Download Path
        for (String jar : coreJars) {
            String correctedJarUrl = baseUrl + "/client-lib/" + new File(jar).getName(); // Ensure the correct base path
            File localFile = new File(cacheCoreDir, new File(jar).getName());

            log("Downloading Core JAR: " + correctedJarUrl);
            if (!downloadFile(correctedJarUrl, localFile, null)) {
                log("WARNING: Failed to download core JAR: " + correctedJarUrl);
            }
            localJars.add(localFile);
        }

        // ✅ Fix Extension JAR Download Path
        for (String jar : extensionJars) {
            String[] jarParts = jar.split("/");
            if (jarParts.length < 2) {
                log("WARNING: Skipping invalid extension JAR path: " + jar);
                continue;
            }
            String correctedJarUrl = baseUrl + "/extensions/libs/" + jarParts[1] + "/" + jarParts[jarParts.length - 1];
            File localFile = new File(cacheExtensionsDir, new File(jarParts[jarParts.length - 1]).getName());

            log("Downloading Extension JAR: " + correctedJarUrl);
            if (!downloadFile(correctedJarUrl, localFile, null)) {
                log("WARNING: Missing extension JAR: " + correctedJarUrl);
            }
            localJars.add(localFile);
        }

        try {
            launchMirth("com.mirth.connect.client.ui.Mirth", localJars);
        } catch (Exception e) {
            log("ERROR: Failed to launch Mirth - " + e.getMessage());
        }
    }

    private void launchMirth(String mainClass, List<File> jarFiles) throws Exception {
        log("Launching Mirth Client...");

        List<URL> urls = new ArrayList<>();
        for (File jar : jarFiles) {
            urls.add(jar.toURI().toURL());
        }

        URLClassLoader classLoader = new URLClassLoader(urls.toArray(new URL[0]), ClassLoader.getSystemClassLoader());
        Thread.currentThread().setContextClassLoader(classLoader);

        Class<?> cls = classLoader.loadClass(mainClass);
        Method mainMethod = cls.getMethod("main", String[].class);
        String[] mirthArgs = {"https://localhost:8443", "4.5.2"};
        mainMethod.invoke(null, (Object) mirthArgs);
    }

    private void log(String message) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String logMessage = "[" + timestamp + "] " + message;

        // Print to console
        System.out.println(logMessage);

        // Append to log file
        try (PrintWriter out = new PrintWriter(new FileWriter(LOG_FILE, true))) {
            out.println(logMessage);
        } catch (IOException e) {
            System.err.println("ERROR: Could not write to log file - " + e.getMessage());
        }
    }

    private boolean downloadFile(String urlStr, File destination, String expectedSha256) {
        try {
            // Skip download if the file already exists and matches the hash
            if (destination.exists() && expectedSha256 != null) {
                String actualSha256 = calculateSha256(destination);
                if (expectedSha256.equalsIgnoreCase(actualSha256)) {
                    log("Skipping already downloaded file: " + destination.getName());
                    return true;
                }
            }

            // Download the file
            log("Downloading: " + urlStr);
            URL url = new URL(urlStr);
            try (InputStream in = url.openStream();
                 FileOutputStream out = new FileOutputStream(destination)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }

            // Verify hash after download
            if (expectedSha256 != null) {
                String actualSha256 = calculateSha256(destination);
                if (!expectedSha256.equalsIgnoreCase(actualSha256)) {
                    log("ERROR: SHA-256 mismatch for " + destination.getName());
                    destination.delete();
                    return false;
                }
            }

            log("Saved: " + destination.getAbsolutePath());
            return true;
        } catch (Exception e) {
            log("Error downloading file: " + urlStr + " - " + e.getMessage());
            return false;
        }
    }

    private String calculateSha256(File file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream fis = new FileInputStream(file);
             DigestInputStream dis = new DigestInputStream(fis, digest)) {
            byte[] buffer = new byte[4096];
            while (dis.read(buffer) != -1) {}
        }
        byte[] hashBytes = digest.digest();
        StringBuilder hexString = new StringBuilder();
        for (byte b : hashBytes) {
            hexString.append(String.format("%02x", b));
        }
        return hexString.toString();
    }
}
