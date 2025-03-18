/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.innovarhealthcare.launcher;
import com.innovarhealthcare.launcher.interfaces.Progress;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.net.URL;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author thait
 */
public class DownloadJNLP {
    private static final String LOG_FILE = "launcher-debug.log";
    private static final boolean DEBUG = false;
    private final String CACHED_FOLDER;
    private String host = "";
    private volatile boolean cancelled = false;

    public DownloadJNLP(String host, String currentDir) {
        this.host = host;
        this.CACHED_FOLDER =  currentDir.isEmpty() ? "cache" :currentDir + "/cache";
    }

    public CodeBase handle(Progress progress) throws  Exception{
        progress.updateProgressText("Requesting main JNLP...");
        checkCancelled("handle start");

        String jnlpUrl = host + "/" + "webstart.jnlp";

        log("🔍 Fetching main JNLP from: " + jnlpUrl);

        List<File> localJars = new ArrayList<>();
        try {
            URL url = new URL(jnlpUrl);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(url.openStream());

            List<String> coreJars = new ArrayList<>();
            String bridgeVersion = "unknown";

            // Extract core JARs
            NodeList jarList = doc.getElementsByTagName("jar");
            for (int i = 0; i < jarList.getLength(); i++) {
                checkCancelled("core JAR extraction");
                Element jarElement = (Element) jarList.item(i);
                coreJars.add(jarElement.getAttribute("href"));
            }

            // Detect Mirth version
            NodeList versionNodes = doc.getElementsByTagName("title");
            if (versionNodes.getLength() > 0) {
                bridgeVersion = versionNodes.item(0).getTextContent().replaceAll("[^0-9.]", "");
            }

            log("✅ Detected BridgeLink Version: " + bridgeVersion);

            // Extract extension JNLPs
            progress.updateProgressText("Requesting JNLP for extensions...");
            checkCancelled("extension JNLP extraction");

            NodeList extensionNodes = doc.getElementsByTagName("extension");
            List<String> extensionJnlpUrls = new ArrayList<>();

            String baseUrl = jnlpUrl.replace("webstart.jnlp", "");
            for (int i = 0; i < extensionNodes.getLength(); i++) {
                Element extElement = (Element) extensionNodes.item(i);
                String extJnlpPath = extElement.getAttribute("href");
                String extJnlpUrl = baseUrl + extJnlpPath;
                extensionJnlpUrls.add(extJnlpUrl);
            }

            log("✅ Found extension JNLPs: " + extensionJnlpUrls);

            // ✅ Fetch extension JARs
            List<ExtensionInfo> listExtensions = new ArrayList<>();
            for (String extJnlpUrl : extensionJnlpUrls) {
                checkCancelled("parseExtensionJnlp");
                listExtensions.add(parseExtensionJnlp(extJnlpUrl, bridgeVersion));
            }

            // Download & Launch Mirth
            localJars = download(jnlpUrl, coreJars, bridgeVersion, listExtensions, progress);
        } catch (Exception e) {
            log("❌ ERROR in handle(): " + e.getMessage());

            throw e;
        }

        // prepare code base
        List<String> classpath = new ArrayList<>();
        for (File jar : localJars) {
            classpath.add(jar.getAbsolutePath());
        }

        return new CodeBase(classpath,"com.mirth.connect.client.ui.Mirth", host);
    }

    private ExtensionInfo parseExtensionJnlp(String extJnlpUrl, String bridgeVersion) {
        log("🔍 Fetching Extension JNLP: " + extJnlpUrl);

        try {
            URL url = new URL(extJnlpUrl);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(url.openStream());


            String extensionName = new File(new URL(extJnlpUrl).getPath()).getName().replace(".jnlp", "");
            String extensionFolder = CACHED_FOLDER + "/" + bridgeVersion + "/extensions/" + extensionName;
            new File(extensionFolder).mkdirs();

            Map<String, String> mapJars = new HashMap<>();

            NodeList jarList = doc.getElementsByTagName("jar");
            for (int i = 0; i < jarList.getLength(); i++) {
                Element jarElement = (Element) jarList.item(i);
                String jarPath = jarElement.getAttribute("href");
                String jarUrl = this.host + "/webstart/extensions/" + jarPath;
                mapJars.put(jarUrl, new File(jarPath).getName());
            }

            log("✅ Extension JARs for " + extensionName + ": " + mapJars.toString());

            return new ExtensionInfo(extensionName, mapJars);
        } catch (Exception e) {
            log("❌ ERROR fetching extension JNLP: " + e.getMessage());
        }

        return new ExtensionInfo();
    }

