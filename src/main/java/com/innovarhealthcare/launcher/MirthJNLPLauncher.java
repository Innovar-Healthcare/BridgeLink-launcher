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
        log("🔍 Fetching main JNLP from: " + jnlpUrl);
        try {
            URL url = new URL(jnlpUrl);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(url.openStream());

            List<String> coreJars = new ArrayList<>();
            String mirthVersion = "unknown";

            // Extract core JARs
            NodeList jarList = doc.getElementsByTagName("jar");
            for (int i = 0; i < jarList.getLength(); i++) {
                Element jarElement = (Element) jarList.item(i);
                coreJars.add(jarElement.getAttribute("href"));
            }

            // Detect Mirth version
            NodeList versionNodes = doc.getElementsByTagName("title");
            if (versionNodes.getLength() > 0) {
                mirthVersion = versionNodes.item(0).getTextContent().replaceAll("[^0-9.]", "");
            }

            log("✅ Detected Mirth Version: " + mirthVersion);

            // Extract extension JNLPs
            NodeList extensionNodes = doc.getElementsByTagName("extension");
            List<String> extensionJnlpUrls = new ArrayList<>();

            for (int i = 0; i < extensionNodes.getLength(); i++) {
                Element extElement = (Element) extensionNodes.item(i);
                String extJnlpPath = extElement.getAttribute("href");
                String extJnlpUrl = "https://localhost:8443/" + extJnlpPath;
                extensionJnlpUrls.add(extJnlpUrl);
            }

            log("✅ Found extension JNLPs: " + extensionJnlpUrls);

            // ✅ Fetch extension JARs
            List<String> extensionJars = new ArrayList<>();
            for (String extJnlpUrl : extensionJnlpUrls) {
                parseExtensionJnlp(extJnlpUrl, mirthVersion, extensionJars);
            }

            // Download & Launch Mirth
            downloadAndLaunch(jnlpUrl, coreJars, extensionJars, mirthVersion);
        } catch (Exception e) {
            log("❌ ERROR in launchMirthFromURL(): " + e.getMessage());
            e.printStackTrace();
        }
    }


    private void parseExtensionJnlp(String extJnlpUrl, String mirthVersion, List<String> extensionJars) {
        log("🔍 Fetching Extension JNLP: " + extJnlpUrl);
        try {
            URL url = new URL(extJnlpUrl);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(url.openStream());

            // Extract extension name from URL (e.g., pdfviewer from "https://localhost:8443/webstart/extensions/pdfviewer.jnlp")
            String extensionName = new File(new URL(extJnlpUrl).getPath()).getName().replace(".jnlp", "");
            String extensionFolder = "mirth-cache/" + mirthVersion + "/extensions/" + extensionName;
            new File(extensionFolder).mkdirs();

            NodeList jarList = doc.getElementsByTagName("jar");
            for (int i = 0; i < jarList.getLength(); i++) {
                Element jarElement = (Element) jarList.item(i);
                String jarPath = jarElement.getAttribute("href");

                // Construct full JAR URL
                String jarUrl = "https://localhost:8443/webstart/extensions/" + jarPath;
                File localFile = new File(extensionFolder, new File(jarPath).getName());

                log("⬇️ Downloading Extension JAR: " + jarUrl + " -> " + localFile.getAbsolutePath());
                if (downloadFile(jarUrl, localFile, null)) {
                    extensionJars.add(localFile.getAbsolutePath());
                } else {
                    log("❌ WARNING: Failed to download extension JAR: " + jarUrl);
                }
            }

            log("✅ Extension JARs for " + extensionName + ": " + extensionJars);
        } catch (Exception e) {
            log("❌ ERROR fetching extension JNLP: " + e.getMessage());
        }
    }

    private void downloadAndLaunch(String jnlpUrl, List<String> coreJars, List<String> extensionJars, String mirthVersion) {
        log("🚀 Starting download process for Mirth version: " + mirthVersion);

        String baseUrl = jnlpUrl.substring(0, jnlpUrl.indexOf("/webstart") + 9); // Ensure base URL includes `/webstart`
        String cacheDir = "mirth-cache/" + mirthVersion;
        String cacheCoreDir = cacheDir + "/core";
        String cacheExtensionsDir = cacheDir + "/extensions";

        new File(cacheCoreDir).mkdirs(); // Ensure core directory exists

        List<File> localJars = new ArrayList<>();

        // ✅ Download Core JARs
        for (String jar : coreJars) {
            String correctedJarUrl = baseUrl + "/client-lib/" + new File(jar).getName();
            File localFile = new File(cacheCoreDir, new File(jar).getName());

            log("⬇️ Downloading Core JAR: " + correctedJarUrl);
            if (!downloadFile(correctedJarUrl, localFile, null)) {
                log("❌ WARNING: Failed to download core JAR: " + correctedJarUrl);
            }
            localJars.add(localFile);
        }

        // ✅ Download Extension JARs
        for (String jar : extensionJars) {
            String extensionName = new File(jar).getParentFile().getName(); // Extracts 'dicomviewer'
            String extensionFolder = cacheExtensionsDir + "/" + extensionName;
            new File(extensionFolder).mkdirs(); // Ensure extension folder exists

            String correctedJarUrl = baseUrl + "/extensions/libs/" + extensionName + "/" + new File(jar).getName();
            File localFile = new File(extensionFolder, new File(jar).getName());

            log("⬇️ Downloading Extension JAR: " + correctedJarUrl + " -> " + localFile.getAbsolutePath());
            if (!downloadFile(correctedJarUrl, localFile, null)) {
                log("❌ WARNING: Missing extension JAR: " + correctedJarUrl);
            }
            localJars.add(localFile);
        }

        try {
            launchMirth("com.mirth.connect.client.ui.Mirth", localJars);
        } catch (Exception e) {
            log("❌ ERROR: Failed to launch Mirth - " + e.getMessage());
        }
    }

    private void launchMirth(String mainClass, List<File> jarFiles) throws Exception {
        log("Launching Mirth Client in a separate process...");

        List<String> classpath = new ArrayList<>();
        for (File jar : jarFiles) {
            classpath.add(jar.getAbsolutePath());
        }

        String classpathString = String.join(File.pathSeparator, classpath);
        log("Final Classpath: " + classpathString);

        List<String> command = new ArrayList<>();
        command.add("java");
        command.add("--add-opens=java.base/java.util=ALL-UNNAMED"); // Ensure reflection-based features work
        command.add("-cp");
        command.add(classpathString);
        command.add(mainClass);

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.inheritIO(); // Redirect output to console

        Process process = processBuilder.start();
        log("Mirth Client started successfully. PID: " + process.pid());
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
            if (destination.exists() && expectedSha256 != null) {
                String actualSha256 = calculateSha256(destination);
                if (expectedSha256.equalsIgnoreCase(actualSha256)) {
                    log("✅ Skipping already downloaded file: " + destination.getName());
                    return true;
                }
            }

            log("⬇️ Downloading: " + urlStr);
            URL url = new URL(urlStr);
            try (InputStream in = url.openStream();
                 FileOutputStream out = new FileOutputStream(destination)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }

            if (expectedSha256 != null) {
                String actualSha256 = calculateSha256(destination);
                if (!expectedSha256.equalsIgnoreCase(actualSha256)) {
                    log("❌ ERROR: SHA-256 mismatch for " + destination.getName());
                    destination.delete();
                    return false;
                }
            }

            log("✅ Saved: " + destination.getAbsolutePath());
            return true;
        } catch (Exception e) {
            log("❌ ERROR downloading file: " + urlStr + " - " + e.getMessage());
            return false;
        }
    }

    private List<String> getAllJarFiles(String baseUrl) throws IOException {
        log("🔍 Fetching JARs under: " + baseUrl);
        List<String> jarFiles = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new URL(baseUrl).openStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("href") && line.endsWith(".jar\"")) { // Ensure only .jar files are processed
                    int startIndex = line.indexOf("href=\"") + 6;
                    int endIndex = line.indexOf("\"", startIndex);
                    if (endIndex > startIndex) {
                        String jarName = line.substring(startIndex, endIndex);
                        jarFiles.add(baseUrl + jarName);
                    }
                }
            }
        }

        log("✅ Detected JARs: " + jarFiles);
        return jarFiles;
    }

    private List<String> getSubdirectories(String url) throws IOException {
        log("🔍 Fetching extension subdirectories from: " + url);
        List<String> subDirs = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new URL(url).openStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Ensure we are parsing only valid directory links
                if (line.contains("href") && line.contains("/") && !line.contains("..") && !line.contains("css") && !line.contains("js")) {
                    int startIndex = line.indexOf("href=\"") + 6;
                    int endIndex = line.indexOf("/", startIndex);
                    if (endIndex > startIndex) {
                        String dirName = line.substring(startIndex, endIndex);

                        // Ensure we do not capture invalid entries
                        if (dirName.matches("^[a-zA-Z0-9-_]+$") && !subDirs.contains(dirName)) {
                            subDirs.add(dirName);
                        }
                    }
                }
            }
        }

        log("✅ Detected subdirectories: " + subDirs);
        return subDirs;
    }

    private List<String> downloadExtensions(String baseUrl, String mirthVersion) {
        log("🔍 Crawling extension directories under: " + baseUrl + "/webstart/extensions/libs/");

        String cacheExtensionsDir = "mirth-cache/" + mirthVersion + "/extensions";
        new File(cacheExtensionsDir).mkdirs(); // Ensure base extensions folder exists

        List<String> downloadedJars = new ArrayList<>();

        try {
            // 1️⃣ Get a list of extension directories under /webstart/extensions/libs/
            List<String> extensionDirs = getSubdirectories(baseUrl + "/webstart/extensions/libs/");
            log("✅ Detected extensions: " + extensionDirs);

            if (extensionDirs.isEmpty()) {
                log("❌ WARNING: No extension directories found under /webstart/extensions/libs/");
            }

            for (String extension : extensionDirs) {
                String extensionFolder = cacheExtensionsDir + "/" + extension;
                new File(extensionFolder).mkdirs(); // Ensure local extension folder exists
                log("📂 Processing extension: " + extension);

                // ✅ 2️⃣ Download JARs from the extension root folder
                String extensionUrl = baseUrl + "/webstart/extensions/libs/" + extension + "/";
                log("🔍 Fetching JARs from: " + extensionUrl);
                List<String> extensionJars = getAllJarFiles(extensionUrl);
                log("📄 JARs found in " + extension + ": " + extensionJars);

                for (String jarUrl : extensionJars) {
                    downloadJar(jarUrl, extensionFolder, downloadedJars);
                }

                // ✅ 3️⃣ Check if a `lib/` subfolder exists, and download JARs from there
                String libUrl = baseUrl + "/webstart/extensions/libs/" + extension + "/lib/";
                log("🔍 Checking for lib JARs at: " + libUrl);
                List<String> libJars = getAllJarFiles(libUrl);
                log("📄 JARs found in " + extension + "/lib/: " + libJars);

                for (String jarUrl : libJars) {
                    downloadJar(jarUrl, extensionFolder, downloadedJars);
                }
            }
        } catch (Exception e) {
            log("❌ ERROR in downloadExtensions(): " + e.getMessage());
        }

        log("✅ Final downloaded extensions: " + downloadedJars);
        return downloadedJars;
    }

    private void downloadJar(String jarUrl, String extensionFolder, List<String> downloadedJars) {
        if (!jarUrl.endsWith(".jar")) return;

        String jarName = new File(jarUrl).getName();
        File localFile = new File(extensionFolder, jarName);

        log("⬇️ Downloading JAR: " + jarUrl + " -> " + localFile.getAbsolutePath());
        if (downloadFile(jarUrl, localFile, null)) {
            downloadedJars.add(localFile.getAbsolutePath());
        } else {
            log("❌ WARNING: Failed to download: " + jarUrl);
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
