package com.innovarhealthcare.launcher;

class HeapMemory {
    private String value;
    private String display;
    public HeapMemory(String value, String display) {
        this.value = value;
        this.display = display;
    }
    public String getValue() { return value; }
    public String getDisplay() { return display; }

    @Override public String toString() { return value; }
}
