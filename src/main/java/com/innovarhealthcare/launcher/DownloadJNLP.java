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

import java.io.File;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.PrintWriter;
import java.io.FileWriter;

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
 * @author Thaitran,Zi-Min (Jim) Weng
 */
public class DownloadJNLP {
    private static final String LOG_FILE = "launcher-debug.log";
    private static final boolean DEBUG = false;
    private final File cacheFolder;
    private String host = "";
    private volatile boolean cancelled = false;
    private boolean clearCacheJars = false;

    public DownloadJNLP(String host, File cacheFolder, boolean clearCacheJars) {
        if (host.endsWith("/")) {
            host = host.substring(0, host.length() - 1);
        }
        this.host = host;
        this.cacheFolder = cacheFolder;
        this.clearCacheJars = clearCacheJars;
    }

    public CodeBase handle(Progress progress) throws  Exception{
        progress.updateProgressText("Requesting main JNLP...");
        checkCancelled("handle start");

        String jnlpUrl = host + "/" + "webstart.jnlp";

        log("🔍 Fetching main JNLP from: " + jnlpUrl);

        List<File> localJars = new ArrayList<>();
        String bridgeVersion = "unknown";
        try {
            URL url = new URL(jnlpUrl);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(url.openStream());


            // Extract core JARs with their SHA-256 hashes
            List<JarInfo> coreJarInfos = new ArrayList<>();
            NodeList jarList = doc.getElementsByTagName("jar");
            for (int i = 0; i < jarList.getLength(); i++) {
                checkCancelled("core JAR extraction");
                Element jarElement = (Element) jarList.item(i);
                String href = jarElement.getAttribute("href");
                String jarElementSha256 = jarElement.getAttribute("sha256");
                coreJarInfos.add(new JarInfo(href, jarElementSha256));
            }

            // Detect Mirth version
            NodeList versionNodes = doc.getElementsByTagName("title");
            if (versionNodes.getLength() > 0) {
                bridgeVersion = versionNodes.item(0).getTextContent().replaceAll("[^0-9.]", "");
            }
            bridgeVersion = doc.getDocumentElement().getAttribute("version");
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
                listExtensions.add(parseExtensionJnlp(extJnlpUrl));
            }

            // Download & Launch Mirth
            localJars = download(jnlpUrl, coreJarInfos, bridgeVersion, listExtensions, progress, clearCacheJars);
        } catch (Exception e) {
            log("❌ ERROR in handle(): " + e.getMessage());

            throw e;
        }

        // prepare code base
        List<String> classpath = new ArrayList<>();
        for (File jar : localJars) {
            classpath.add(jar.getAbsolutePath());
        }

