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
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

public class MirthJNLPLauncher extends Application {
    private TextField urlField;
    private TextArea logArea;

    public static void main(String[] args) {
        SSLBypass.disableSSLVerification(); // Disable SSL certificate validation
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        urlField = new TextField("https://localhost:8443/webstart.jnlp");
        Button launchButton = new Button("Launch Mirth");
        logArea = new TextArea();
        logArea.setEditable(false);

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

            NodeList jarList = doc.getElementsByTagName("jar");
            List<String> jarFiles = new ArrayList<>();
            for (int i = 0; i < jarList.getLength(); i++) {
                jarFiles.add(((Element) jarList.item(i)).getAttribute("href"));
            }

            Element appDesc = (Element) doc.getElementsByTagName("application-desc").item(0);
            String mainClass = appDesc.getAttribute("main-class");

            log("Main Class: " + mainClass);
            log("JARs: " + jarFiles);

            downloadAndLaunch(jnlpUrl, mainClass, jarFiles);
        } catch (Exception e) {
            log("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void downloadAndLaunch(String jnlpUrl, String mainClass, List<String> jarFiles) {
        try {
            String baseUrl = jnlpUrl.substring(0, jnlpUrl.lastIndexOf("/"));
            String cacheDir = "mirth-cache";
            new File(cacheDir).mkdirs();

            List<File> localJars = new ArrayList<>();
            for (String jar : jarFiles) {
                File localFile = new File(cacheDir, new File(jar).getName());
                if (!localFile.exists()) {
                    log("Downloading: " + jar);
                    try (InputStream in = new URL(baseUrl + "/" + jar).openStream();
                         FileOutputStream out = new FileOutputStream(localFile)) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = in.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                        }
                    }
                    log("Saved: " + localFile.getAbsolutePath());
                }
                localJars.add(localFile);
            }

            launchMirth(mainClass, localJars);
        } catch (Exception e) {
            log("Error: " + e.getMessage());
            e.printStackTrace();
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
        logArea.appendText(message + "\n");
    }
}