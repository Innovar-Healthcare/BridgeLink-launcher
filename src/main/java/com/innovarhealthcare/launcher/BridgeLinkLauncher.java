package com.innovarhealthcare.launcher;
import com.innovarhealthcare.launcher.interfaces.Progress;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Callback;


public class BridgeLinkLauncher extends Application implements Progress {
    private static final String VERSION = "1.0.0";
    private TableView<Connection> connectionsTableView;
    private Label addressLabel;
    private TextField addressTextField;
    private Button launchButton;
    private Label javaHomeLabel;
    private RadioButton bundledJavaRadio;
    private ComboBox<BundledJava> bundledJavaCombo;
    private RadioButton defaultJavaRadio;
    private RadioButton customJavaRadio;
    private TextField customJavaTextField;
    private Label heapSizeLabel;
    private TextField heapSizeTextField;
    private ComboBox<HeapMemory> heapSizeCombo;
    private Label consoleLabel;
    private RadioButton consoleYesRadio;
    private RadioButton consoleNoRadio;
    private Separator separator2;
    private Text progressText;
    private ProgressBar progressBar;
    private ProgressIndicator progressIndicator;
    private Button cancelButton;
    private CheckBox closeWindowCheckBox;
    private Button newButton;
    private Button saveButton;
    private Button saveAsButton;
    private Button deleteButton;
    private Thread launchThread;
    private volatile boolean isLaunching = false;
    private Stage primaryStage;

    @Override
    public void start(Stage stage) {
        primaryStage = stage;
        stage.setTitle("BridgeLink Administrator Launcher (" + VERSION + ")");
        try {
            stage.getIcons().add(new Image(getClass().getResourceAsStream("/images/launcher_32.png")));
        } catch (Exception e) {
            // Handle icon loading failure
        }

        BorderPane root = new BorderPane();
        SplitPane splitPane = new SplitPane();
        splitPane.setDividerPositions(0.3);
        root.setCenter(splitPane);

        // Connections TableView
        this.connectionsTableView = new TableView<>();
        this.connectionsTableView.setPlaceholder(new Label("No saved connections"));

        TableColumn<Connection, String> nameColumn = new TableColumn<>("Connections");
        nameColumn.setCellValueFactory(param -> new javafx.beans.property.SimpleStringProperty(param.getValue().getName()));
        this.connectionsTableView.getColumns().add(nameColumn);

        splitPane.getItems().add(this.connectionsTableView);

        // Configuration GridPane
        GridPane gridPane = new GridPane();
        gridPane.setAlignment(Pos.TOP_LEFT);
        gridPane.setHgap(10.0);
        gridPane.setVgap(10.0);
        gridPane.setPadding(new Insets(12.0));

        int row = 0;
        // Address
        this.addressLabel = new Label("Address:");
        this.addressLabel.setMinWidth(110.0);
        gridPane.add(this.addressLabel, 0, row);

        this.addressTextField = new TextField("https://localhost:8443");
        this.addressTextField.setPrefWidth(388.0);
        gridPane.add(this.addressTextField, 1, row);

        this.launchButton = new Button("Launch");
        this.launchButton.setMinWidth(63.0);
        this.launchButton.setOnAction(event -> launch());
        gridPane.add(this.launchButton, 3, row++);

        // Java Home
        this.javaHomeLabel = new Label("Java Home:");
        gridPane.add(this.javaHomeLabel, 0, row);

        this.bundledJavaCombo = new ComboBox<>(FXCollections.observableArrayList(
            new BundledJava("", "Java 17"),
            new BundledJava("", "Java 8")
        ));

        this.bundledJavaCombo.getSelectionModel().select(0);
        gridPane.add(this.bundledJavaCombo, 1, row++, 3, 1);

        // Max Heap Size
        this.heapSizeLabel = new Label("Max Heap Size:");
        gridPane.add(this.heapSizeLabel, 0, row);

        this.heapSizeCombo = new ComboBox<>(FXCollections.observableArrayList(
            new HeapMemory("256m", "256 MB"),
            new HeapMemory("512m", "512 MB"),
            new HeapMemory("1g", "1 GB"),
            new HeapMemory("2g", "2 GB"),
            new HeapMemory("4g", "4 GB")
        ));
        this.heapSizeCombo.getSelectionModel().select(1);
        gridPane.add(this.heapSizeCombo, 1, row++, 3, 1);

        // Show Java Console
        this.consoleLabel = new Label("Show Java Console:");
        gridPane.add(this.consoleLabel, 0, row);

        ToggleGroup consoleGroup = new ToggleGroup();
        this.consoleYesRadio = new RadioButton("Yes");
        this.consoleYesRadio.setToggleGroup(consoleGroup);

        this.consoleNoRadio = new RadioButton("No");
        this.consoleNoRadio.setToggleGroup(consoleGroup);
        this.consoleNoRadio.setSelected(true);

        HBox consoleBox = new HBox(10, this.consoleYesRadio, this.consoleNoRadio);
        gridPane.add(consoleBox, 1, row++, 3, 1);

        // Progress Indicators
        this.separator2 = new Separator();
        gridPane.add(this.separator2, 0, row++, 4, 1);

        this.progressBar = new ProgressBar(0.0);
        this.progressBar.setMaxWidth(Double.MAX_VALUE);
        this.progressIndicator = new ProgressIndicator(-1.0);
        this.progressIndicator.setPrefHeight(20.0);
        HBox progressBox = new HBox(10, this.progressBar, this.progressIndicator);
        HBox.setHgrow(this.progressBar, Priority.ALWAYS);
        progressBox.setAlignment(Pos.CENTER);
        gridPane.add(progressBox, 0, row, 3, 1);

        this.cancelButton = new Button("Cancel");
        this.cancelButton.setPrefWidth(63.0);
        this.cancelButton.setOnAction(event -> cancelLaunch());
        gridPane.add(this.cancelButton, 3, row++);

        this.progressText = new Text("Requesting main JNLP...");
        VBox textBox = new VBox(this.progressText);
        textBox.setPrefWidth(450.0);
        gridPane.add(textBox, 0, row++, 4, 1);

        // Bottom Buttons
        this.closeWindowCheckBox = new CheckBox("Close this window after launching");

        this.newButton = new Button("New");
        this.saveButton = new Button("Save");
        this.saveAsButton = new Button("Save As...");
        this.deleteButton = new Button("Delete");

        HBox bottomLeft = new HBox(this.closeWindowCheckBox);
        bottomLeft.setAlignment(Pos.BOTTOM_LEFT);
        HBox bottomRight = new HBox(10, this.newButton, this.saveButton, this.saveAsButton, this.deleteButton);
        bottomRight.setAlignment(Pos.BOTTOM_RIGHT);

        AnchorPane bottomPane = new AnchorPane(bottomLeft, bottomRight);
        AnchorPane.setLeftAnchor(bottomLeft, 10.0);
        AnchorPane.setBottomAnchor(bottomLeft, 15.0);
        AnchorPane.setRightAnchor(bottomRight, 10.0);

        VBox configPane = new VBox(gridPane, bottomPane);
        VBox.setVgrow(gridPane, Priority.ALWAYS);
        splitPane.getItems().add(configPane);

        setProgressControlsVisible(false);

        Scene scene = new Scene(root, 800, 400);
        stage.setScene(scene);
        stage.show();
    }

