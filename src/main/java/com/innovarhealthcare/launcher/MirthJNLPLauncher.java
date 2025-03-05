package com.innovarhealthcare.launcher;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import java.io.IOException;
import java.io.PrintWriter;

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
        String url = "https://localhost:8443";
        urlField = new TextField(url);
        Button launchButton = new Button("Launch Mirth");
        logArea = new TextArea();
        logArea.setEditable(false);

        // Clear the log file at the start of the program
        try (PrintWriter out = new PrintWriter(LOG_FILE)) {
            out.print(""); // Clears the file
        } catch (IOException e) {
            System.err.println("ERROR: Could not clear log file - " + e.getMessage());
        }

        launchButton.setOnAction(e -> (new MirthJNLP(urlField.getText())).launchMirth());

        VBox root = new VBox(10, new Label("Enter JNLP URL:"), urlField, launchButton, new Label("Log:"), logArea);
        primaryStage.setScene(new Scene(root, 600, 400));
        primaryStage.setTitle("Mirth JNLP Launcher");
        primaryStage.show();
    }
}