    private List<File> download(String jnlpUrl, List<String> coreJars, String bridgeVersion,
            List<ExtensionInfo> listExtensions, Progress progress) throws  Exception{
        log("🚀 Starting download process for BridgeLink version: " + bridgeVersion);
        checkCancelled("download start");

        String baseUrl = jnlpUrl.substring(0, jnlpUrl.indexOf("/webstart") + 9); // Ensure base URL includes `/webstart`
        String cacheDir = CACHED_FOLDER +"/" + bridgeVersion;
        String cacheCoreDir = cacheDir + "/core";
        String cacheExtensionsDir = cacheDir + "/extensions";

        new File(cacheCoreDir).mkdirs(); // Ensure core directory exists

        List<File> localJars = new ArrayList<>();
        int numOfJars = coreJars.size() + listExtensions.size();
        int cntNum = 0;

        // ✅ Download Core JARs
        for (String jar : coreJars) {
            checkCancelled("core JAR download");
            String correctedJarUrl = baseUrl + "/client-lib/" + new File(jar).getName();
            File localFile = new File(cacheCoreDir, new File(jar).getName());

            progress.updateProgressText("Downloading Core JAR " + jar + "...");
            log("⬇️ Downloading Core JAR: " + correctedJarUrl);

            cntNum += 1;

            if (!downloadFile(correctedJarUrl, localFile, null)) {
                log("❌ WARNING: Failed to download core JAR: " + correctedJarUrl);
                continue;
            }

            localJars.add(localFile);

            progress.updateProgressBar((double) cntNum/numOfJars);
        }

        // ✅ Download Extension JARs
        for (ExtensionInfo jar : listExtensions) {
            checkCancelled("extension JAR download");

            String extensionName = jar.getName();
            String extensionFolder = cacheExtensionsDir + "/" + extensionName;
            Map<String, String> mapJars = jar.getMapJars();

            cntNum += 1;

            if (mapJars == null) {
                log("❌ WARNING: Failed to download Extension JAR: mapJars is null - extensionName: " + extensionName);
                continue;
            }

            for (Map.Entry<String, String> entry : mapJars.entrySet()) {
                checkCancelled("extension JAR download loop");
                String jarUrl = entry.getKey();
                String jarName = entry.getValue();
                File localFile = new File(extensionFolder, jarName);

                progress.updateProgressText("Downloading JARs for extension " + jarName + "...");
                log("⬇️ Downloading Extension JAR: " + jarUrl + " -> " + localFile.getAbsolutePath());

                if (!downloadFile(jarUrl, localFile, null)) {
                    log("❌ WARNING: Missing extension JAR: " + jarUrl);
                    continue;
                }
                localJars.add(localFile);
            }

            progress.updateProgressBar((double) cntNum/numOfJars);
        }

        return localJars;
    }

    private boolean downloadFile(String urlStr, File destination, String expectedSha256) throws InterruptedException {
        if (Thread.interrupted() || cancelled){
            throw new InterruptedException("Download cancelled");
        }

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

    private String calculateSha256(File file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream fis = new FileInputStream(file);
                DigestInputStream dis = new DigestInputStream(fis, digest)) {
            byte[] buffer = new byte[4096];
            while (dis.read(buffer) != -1) {
            }
        }
        byte[] hashBytes = digest.digest();
        StringBuilder hexString = new StringBuilder();
        for (byte b : hashBytes) {
            hexString.append(String.format("%02x", b));
        }
        return hexString.toString();
    }

    private void log(String message) {
        if(DEBUG){
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
    }

    public void cancel() {
        cancelled = true;
    }

    private void checkCancelled(String context) throws InterruptedException {
        if (Thread.interrupted() || cancelled) {
            log("🚫 Cancelled at: " + context);
            throw new InterruptedException("Download cancelled");
        }
    }
}
