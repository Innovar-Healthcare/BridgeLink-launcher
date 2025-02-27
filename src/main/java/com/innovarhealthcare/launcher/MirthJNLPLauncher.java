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
import java.util.ArrayList;
import java.util.List;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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
            List<String> extensionJnlps = new ArrayList<>();

            NodeList jarList = doc.getElementsByTagName("jar");
            for (int i = 0; i < jarList.getLength(); i++) {
                Element jarElement = (Element) jarList.item(i);
                String jarPath = jarElement.getAttribute("href");

                if (jarPath.contains("extensions")) {
                    extensionJars.add(jarPath);
                } else {
                    coreJars.add(jarPath);
                }
            }

            // Detect and log extension JNLPs
            NodeList extensionList = doc.getElementsByTagName("extension");
            for (int i = 0; i < extensionList.getLength(); i++) {
                Element extElement = (Element) extensionList.item(i);
                String extJnlpPath = extElement.getAttribute("href");
                extensionJnlps.add(extJnlpPath);
                log("Detected extension JNLP: " + extJnlpPath);
            }

            log("Core JARs: " + coreJars);
            log("Extension JARs: " + extensionJars);
            log("Detected Extension JNLPs: " + extensionJnlps);

            // Process extension JNLP files BEFORE downloading JARs
            for (String extJnlp : extensionJnlps) {
                String extJnlpUrl = jnlpUrl.substring(0, jnlpUrl.lastIndexOf("/")) + "/" + extJnlp;
                log("Processing extension JNLP: " + extJnlpUrl);
                parseExtensionJnlp(extJnlpUrl, extensionJars);
            }

            downloadAndLaunch(jnlpUrl, coreJars, extensionJars);
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

    private void downloadAndLaunch(String jnlpUrl, List<String> coreJars, List<String> extensionJars) {
        log("Starting download process...");

        // Ensure /webstart is always part of the base URL
        String baseUrl = jnlpUrl.contains("/webstart") ? jnlpUrl.substring(0, jnlpUrl.indexOf("/webstart") + 9) : jnlpUrl;
        String extensionsBaseUrl = baseUrl + "/extensions/libs/"; // Corrected path
        String cacheCoreDir = "mirth-cache/core";
        String cacheExtensionsDir = "mirth-cache/extensions";

        new File(cacheCoreDir).mkdirs();
        new File(cacheExtensionsDir).mkdirs();

        List<File> localJars = new ArrayList<>();

        // Download Core JARs
        for (String jar : coreJars) {
            String jarUrl = baseUrl + "/" + jar;
            File localFile = new File(cacheCoreDir, new File(jar).getName());

            log("Downloading Core JAR: " + jarUrl);
            if (!downloadFile(jarUrl, localFile)) {
                log("WARNING: Missing core JAR: " + jarUrl);
            }
            localJars.add(localFile);
        }

        // Download Extension JARs with Corrected URL Structure
        for (String jar : extensionJars) {
            String[] jarParts = jar.split("/"); // Extracts extension name
            if (jarParts.length < 2) {
                log("WARNING: Skipping invalid extension JAR path: " + jar);
                continue;
            }
            String extensionName = jarParts[1]; // Extracts 'globalmapviewer' from 'libs/globalmapviewer/globalmapviewer-client.jar'
            String correctedJarUrl = extensionsBaseUrl + extensionName + "/" + jarParts[jarParts.length - 1];

            File localFile = new File(cacheExtensionsDir, new File(jarParts[jarParts.length - 1]).getName());

            log("Downloading Extension JAR: " + correctedJarUrl);
            if (!downloadFile(correctedJarUrl, localFile)) {
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

        URL[] urls = jarFiles.stream().map(file -> {
            try {
                return file.toURI().toURL();
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }).toArray(URL[]::new);

        URLClassLoader classLoader = new URLClassLoader(urls, ClassLoader.getSystemClassLoader());
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

    private boolean downloadFile(String urlStr, File destination) {
        try {
            log("Attempting to download: " + urlStr);
            URL url = new URL(urlStr);

            try (InputStream in = url.openStream(); FileOutputStream out = new FileOutputStream(destination)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }

            log("Saved: " + destination.getAbsolutePath());
            return true;
        } catch (FileNotFoundException e) {
            log("WARNING: File not found: " + urlStr + " - Skipping.");
            return false;
        } catch (IOException e) {
            log("ERROR: Failed to download " + urlStr + " - " + e.getMessage());
            return false;
        }
    }
}