        return new CodeBase(classpath,"com.mirth.connect.client.ui.Mirth", host, bridgeVersion);
    }

    private ExtensionInfo parseExtensionJnlp(String extJnlpUrl) {
        log("🔍 Fetching Extension JNLP: " + extJnlpUrl);

        try {
            URL url = new URL(extJnlpUrl);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(url.openStream());

            String extensionName = new File(new URL(extJnlpUrl).getPath()).getName().replace(".jnlp", "");
            Map<String, String> mapJars = new HashMap<>();

            NodeList jarList = doc.getElementsByTagName("jar");
            for (int i = 0; i < jarList.getLength(); i++) {
                Element jarElement = (Element) jarList.item(i);
                String jarPath = jarElement.getAttribute("href");
                String jarElementSha256 = jarElement.getAttribute("sha256");
                String jarUrl = this.host + "/webstart/extensions/" + jarPath;
                mapJars.put(jarUrl, new File(jarPath).getName() + "|" + jarElementSha256);
            }

            log("✅ Extension JARs for " + extensionName + ": " + mapJars.toString());

            return new ExtensionInfo(extensionName, mapJars);
        } catch (Exception e) {
            log("❌ ERROR fetching extension JNLP: " + e.getMessage());
        }

        return new ExtensionInfo();
    }

    private List<File> download(String jnlpUrl, List<JarInfo> coreJarInfos, String bridgeVersion,
                                List<ExtensionInfo> listExtensions, Progress progress, boolean clearCacheJars) throws  Exception{
        log("🚀 Starting download process for BridgeLink version: " + bridgeVersion);
        checkCancelled("download start");

        String baseUrl = jnlpUrl.substring(0, jnlpUrl.indexOf("/webstart") + 9); // Ensure base URL includes `/webstart`

        String path = bridgeVersion + "/core";
        File coreFolder = new File(cacheFolder, path);
        if (!coreFolder.exists()) {
            coreFolder.mkdirs(); // Ensure core directory exists
        }

        List<File> localJars = new ArrayList<>();
        int numOfJars = coreJarInfos.size() + listExtensions.size();
        int cntNum = 0;

        // ✅ Download Core JARs
        for (JarInfo jarInfo : coreJarInfos) {
            checkCancelled("core JAR download");
            String correctedJarUrl = baseUrl + "/client-lib/" + new File(jarInfo.getHref()).getName();
            File localFile = new File(coreFolder, new File(jarInfo.getHref()).getName());

            progress.updateProgressText("Downloading Core JAR " + jarInfo.getHref() + "...");
            log("⬇️ Checking Core JAR: " + correctedJarUrl);

            cntNum += 1;

            // Check if clearCacheJars is enabled - if so, always download
            if (clearCacheJars) {
                log("🔄 Clear cache enabled - downloading core JAR: " + localFile.getName());
            } else {
                // Check if file exists and compare SHA-256 hash from JNLP
                if (localFile.exists() && jarInfo.getSha256() != null && !jarInfo.getSha256().trim().isEmpty()) {
                    try {
                        String localHash = calculateSha256(localFile);
                        log("🔍 Local file SHA-256:    '" + localHash + "'");
                        log("🔍 Expected SHA-256:      '" + jarInfo.getSha256() + "'");
                        if (jarInfo.getSha256().trim().equals(localHash)) {
                            log("✅ Skipping core JAR (hash matches): " + localFile.getName());
                            localJars.add(localFile);
                            progress.updateProgressBar((double) cntNum/numOfJars);
                            continue;
                        } else {
                            log("🔄 Hash differs, re-downloading core JAR: " + localFile.getName());
                            log("🔄 Local:    " + localHash);
                            log("🔄 Expected: " + jarInfo.getSha256());
                        }
                    } catch (Exception e) {
                        log("❌ ERROR calculating local hash for " + localFile.getName() + ": " + e.getMessage());
                    }
                } else {
                    log("🔍 Core JAR info - exists: " + localFile.exists() + ", getSha256: '" + jarInfo.getSha256() + "'");
                }
            }

            // Download if file doesn't exist or hashes don't match
            if (!downloadFile(correctedJarUrl, localFile, jarInfo.getSha256())) {
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
            String extPath = bridgeVersion + "/extensions/" + extensionName;
            File extensionFolder = new File(cacheFolder, extPath);
            if (!extensionFolder.exists()) {
                extensionFolder.mkdirs(); // Create the "extensionFolder" folder if it doesn't exist
            }

            Map<String, String> mapJars = jar.getMapJars();

            cntNum += 1;

            if (mapJars == null) {
                log("❌ WARNING: Failed to download Extension JAR: mapJars is null - extensionName: " + extensionName);
                continue;
            }

            for (Map.Entry<String, String> entry : mapJars.entrySet()) {
                checkCancelled("extension JAR download loop");
                String jarUrl = entry.getKey();
                String jarInfo = entry.getValue();

                // Split jarName and getSha256 (format: "jarName|getSha256")
                String[] parts = jarInfo.split("\\|", 2);
                String jarName = parts[0];
                String expectedSha256 = parts.length > 1 ? parts[1] : null;

                File localFile = new File(extensionFolder, jarName);

                progress.updateProgressText("Downloading JARs for extension " + jarName + "...");
                log("⬇️ Checking Extension JAR: " + jarUrl + " -> " + localFile.getAbsolutePath());

                // Check if clearCacheJars is enabled - if so, always download
                if (clearCacheJars) {
                    log("🔄 Clear cache enabled - downloading extension JAR: " + localFile.getName());
                } else {
                    // Check if file exists and compare SHA-256 hash from JNLP
                    if (localFile.exists() && expectedSha256 != null && !expectedSha256.trim().isEmpty()) {
                        try {
                            String localHash = calculateSha256(localFile);
                            log("🔍 Extension Local SHA-256:    '" + localHash + "'");
                            log("🔍 Extension Expected SHA-256:  '" + expectedSha256 + "'");
                            if (expectedSha256.trim().equals(localHash)) {
                                log("✅ Skipping extension JAR (hash matches): " + localFile.getName());
                                localJars.add(localFile);
                                continue;
                            } else {
                                log("🔄 Hash differs, re-downloading extension JAR: " + localFile.getName());
                            }
                        } catch (Exception e) {
                            log("❌ ERROR calculating local hash for " + localFile.getName() + ": " + e.getMessage());
                        }
                    }
                }

                // Download if file doesn't exist or hashes don't match
                if (!downloadFile(jarUrl, localFile, expectedSha256)) {
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
            // Skip file existence check if clearCacheJars is enabled
            if (!clearCacheJars && destination.exists() && expectedSha256 != null) {
                String actualSha256 = calculateSha256(destination);
                if (expectedSha256.trim().equalsIgnoreCase(actualSha256)) {
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

            if (expectedSha256 != null && !expectedSha256.trim().isEmpty()) {
                String actualSha256 = calculateSha256(destination);
                log("🔍 Expected SHA-256: '" + expectedSha256.trim() + "'");
                log("🔍 Actual SHA-256:   '" + actualSha256 + "'");
                log("🔍 Hash lengths - Expected: " + expectedSha256.length() + ", Actual: " + actualSha256.length());

                if (!expectedSha256.trim().equals(actualSha256)) {
                    log("❌ ERROR: SHA-256 mismatch for " + destination.getName());
                    log("❌ Expected: " + expectedSha256.trim());
                    log("❌ Actual:   " + actualSha256);
                    destination.delete();
                    return false;
                }
                log("✅ SHA-256 verification passed for " + destination.getName());
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
        return java.util.Base64.getEncoder().encodeToString(hashBytes);
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
