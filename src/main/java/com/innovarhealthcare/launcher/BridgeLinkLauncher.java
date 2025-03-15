package com.innovarhealthcare.launcher;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.innovarhealthcare.launcher.interfaces.Progress;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import java.io.*;
import java.util.*;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
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
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
//import org.apache.logging.log4j.LogManager;
//import org.apache.logging.log4j.Logger;

public class BridgeLinkLauncher extends Application implements Progress {
    private static final String VERSION = "1.0.0";
    private static final Image ICON_DEFAULT = new Image("/images/launcher_32.png");

    private final ObservableList<Connection> connectionsList = FXCollections.observableArrayList();

    private TableView<Connection> connectionsTableView;
    private TableView.TableViewSelectionModel<Connection> tableSelectionModel;

    private Label addressLabel;
    private TextField addressTextField;
    private Button launchButton;
    private Label javaHomeLabel;
    private ComboBox<BundledJava> bundledJavaCombo;
    private Label heapSizeLabel;
    private ComboBox<HeapMemory> heapSizeCombo;
    private Label consoleLabel;
    private CheckBox showConsoleCheckBox;
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
            stage.getIcons().add(ICON_DEFAULT);
        } catch (Exception e) {
            // Handle icon loading failure
        }

        VBox root = new VBox(15);
        root.setPadding(new Insets(15));

        // Connections Section with Buttons (unchanged)
        VBox connectionsSection = new VBox(20);
        connectionsSection.setPadding(new Insets(10));
        HBox tableButtons = new HBox(10);
        newButton = new Button("New");
        newButton.setOnAction(e -> createNewConnection());
        saveButton = new Button("Save");
        saveButton.setOnAction(e -> saveCurrentConnection());
        saveAsButton = new Button("Save As...");
        saveAsButton.setOnAction(e -> saveAsNewConnection());
        deleteButton = new Button("Delete");
        deleteButton.setDisable(true);
        deleteButton.setOnAction(e -> deleteCurrentConnection());
        tableButtons.getChildren().addAll(newButton, saveButton, saveAsButton, deleteButton);
        tableButtons.setAlignment(Pos.CENTER);

        connectionsTableView = new TableView<>();
        connectionsTableView.setPlaceholder(new Label("No saved connections"));
        connectionsTableView.setEditable(true);
        VBox.setVgrow(connectionsTableView, Priority.ALWAYS);

        TableColumn<Connection, String> iconColumn = new TableColumn<>("");
        iconColumn.setCellFactory(col -> new TableCell<Connection, String>() {
            private final ImageView imageView = new ImageView();
            @Override
            protected void updateItem(String iconName, boolean empty) {
                super.updateItem(iconName, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    imageView.setImage(getIconImage(iconName));
                    imageView.setFitHeight(20);
                    imageView.setFitWidth(20);
                    setGraphic(imageView);
                }
            }
        });
        iconColumn.setCellValueFactory(new PropertyValueFactory<>("icon"));
        iconColumn.setMinWidth(26.0);
        iconColumn.setMaxWidth(26.0);

        TableColumn<Connection, String> nameColumn = new TableColumn<>("Connections");
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        nameColumn.setOnEditCommit(event -> {
            String newName = StringUtils.trim(event.getNewValue());
            boolean exists = connectionsList.stream().anyMatch(conn ->
                    !StringUtils.equals(conn.getId(), event.getRowValue().getId()) &&
                            StringUtils.equalsIgnoreCase(conn.getName(), newName));
            if (!exists) event.getRowValue().setName(newName);
            connectionsTableView.refresh();
            saveConnections();
        });

        TableColumn<Connection, String> idColumn = new TableColumn<>("Id");
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        idColumn.setVisible(false);

        connectionsTableView.getColumns().addAll(iconColumn, nameColumn, idColumn);
        connectionsList.addAll(loadConnections());
        connectionsTableView.setItems(connectionsList);
        connectionsTableView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                addressTextField.setText(newVal.getAddress());
                updateUIFromConnection(newVal);
            }
            saveButton.setDisable(true);
            deleteButton.setDisable(newVal == null);
        });
        tableSelectionModel = connectionsTableView.getSelectionModel();
        tableSelectionModel.setSelectionMode(SelectionMode.SINGLE);
        connectionsTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        connectionsSection.getChildren().addAll(tableButtons, connectionsTableView);

        // Configuration Section (Modified: Java Home and Max Heap Size on same line)
        VBox configBox = new VBox(10);

        HBox addressRow = new HBox(10);
        addressLabel = new Label("Address:");
        addressTextField = new TextField("https://localhost:8443");
        addressTextField.textProperty().addListener((obs, oldVal, newVal) -> updateSaveButtonState());
        addressRow.getChildren().addAll(addressLabel, addressTextField);
        HBox.setHgrow(addressTextField, Priority.ALWAYS);

        HBox javaHeapRow = new HBox(10); // Combined Java Home and Max Heap Size
        javaHomeLabel = new Label("Java Home:");
        bundledJavaCombo = new ComboBox<>(FXCollections.observableArrayList(
                new BundledJava("", "Java 17"),
                new BundledJava("", "Java 8")
        ));
        bundledJavaCombo.getSelectionModel().select(0);
        bundledJavaCombo.setOnAction(e -> updateSaveButtonState());
        heapSizeLabel = new Label("Max Heap Size:");
        heapSizeCombo = new ComboBox<>(FXCollections.observableArrayList(
                new HeapMemory("256m", "256 MB"),
                new HeapMemory("512m", "512 MB"),
                new HeapMemory("1g", "1 GB"),
                new HeapMemory("2g", "2 GB"),
                new HeapMemory("4g", "4 GB")
        ));
        heapSizeCombo.getSelectionModel().select(1);
        heapSizeCombo.setOnAction(e -> updateSaveButtonState());
        javaHeapRow.getChildren().addAll(javaHomeLabel, bundledJavaCombo, heapSizeLabel, heapSizeCombo);
        HBox.setHgrow(bundledJavaCombo, Priority.ALWAYS);
        HBox.setHgrow(heapSizeCombo, Priority.ALWAYS);

        HBox consoleRow = new HBox(10);
        consoleLabel = new Label("Show Java Console:");
        showConsoleCheckBox = new CheckBox();
        showConsoleCheckBox.setSelected(false);
        showConsoleCheckBox.setOnAction(e -> updateSaveButtonState());
        consoleRow.getChildren().addAll(consoleLabel, showConsoleCheckBox);

        configBox.getChildren().addAll(addressRow, javaHeapRow, consoleRow);

        // Progress Section (unchanged)
        separator2 = new Separator();
        progressBar = new ProgressBar(0.0);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        launchButton = new Button("Launch");
        launchButton.setOnAction(e -> launch());
        cancelButton = new Button("Cancel");
        cancelButton.setOnAction(e -> cancelLaunch());
        cancelButton.setVisible(false);
        StackPane buttonStack = new StackPane(launchButton, cancelButton);
        buttonStack.setAlignment(Pos.CENTER);
        HBox progressBarBox = new HBox(10, progressBar, buttonStack);
        HBox.setHgrow(progressBar, Priority.ALWAYS);
        progressIndicator = new ProgressIndicator(-1.0);
        progressIndicator.setPrefHeight(20.0);
        progressText = new Text("Requesting main JNLP...");
        VBox progressBox = new VBox(10, separator2, progressBarBox, progressIndicator, progressText);
        setProgressControlsVisible(false);

        // Bottom Section (unchanged)
        HBox bottomBox = new HBox(10);
        closeWindowCheckBox = new CheckBox("Close after launch");
        bottomBox.getChildren().add(closeWindowCheckBox);
        bottomBox.setAlignment(Pos.CENTER_LEFT);

        // Assemble layout
        root.getChildren().addAll(connectionsSection, configBox, progressBox, bottomBox);

        Scene scene = new Scene(root, 800, 600);
        stage.setScene(scene);
        stage.show();

        connectionsTableView.getSelectionModel().selectFirst();
    }

    private void createNewConnection() {
        if (!isLaunching) {
            String newName = "New Connection";
            int counter = 1;
            while (nameExists(newName)) {
                newName = "New Connection " + counter++;
            }

            TextInputDialog dialog = new TextInputDialog(newName);
            dialog.setTitle("New Connection");
            dialog.setHeaderText("Enter connection name:");

            // Set an icon for the dialog's title bar
            Stage dialogStage = (Stage) dialog.getDialogPane().getScene().getWindow();
            dialogStage.getIcons().add(ICON_DEFAULT); // Replace with your image path

            dialog.showAndWait().ifPresent(this::addConnectionWithName);
        }
    }

    private void addConnectionWithName(String name) {
        if (StringUtils.isNotBlank(name)) {
            String finalName = name;
            int cnt = 1;
            while (nameExists(finalName)) {
                finalName = name + " Copy " + cnt++;
            }
            addConnection(finalName, "", "BUNDLED", "Java 17", "", "512m", "", false, "", false, "", false);
        }
    }

    private void saveCurrentConnection() {
        if (!isLaunching) {
            Connection selected = connectionsTableView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                updateConnectionFromUI(selected);
                connectionsTableView.refresh();
                saveButton.setDisable(true);
                saveConnections();
            }
        }
    }

    private void saveAsNewConnection() {
        if (!isLaunching) {
            TextInputDialog dialog = new TextInputDialog("New Connection");
            dialog.setTitle("Save Connection");
            dialog.setHeaderText("Enter new connection name:");
            // Set an icon for the dialog's title bar
            Stage dialogStage = (Stage) dialog.getDialogPane().getScene().getWindow();
            dialogStage.getIcons().add(ICON_DEFAULT); // Replace with your image path

            dialog.showAndWait().ifPresent(name -> {
                if (StringUtils.isNotBlank(name)) {
                    String finalName = name;
                    int cnt = 1;
                    while (nameExists(finalName)) {
                        finalName = name + " Copy " + cnt++;
                    }
                    Connection newConn = new Connection(UUID.randomUUID().toString(), finalName,
                            addressTextField.getText(), getJavaHome(), bundledJavaCombo.getValue().toString(),
                            "", heapSizeCombo.getValue().toString(), "", showConsoleCheckBox.isSelected(),
                            false, "", false, "", false);
                    connectionsList.add(newConn);
                    connectionsTableView.refresh();
                    saveConnections();
                    tableSelectionModel.select(newConn);
                }
            });
        }
    }

    private void deleteCurrentConnection() {
        if (!isLaunching) {
            Connection selected = connectionsTableView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                int index = connectionsTableView.getSelectionModel().getSelectedIndex();
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle("Delete Connection");
                alert.setHeaderText("Confirm deletion?");

                // Set an icon for the dialog's title bar
                Stage dialogStage = (Stage) alert.getDialogPane().getScene().getWindow();
                dialogStage.getIcons().add(ICON_DEFAULT); //

                alert.showAndWait().ifPresent(response -> {
                    if (response == ButtonType.OK) {
                        connectionsList.remove(selected);
                        connectionsTableView.refresh();
                        saveConnections();
                        if (index < connectionsList.size()) {
                            tableSelectionModel.select(index);
                        } else if (!connectionsList.isEmpty()) {
                            tableSelectionModel.select(connectionsList.size() - 1);
                        }
                    }
                });
            }
        }
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

                JavaConfig javaConfig = new JavaConfig(heapSizeCombo.getValue().toString(), this.bundledJavaCombo.getValue().toString());

                CodeBase codeBase = download.handle(this);

                updateProgressText("Launching......");

                ProcessLauncher process = new ProcessLauncher();
                process.launch(javaConfig, codeBase);

                Thread.sleep(5000);

                Platform.runLater(() -> {
                    progressText.setText("Launch complete!");
                    if (closeWindowCheckBox.isSelected()) {
                        primaryStage.close();
                    } else {
                        resetUI();
                    }
                });

            } catch (Exception e ) {
                Platform.runLater(() -> {
                    showErrorDialog(e, "Launch Failed");
                    resetUI();
                });
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
        addressTextField.setDisable(!enabled);
        bundledJavaCombo.setDisable(!enabled);
        heapSizeCombo.setDisable(!enabled);
        showConsoleCheckBox.setDisable(!enabled);
        closeWindowCheckBox.setDisable(!enabled);
        newButton.setDisable(!enabled);
        saveButton.setDisable(!enabled);
        saveAsButton.setDisable(!enabled);
        deleteButton.setDisable(!enabled);
    }

    private void resetUI() {
        progressBar.setProgress(0.0);
        progressIndicator.setProgress(-1.0);
        progressText.setText("");
        setUIEnabled(true);
        setProgressControlsVisible(false);
        isLaunching = false;
    }

    private void setProgressControlsVisible(boolean visible) {
        progressBar.setVisible(visible);
        progressIndicator.setVisible(visible);
        progressText.setVisible(visible);
        launchButton.setVisible(!visible);
        cancelButton.setVisible(visible);
    }

    private boolean nameExists(String name) {
        return this.connectionsList.stream().anyMatch(conn -> StringUtils.equalsIgnoreCase(name, conn.getName()));
    }

    private String getJavaHome() {
        return "BUNDLED";
    }

    private List<Connection> loadConnections() {
        List<Connection> connections = new ArrayList<>();
        File connectionsFile = new File("data/connections.json");
        if (connectionsFile.exists()) {
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                connections = objectMapper.readValue(connectionsFile, new TypeReference<List<Connection>>() {});
            } catch (IOException e) {
                System.out.println("Unable to load connections from file. Error: " + e.getMessage());
//                showAlert("Error", "Unable to load connections from file. Error: " + e.getMessage());
            }
        }
        return connections;
    }

    private void saveConnections() {
        File directory = new File("data");
        if (!directory.exists()) {
            directory.mkdirs();
        }

        File connectionsFile = new File("data/connections.json");
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.writeValue(connectionsFile, this.connectionsList);
        } catch (IOException e) {
            showAlert(e.getMessage());
        }
    }

    private void addConnection(String name, String address, String javaHome, String javaHomeBundledValue, String javaFxHome,
                               String heapSize, String icon, boolean showJavaConsole, String sslProtocols,
                               boolean sslProtocolsCustom, String sslCipherSuites, boolean useLegacyDHSettings) {
        Connection conn = new Connection(UUID.randomUUID().toString(), name, address, javaHome, javaHomeBundledValue,
                javaFxHome, heapSize, icon, showJavaConsole, sslProtocolsCustom, sslProtocols, false, sslCipherSuites, useLegacyDHSettings);

        this.connectionsList.add(conn);
        this.connectionsTableView.refresh();

        saveConnections();

        this.tableSelectionModel.select(conn);
        this.saveButton.setDisable(true);
    }

    private Image getIconImage(String iconName) {
        return new Image(getClass().getResourceAsStream("/images/launcher_32.png"), 20, 20, true, true);

//        try {
//            return new Image(getClass().getResourceAsStream("/images/" + iconName), 20, 20, true, true);
//        } catch (Exception e) {
//            return new Image(getClass().getResourceAsStream("/images/launcher_32.png"), 20, 20, true, true);
//        }
    }

    private void updateUIFromConnection(Connection conn) {
        addressTextField.setText(conn.getAddress());
        bundledJavaCombo.getSelectionModel().select(new BundledJava("", conn.getJavaHomeBundledValue()));
        showConsoleCheckBox.setSelected(conn.isShowJavaConsole());

        String heapSize = conn.getHeapSize();
        for (HeapMemory e : this.heapSizeCombo.getItems()) {
            if (StringUtils.equalsIgnoreCase(e.getValue(), heapSize)) {
                this.heapSizeCombo.getSelectionModel().select(e);
                break;
            }
        }
    }

    private void updateConnectionFromUI(Connection conn) {
        conn.setAddress(this.addressTextField.getText());
        conn.setJavaHome(getJavaHome());
        conn.setJavaHomeBundledValue(this.bundledJavaCombo.getValue().toString());
        conn.setJavaFxHome("");
        conn.setHeapSize(this.heapSizeCombo.getValue().toString());
        conn.setIcon("");
        conn.setShowJavaConsole(showConsoleCheckBox.isSelected());
        conn.setSslProtocolsCustom(false);
        conn.setSslProtocols("");
        conn.setSslCipherSuitesCustom(false);
        conn.setSslCipherSuites("");
        conn.setUseLegacyDHSettings(false);
    }

    private void updateSaveButtonState() {
        if (isLaunching) {
            this.saveButton.setDisable(true);
            return;
        }
        Connection selected = this.connectionsTableView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            this.saveButton.setDisable(true);
            return;
        }

        boolean unchanged =
                StringUtils.equals(selected.getAddress(), this.addressTextField.getText()) &&
                        StringUtils.equals(selected.getJavaHome(), getJavaHome()) &&
                        StringUtils.equals(selected.getJavaHomeBundledValue(), this.bundledJavaCombo.getValue().toString()) &&
                        StringUtils.equals(selected.getHeapSize(), this.heapSizeCombo.getValue().toString()) &&
                        selected.isShowJavaConsole() == showConsoleCheckBox.isSelected();

        this.saveButton.setDisable(unchanged);
    }

    public void showAlert(String err){
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(err);
        alert.initOwner(primaryStage);
        alert.showAndWait();
    }

    private void showErrorDialog(Throwable t, String header) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(header);
        alert.setResizable(true);
        alert.setHeight(550.0);
        alert.setWidth(550.0);
        TextArea textArea = new TextArea(ExceptionUtils.getStackTrace(t));
        textArea.setEditable(false);
        alert.getDialogPane().setContent(textArea);
        alert.initOwner(this.primaryStage);
        alert.show();
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
