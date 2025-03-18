package com.innovarhealthcare.launcher;

import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Properties;

public class JavaConsoleDialog extends JDialog {
    private JTextArea consoleTextArea;
    private JButton clearButton;
    private JButton copyButton;
    private JButton closeButton;

    public JavaConsoleDialog(Frame owner) {
        super(owner, "Java Console", false);
        
        initializeUI();
        
        setupActions();
        
        dumpJavaRuntimeConfig();
        
        setSize(400, 300);
//        setLocationRelativeTo(owner); // Center the dialog relative to the owner
        setLocation(10, 10);
    }

    private void initializeUI() {
        // Main panel with BorderLayout
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Console Text Area
        consoleTextArea = new JTextArea();
        consoleTextArea.setEditable(false);
        consoleTextArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        DefaultCaret caret = (DefaultCaret) consoleTextArea.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE); // Auto-scroll to the bottom
        
        JScrollPane scrollPane = new JScrollPane(consoleTextArea);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // Button Panel
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new FlowLayout(FlowLayout.RIGHT));
        clearButton = new JButton("Clear");
        copyButton = new JButton("Copy");
        closeButton = new JButton("Close");
        buttonPanel.add(clearButton);
        buttonPanel.add(copyButton);
        buttonPanel.add(closeButton);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        // Add panel to dialog
        getContentPane().add(mainPanel);
    }

    private void setupActions() {
        clearButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
            	dumpJavaRuntimeConfig();
            }
        });

        copyButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                consoleTextArea.selectAll();
                consoleTextArea.copy();
                consoleTextArea.setSelectionStart(consoleTextArea.getText().length());
                consoleTextArea.setSelectionEnd(consoleTextArea.getText().length());
            }
        });

        closeButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });

        consoleTextArea.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e) {
                if (e.getKeyChar() == '1') {
                	dumpJavaRuntimeConfig();
                } else if (e.getKeyChar() == 's') {
                    dumpSystemProperties();
                }
            }
        });
    }

    private static void setupPrintLog(JTextArea textArea) {
        try (InputStream input = System.in) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                String text = new String(buffer, 0, bytesRead);
                SwingUtilities.invokeLater(() -> {
                    textArea.append(text);
                    textArea.setCaretPosition(textArea.getDocument().getLength()); // Scroll to end
                });
            }
        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> textArea.append("Error: " + e.getMessage() + "\n"));
        }
    }
    
    private void dumpSystemProperties() {
        Properties props = System.getProperties();
        StringBuilder sb = new StringBuilder();
        for (Object key : new java.util.TreeSet<>(props.keySet())) {
            sb.append(key).append(" = ").append(props.getProperty((String) key)).append("\n");
        }
        consoleTextArea.append("\nSystem Properties:\n" + sb.toString());
    }

    public void appendToConsole(String text) {
        consoleTextArea.append(text + "\n");
    }
    
    private void dumpJavaRuntimeConfig() {
    	String jre = "Using JRE version " + System.getProperty("java.version") + " " +System.getProperty("java.vm.name") + "\n";  
    	String javaHome = "Java home directory = " + System.getProperty("java.home") + "\n"; 
    	String userHome = "User home directory = "+ System.getProperty("user.home") + "\n"; 
    	
        consoleTextArea.setText(jre);
        consoleTextArea.append(javaHome);
        consoleTextArea.append(userHome);
    	consoleTextArea.append("----------------------------------------------------\n");
        consoleTextArea.append("1: Clear console window\ns: Dump system properties\n");
        consoleTextArea.append("----------------------------------------------------\n");
    }
    
    public static void main(String[] args) {
        // Example usage
        SwingUtilities.invokeLater(() -> {
            JavaConsoleDialog dialog = new JavaConsoleDialog(null);
            dialog.setVisible(true);
            
            new Thread(() -> setupPrintLog(dialog.consoleTextArea)).start();
        });
    }
}