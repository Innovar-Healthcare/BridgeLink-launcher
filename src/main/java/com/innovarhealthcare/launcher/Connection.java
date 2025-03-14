package com.innovarhealthcare.launcher;

public class Connection {
    private String id;
    private String name;
    private String address;
    private String javaHome;
    private String javaHomeBundledValue;
    private String javaFxHome;
    private String heapSize;
    private String icon;
    private boolean showJavaConsole;
    private boolean sslProtocolsCustom;
    private String sslProtocols;
    private boolean sslCipherSuitesCustom;
    private String sslCipherSuites;
    private boolean useLegacyDHSettings;

    // Constructors, getters, and setters
    public Connection() {}

    public Connection(String id, String name, String address, String javaHome, String javaHomeBundledValue, String javaFxHome,
                      String heapSize, String icon, boolean showJavaConsole, boolean sslProtocolsCustom, String sslProtocols,
                      boolean sslCipherSuitesCustom, String sslCipherSuites, boolean useLegacyDHSettings) {
        this.id = id;
        this.name = name;
        this.address = address;
        this.javaHome = javaHome;
        this.javaHomeBundledValue = javaHomeBundledValue;
        this.javaFxHome = javaFxHome;
        this.heapSize = heapSize;
        this.icon = icon;
        this.showJavaConsole = showJavaConsole;
        this.sslProtocolsCustom = sslProtocolsCustom;
        this.sslProtocols = sslProtocols;
        this.sslCipherSuitesCustom = sslCipherSuitesCustom;
        this.sslCipherSuites = sslCipherSuites;
        this.useLegacyDHSettings = useLegacyDHSettings;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getJavaHome() { return javaHome; }
    public void setJavaHome(String javaHome) { this.javaHome = javaHome; }
    public String getJavaHomeBundledValue() { return javaHomeBundledValue; }
    public void setJavaHomeBundledValue(String javaHomeBundledValue) { this.javaHomeBundledValue = javaHomeBundledValue; }
    public String getJavaFxHome() { return javaFxHome; }
    public void setJavaFxHome(String javaFxHome) { this.javaFxHome = javaFxHome; }
    public String getHeapSize() { return heapSize; }
    public void setHeapSize(String heapSize) { this.heapSize = heapSize; }
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
    public boolean isShowJavaConsole() { return showJavaConsole; }
    public void setShowJavaConsole(boolean showJavaConsole) { this.showJavaConsole = showJavaConsole; }
    public boolean isSslProtocolsCustom() { return sslProtocolsCustom; }
    public void setSslProtocolsCustom(boolean sslProtocolsCustom) { this.sslProtocolsCustom = sslProtocolsCustom; }
    public String getSslProtocols() { return sslProtocols; }
    public void setSslProtocols(String sslProtocols) { this.sslProtocols = sslProtocols; }
    public boolean isSslCipherSuitesCustom() { return sslCipherSuitesCustom; }
    public void setSslCipherSuitesCustom(boolean sslCipherSuitesCustom) { this.sslCipherSuitesCustom = sslCipherSuitesCustom; }
    public String getSslCipherSuites() { return sslCipherSuites; }
    public void setSslCipherSuites(String sslCipherSuites) { this.sslCipherSuites = sslCipherSuites; }
    public boolean isUseLegacyDHSettings() { return useLegacyDHSettings; }
    public void setUseLegacyDHSettings(boolean useLegacyDHSettings) { this.useLegacyDHSettings = useLegacyDHSettings; }
}