    private void launch() {
        if (isLaunching || addressTextField.getText().isEmpty()) return;

        isLaunching = true;
        setUIEnabled(false);
        setProgressControlsVisible(true);
        progressText.setText("Launching " + addressTextField.getText());
        progressBar.setProgress(0.0);
        progressIndicator.setProgress(-1.0);
        cancelButton.setDisable(false);

        launchThread = new Thread(() -> {
            try {
                String host = addressTextField.getText();
                DownloadJNLP download = new DownloadJNLP(host);

                List<File> jarFiles  = download.handle(this);
                updateProgressText("Done downloading......");

                List<String> classpath = new ArrayList<>();
                for (File jar : jarFiles) {
                    classpath.add(jar.getAbsolutePath());
                }
                String classpathString = String.join(File.pathSeparator, classpath);
                String mainClass = "com.mirth.connect.client.ui.Mirth";
                List<String> command = new ArrayList<>();
                command.add("java");
                command.add("-Xmx512m");
                command.add("-cp");
                command.add(classpathString);
                command.add(mainClass);
                command.add(host);
                ProcessBuilder processBuilder = new ProcessBuilder(command);
                processBuilder.inheritIO(); // Redirect output to console

                Process process = processBuilder.start();
                Thread.sleep(5000);

                Platform.runLater(() -> {
                    progressText.setText("Launch complete!");
                    if (closeWindowCheckBox.isSelected()) {
                        primaryStage.close();
                    } else {
                        resetUI();
                    }
                });

            } catch (InterruptedException | IOException e) {
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Error");
                    alert.setHeaderText("Error Launching BridgeLink");
                    alert.setContentText(e.getMessage());
                    alert.initOwner(primaryStage);
                    alert.showAndWait();
                    resetUI();
                });
            } finally {
                setProgressControlsVisible(false);
                isLaunching = false;
            }
        }, "Launch Thread");
        launchThread.start();
    }

    private void cancelLaunch() {
        if (launchThread != null && launchThread.isAlive()) {
            launchThread.interrupt();
        }
    }

    private void setUIEnabled(boolean enabled) {
        launchButton.setDisable(!enabled);
        addressTextField.setDisable(!enabled);
        bundledJavaCombo.setDisable(!enabled);
        heapSizeCombo.setDisable(!enabled);
        consoleYesRadio.setDisable(!enabled);
        consoleNoRadio.setDisable(!enabled);
        closeWindowCheckBox.setDisable(!enabled);
        newButton.setDisable(!enabled);
        saveButton.setDisable(!enabled);
        saveAsButton.setDisable(!enabled);
        deleteButton.setDisable(!enabled);
        cancelButton.setDisable(enabled);
    }

    private void resetUI() {
        progressBar.setProgress(0.0);
        progressIndicator.setProgress(-1.0);
        progressText.setText("");
        setUIEnabled(true);
        isLaunching = false;
    }

    private void setProgressControlsVisible(boolean visible) {
        progressBar.setVisible(visible);
        progressIndicator.setVisible(visible);
        progressText.setVisible(visible);
        cancelButton.setVisible(visible);
    }

    @Override
    public void updateProgressBar(double progress){
        Platform.runLater(() -> this.progressBar.setProgress(progress));
    }
    @Override
    public void updateProgressText(String message){
        Platform.runLater(() -> this.progressText.setText(message));
    }

    public static void main(String[] args) {
        SSLBypass.disableSSLVerification();
        launch(args);
    }

}
