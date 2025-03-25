package com.innovarhealthcare.launcher;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.innovarhealthcare.launcher.interfaces.Progress;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTreeTableCell;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Callback;
import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.image.Image;
import javafx.scene.text.Text;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

public class BridgeLinkLauncher extends Application implements Progress {
    private static final boolean DEVELOP = false;
    private static final String VERSION = DEVELOP ? "Development 1.0.1" : "1.0.1";
    private static final Image ICON_DEFAULT = new Image("/images/logo.png");

    private final ObservableList<Connection> connectionsList = FXCollections.observableArrayList();

    private TreeTableView<Connection> connectionsTreeTableView;
    private TreeTableView.TreeTableViewSelectionModel<Connection> treeSelectionModel;

    private TextField filterField;
    private TextField groupTextField;
    private TextField addressTextField;
    private TextField usernameTextField;
    private PasswordField passwordField;
    private Button launchButton;
    private ComboBox<BundledJava> bundledJavaCombo;
    private ComboBox<HeapMemory> heapSizeCombo;
    private CheckBox showConsoleCheckBox;
    private Separator separator2;
    private Text progressText;
    private ProgressBar progressBar;
    private ProgressIndicator progressIndicator;
    private Button cancelButton;
    private CheckBox closeWindowCheckBox;
    private Button newButton;
    private Button saveButton;
    private Button duplicateButton;
    private Button deleteButton;
    private Button importButton;
    private Button exportButton;
    private Thread launchThread;
    private volatile DownloadJNLP currentDownload;
    private volatile boolean isLaunching = false;
    private Stage primaryStage;
    private String appDir;     // Application directory
    private File dataFolder;   // "data" folder within appDir
    private File cacheFolder;  // New "cache" folder within appDir

    @Override
    public void start(Stage stage) {
        primaryStage = stage;

        stage.setTitle("BridgeLink Administrator Launcher (" + VERSION + ")");

        try {
            stage.getIcons().add(ICON_DEFAULT);
        } catch (Exception e) {
            // Handle icon loading failure
        }

        initializeDirectories();

        VBox root = new VBox(15);
        root.setPadding(new Insets(15));

        // Connections Section with Buttons (unchanged)
        VBox connectionsSection = new VBox(20);
        connectionsSection.setPadding(new Insets(10));
        connectionsSection.setAlignment(Pos.TOP_CENTER);

        // Table buttons
        HBox tableButtons = new HBox(10);

        filterField = new TextField();
        filterField.setPromptText("Filter connections...");
        filterField.setPrefWidth(200);
        filterField.setMinWidth(200);
        filterField.setMaxWidth(200);
        filterField.textProperty().addListener((obs, oldVal, newVal) -> updateTreeTableWithFilter(newVal));

        newButton = new Button("New");
        newButton.setOnAction(e -> createNewConnection());
        saveButton = new Button("Save");
        saveButton.setOnAction(e -> saveCurrentConnection());
        duplicateButton = new Button("Duplicate");
        duplicateButton.setOnAction(e -> duplicateConnection());
        deleteButton = new Button("Delete");
        deleteButton.setDisable(true);
        deleteButton.setOnAction(e -> deleteCurrentConnection());
        tableButtons.getChildren().addAll(filterField, newButton, saveButton, duplicateButton, deleteButton);
        tableButtons.setAlignment(Pos.CENTER_LEFT);

        // TreeTableView setup
        connectionsTreeTableView = new TreeTableView<>();
        connectionsTreeTableView.setPlaceholder(new Label("No saved connections"));
        connectionsTreeTableView.setEditable(true);
        VBox.setVgrow(connectionsTreeTableView, Priority.ALWAYS);

        // Group column
        TreeTableColumn<Connection, String> groupColumn = new TreeTableColumn<>("Group");
        groupColumn.setCellValueFactory(new Callback<TreeTableColumn.CellDataFeatures<Connection, String>, ObservableValue<String>>() {
            @Override
            public ObservableValue<String> call(TreeTableColumn.CellDataFeatures<Connection, String> cellData) {
                Connection conn = cellData.getValue().getValue();
                String groupValue = (conn != null && conn.getGroup() != null) ? conn.getGroup() : "";
                return new SimpleStringProperty(groupValue);
            }
        });
        groupColumn.setPrefWidth(150);  // Fixed width for group names
        groupColumn.setMinWidth(100);   // Minimum to ensure readability
        groupColumn.setMaxWidth(200);   // Maximum to prevent over-expansion
        groupColumn.setSortable(false);

        // Name column
        TreeTableColumn<Connection, String> nameColumn = new TreeTableColumn<>("Connections");
        nameColumn.setCellValueFactory(new Callback<TreeTableColumn.CellDataFeatures<Connection, String>, ObservableValue<String>>() {
            @Override
            public ObservableValue<String> call(TreeTableColumn.CellDataFeatures<Connection, String> cellData) {
                Connection conn = cellData.getValue().getValue();
                String nameValue = (conn != null && conn.getName() != null) ? conn.getName() : "";
                return new SimpleStringProperty(nameValue);
            }
        });
        nameColumn.setCellFactory(TextFieldTreeTableCell.forTreeTableColumn());
        nameColumn.setOnEditCommit(event -> {
            String newName = StringUtils.trim(event.getNewValue());
            boolean exists = connectionsList.stream().anyMatch(conn ->
                    !StringUtils.equals(conn.getId(), event.getRowValue().getValue().getId()) &&
                            StringUtils.equalsIgnoreCase(conn.getName(), newName));
            if (!exists) event.getRowValue().getValue().setName(newName);
            connectionsTreeTableView.refresh();
            saveConnections();
        });
        nameColumn.setPrefWidth(300);  // Initial preferred width (will grow)
        nameColumn.setMinWidth(150);   // Minimum for usability
        nameColumn.setSortable(false);
        // No maxWidth - allow it to expand
        connectionsTreeTableView.setColumnResizePolicy(TreeTableView.CONSTRAINED_RESIZE_POLICY); // Ensure nameColumn takes remaining space

        // Id column
        TreeTableColumn<Connection, String> idColumn = new TreeTableColumn<>("Id");
        idColumn.setCellValueFactory(new Callback<TreeTableColumn.CellDataFeatures<Connection, String>, ObservableValue<String>>() {
            @Override
            public ObservableValue<String> call(TreeTableColumn.CellDataFeatures<Connection, String> cellData) {
                Connection conn = cellData.getValue().getValue();
                String idValue = (conn != null && conn.getId() != null) ? conn.getId() : "";
                return new SimpleStringProperty(idValue);
            }
        });
        idColumn.setVisible(false);

        connectionsTreeTableView.getColumns().addAll(groupColumn, nameColumn, idColumn);
        connectionsList.addAll(loadConnections());

        // Build tree structure
        TreeItem<Connection> rootTree = new TreeItem<>(null); // Root is invisible
        rootTree.setExpanded(true);
        connectionsTreeTableView.setRoot(rootTree);
        connectionsTreeTableView.setShowRoot(false);

        updateTreeTable();

        // Selection model
        treeSelectionModel = connectionsTreeTableView.getSelectionModel();
        treeSelectionModel.setSelectionMode(SelectionMode.SINGLE);
        treeSelectionModel.selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            Connection selectedConn = newVal != null ? newVal.getValue() : null;
            boolean isConnection = selectedConn != null && selectedConn.getAddress() != null;

            if (isConnection) {
                groupTextField.setText(selectedConn.getGroup() != null ? selectedConn.getGroup() : "");
                addressTextField.setText(selectedConn.getAddress());
                updateUIFromConnection(selectedConn);
            } else {
                // Clear fields when a group is selected
                groupTextField.setText("");
                addressTextField.setText("");
                usernameTextField.setText("");
                passwordField.setText("");
                showConsoleCheckBox.setSelected(false);
                bundledJavaCombo.getSelectionModel().select(0);
                heapSizeCombo.getSelectionModel().select(1);
            }

            // always disable Save button
            saveButton.setDisable(true);

            // Disable Duplicate, Delete, Launch and input fields when a group is selected
            duplicateButton.setDisable(!isConnection);
            deleteButton.setDisable(!isConnection);
            launchButton.setDisable(!isConnection);
            groupTextField.setDisable(!isConnection);
            addressTextField.setDisable(!isConnection);
            usernameTextField.setDisable(!isConnection);
            passwordField.setDisable(!isConnection);
            bundledJavaCombo.setDisable(!isConnection);
            heapSizeCombo.setDisable(!isConnection);
            showConsoleCheckBox.setDisable(!isConnection);

            exportButton.setDisable(connectionsList.isEmpty());
        });

        // right of layout button
        VBox rightButtons = new VBox(10);
        importButton = new Button("Import");
        importButton.setOnAction(e -> importConnections());
        exportButton = new Button("Export");
        exportButton.setOnAction(e -> exportConnections());
        exportButton.setDisable(true); // Disabled until a connection is selected
        rightButtons.getChildren().addAll(importButton, exportButton);
        rightButtons.setAlignment(Pos.TOP_CENTER);

        HBox tableArea = new HBox(10);
        tableArea.getChildren().addAll(connectionsTreeTableView, rightButtons);
        HBox.setHgrow(connectionsTreeTableView, Priority.ALWAYS);

        connectionsSection.getChildren().addAll(tableButtons, tableArea);

        // Configuration Section (Modified: Java Home and Max Heap Size on same line)
        VBox configBox = new VBox(10);

        // Group row
        HBox groupRow = new HBox(10);
        Label groupLabel = new Label("Group:");
        groupTextField = new TextField();
        groupTextField.textProperty().addListener((obs, oldVal, newVal) -> updateSaveButtonState());
        groupRow.getChildren().addAll(groupLabel, groupTextField);
        HBox.setHgrow(groupTextField, Priority.ALWAYS);

        // Address row
        HBox addressRow = new HBox(10);
        Label addressLabel = new Label("Address:");
        addressTextField = new TextField("https://localhost:8443");
        addressTextField.textProperty().addListener((obs, oldVal, newVal) -> updateSaveButtonState());
        addressRow.getChildren().addAll(addressLabel, addressTextField);
        HBox.setHgrow(addressTextField, Priority.ALWAYS);

        // Credential row
        HBox credentialsRow = new HBox(10);
        Label usernameLabel = new Label("Username:");
        usernameTextField = new TextField();
        usernameTextField.textProperty().addListener((obs, oldVal, newVal) -> updateSaveButtonState());
        Label passwordLabel = new Label("Password:");
        passwordField = new PasswordField();
        passwordField.textProperty().addListener((obs, oldVal, newVal) -> updateSaveButtonState());
        credentialsRow.getChildren().addAll(usernameLabel, usernameTextField, passwordLabel, passwordField);
        HBox.setHgrow(usernameTextField, Priority.ALWAYS);
        HBox.setHgrow(passwordField, Priority.ALWAYS);

        // Java Config row
        HBox javaHeapRow = new HBox(10); // Combined Java Home and Max Heap Size
        Label javaHomeLabel = new Label("Java Home:");
        bundledJavaCombo = new ComboBox<>(FXCollections.observableArrayList(
                new BundledJava("", "Java 17"),
                new BundledJava("", "Java 8")
        ));
        bundledJavaCombo.getSelectionModel().select(0);
        bundledJavaCombo.setOnAction(e -> updateSaveButtonState());
        Label heapSizeLabel = new Label("Max Heap Size:");
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
        Label consoleLabel = new Label("Show Java Console:");
        showConsoleCheckBox = new CheckBox();
        showConsoleCheckBox.setSelected(false);
        showConsoleCheckBox.setOnAction(e -> updateSaveButtonState());
        consoleRow.getChildren().addAll(consoleLabel, showConsoleCheckBox);

        configBox.getChildren().addAll(groupRow, addressRow, credentialsRow, javaHeapRow, consoleRow);

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

        // Select first connection
        if (!connectionsList.isEmpty()) {
            TreeItem<Connection> firstGroup = connectionsTreeTableView.getRoot().getChildren().get(0);
            if (firstGroup != null && !firstGroup.getChildren().isEmpty()) {
                treeSelectionModel.select(firstGroup.getChildren().get(0));
            }
        }

        Scene scene = new Scene(root, 800, 600);
        stage.setScene(scene);
        stage.show();

        newButton.requestFocus();
        // Check write permissions to "data" and "cache" folders after showing the application
        checkWritePermissions(stage);
    }

    private void initializeDirectories() {
        // Get the application directory (where the JAR or class file resides)
        try {
            String jarPath = BridgeLinkLauncher.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
            appDir = new File(jarPath).getParent(); // Parent directory of the JAR/class file
            appDir = URLDecoder.decode(appDir, StandardCharsets.UTF_8.name()); // Decode path
        } catch (Exception e) {
            appDir = System.getProperty("user.dir"); // Fallback to user.dir if detection fails
            System.err.println("Failed to determine application directory: " + e.getMessage());
        }

        if (!getParameters().getRaw().isEmpty()) {
            appDir = getParameters().getRaw().get(0); // Override with first parameter if provided
        }

        // Set up data and cache folders
        dataFolder = new File(appDir, "data");
        cacheFolder = new File(appDir, "cache");
    }

    private void checkWritePermissions(Stage stage) {
        // Check "data" folder
        StringBuilder errorMessage = new StringBuilder();

        if (!dataFolder.exists()) {
            try {
                dataFolder.mkdirs(); // Create the "data" folder if it doesn't exist
            } catch (SecurityException e) {
                errorMessage.append("Cannot create 'data' folder in: ").append(appDir)
                        .append("\nError: ").append(e.getMessage()).append("\n");
            }
        }

        if (dataFolder.exists() && !dataFolder.isDirectory()) {
            errorMessage.append("'data' path exists but is not a directory: ")
                    .append(dataFolder.getAbsolutePath()).append("\n");
        } else if (dataFolder.exists()) {
            try {
                File tempFile = File.createTempFile("test", ".tmp", dataFolder);
                tempFile.delete(); // Clean up
            } catch (IOException e) {
                errorMessage.append("No write permission in 'data' folder: ")
                        .append(dataFolder.getAbsolutePath())
                        .append("\nError: ").append(e.getMessage()).append("\n");
            }
        }

        // Check "cache" folder
        if (!cacheFolder.exists()) {
            try {
                cacheFolder.mkdirs(); // Create the "cache" folder if it doesn't exist
            } catch (SecurityException e) {
                errorMessage.append("Cannot create 'cache' folder in: ").append(appDir)
                        .append("\nError: ").append(e.getMessage()).append("\n");
            }
        }

        if (cacheFolder.exists() && !cacheFolder.isDirectory()) {
            errorMessage.append("'cache' path exists but is not a directory: ")
                    .append(cacheFolder.getAbsolutePath()).append("\n");
        } else if (cacheFolder.exists()) {
            try {
                File tempFile = File.createTempFile("test", ".tmp", cacheFolder);
                tempFile.delete(); // Clean up
            } catch (IOException e) {
                errorMessage.append("No write permission in 'cache' folder: ")
                        .append(cacheFolder.getAbsolutePath())
                        .append("\nError: ").append(e.getMessage()).append("\n");
            }
        }

        // Show alert if there are any errors
        if (errorMessage.length() > 0) {
            showWritePermissionAlert(stage, errorMessage.toString());
        }
    }

    private void showWritePermissionAlert(Stage stage, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.initOwner(stage);
        alert.setTitle("Write Permission Error");
        alert.setHeaderText("Cannot write to required folders");
        alert.setContentText(message + "\nSome features (e.g., saving connections or caching) may not work. " +
                "Please ensure the 'data' and 'cache' folders are writable.");
        alert.showAndWait();
    }

    private void updateTreeTable() {
        updateTreeTableWithFilter(filterField != null ? filterField.getText() : "");
    }

    private void updateTreeTableWithFilter(String filter) {
        TreeItem<Connection> root = connectionsTreeTableView.getRoot();
        if (root == null) {
            root = new TreeItem<>(null);
            root.setExpanded(true);
            connectionsTreeTableView.setRoot(root);
            connectionsTreeTableView.setShowRoot(false);
        }
        root.getChildren().clear();

        Map<String, TreeItem<Connection>> groupItems = new HashMap<>();
        String filterLower = filter.toLowerCase().trim();

        for (Connection conn : connectionsList) {
            String groupName = StringUtils.isBlank(conn.getGroup()) ? "Ungrouped" : conn.getGroup();
            String name = conn.getName() != null ? conn.getName() : "";

            // Filter by name or group
            if (filterLower.isEmpty() || name.toLowerCase().contains(filterLower) || groupName.toLowerCase().contains(filterLower)) {
                TreeItem<Connection> groupItem = groupItems.computeIfAbsent(groupName,
                        k -> {
                            Connection groupConn = new Connection(null, "", null, null, null, null, null,
                                    null, false, false, null, false, null, false, null, null, groupName);
                            TreeItem<Connection> item = new TreeItem<>(groupConn);
                            item.setExpanded(true);
                            return item;
                        });

                TreeItem<Connection> connItem = new TreeItem<>(conn);
                groupItem.getChildren().add(connItem);

                if (!root.getChildren().contains(groupItem)) {
                    root.getChildren().add(groupItem);
                }
            }
        }

        // Update placeholder
        connectionsTreeTableView.setPlaceholder(new Label(
                filter.isEmpty() ? "No saved connections" : "No matching connections"));
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

            dialog.showAndWait().ifPresent(name -> {
                addConnectionWithName(name); // Add the new connection
                updateTreeTable();           // Rebuild the tree
                // Find and select the new connection
                Connection newConn = connectionsList.stream()
                        .filter(conn -> conn.getName().equals(name))
                        .findFirst()
                        .orElse(null);
                if (newConn != null) {
                    TreeItem<Connection> newItem = findTreeItem(newConn);
                    if (newItem != null) {
                        treeSelectionModel.select(newItem);
                    }
                }
            });
        }
    }

    private void addConnectionWithName(String name) {
        if (StringUtils.isNotBlank(name)) {
            String finalName = name;
            int cnt = 1;
            while (nameExists(finalName)) {
                finalName = name + " Copy " + cnt++;
            }
            addConnection(finalName, "", "BUNDLED", "Java 17", "", "512m", "", false, "", false, "", false, "", "", "");
        }
    }

    private void saveCurrentConnection() {
        if (!isLaunching) {
            TreeItem<Connection> selectedItem = connectionsTreeTableView.getSelectionModel().getSelectedItem();
            if (selectedItem != null && selectedItem.getValue().getAddress() != null) {
                Connection currentConnection = selectedItem.getValue();

                updateConnectionFromUI(currentConnection);
                updateTreeTable();
                connectionsTreeTableView.refresh();
                saveButton.setDisable(true);
                saveConnections();

                // Re-select the saved connection
                TreeItem<Connection> newSelectedItem = findTreeItem(currentConnection);
                if (newSelectedItem != null) {
                    treeSelectionModel.select(newSelectedItem);
                }
            }
        }
    }

    private void duplicateConnection() {
        if (!isLaunching) {
            TextInputDialog dialog = new TextInputDialog("New Connection");
            dialog.setTitle("Duplicate Connection");
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
                    // [Name conflict resolution]
                    Connection newConn = new Connection(UUID.randomUUID().toString(), finalName,
                            addressTextField.getText(), getJavaHome(), bundledJavaCombo.getValue().toString(),
                            "", heapSizeCombo.getValue().toString(), "", showConsoleCheckBox.isSelected(),
                            false, "", false, "", false, usernameTextField.getText(), passwordField.getText(), groupTextField.getText());
                    connectionsList.add(newConn);
                    updateTreeTable(); // Update tree after adding
                    saveConnections();
                    // Select new connection
                    treeSelectionModel.select(findTreeItem(newConn));
                }
            });
        }
    }

    private void deleteCurrentConnection() {
        if (!isLaunching) {
            TreeItem<Connection> selectedItem = connectionsTreeTableView.getSelectionModel().getSelectedItem();
            if (selectedItem != null && selectedItem.getValue().getAddress() != null) { // Ensure it's a connection
                Connection selected = selectedItem.getValue();
                String originalGroup = StringUtils.isBlank(selected.getGroup()) ? "Ungrouped" : selected.getGroup(); // Capture group before deletion

                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle("Delete Connection");
                alert.setHeaderText("Confirm deletion?");
                Stage dialogStage = (Stage) alert.getDialogPane().getScene().getWindow();
                dialogStage.getIcons().add(ICON_DEFAULT);

                alert.showAndWait().ifPresent(response -> {
                    if (response == ButtonType.OK) {
                        connectionsList.remove(selected);
                        updateTreeTable();
                        saveConnections();

                        // Selection logic after deletion
                        if (!connectionsList.isEmpty()) {
                            // Find the new group item matching the original group
                            TreeItem<Connection> newGroupItem = null;
                            for (TreeItem<Connection> groupItem : connectionsTreeTableView.getRoot().getChildren()) {
                                if (StringUtils.equals(groupItem.getValue().getGroup(), originalGroup)) {
                                    newGroupItem = groupItem;
                                    break;
                                }
                            }

                            if (newGroupItem != null && !newGroupItem.getChildren().isEmpty()) {
                                // Select the first item in the same group
                                treeSelectionModel.select(newGroupItem.getChildren().get(0));
                            } else if (!connectionsTreeTableView.getRoot().getChildren().isEmpty()) {
                                // Fall back to first item in first group if original group is gone
                                TreeItem<Connection> firstGroup = connectionsTreeTableView.getRoot().getChildren().get(0);
                                if (!firstGroup.getChildren().isEmpty()) {
                                    treeSelectionModel.select(firstGroup.getChildren().get(0));
                                } else {
                                    treeSelectionModel.clearSelection();
                                }
                            } else {
                                treeSelectionModel.clearSelection();
                            }
                        } else {
                            // List is empty, clear selection
                            treeSelectionModel.clearSelection();
                        }
                    }
                });
            }
        }
    }

    private TreeItem<Connection> findTreeItem(Connection conn) {
        for (TreeItem<Connection> groupItem : connectionsTreeTableView.getRoot().getChildren()) {
            for (TreeItem<Connection> item : groupItem.getChildren()) {
                if (item.getValue() == conn) return item;
            }
        }
        return null;
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
                updateProgressText("Downloading JNLP from " + host);

                DownloadJNLP download = new DownloadJNLP(host, cacheFolder);
                currentDownload = download;

                JavaConfig javaConfig = new JavaConfig(heapSizeCombo.getValue().toString(), this.bundledJavaCombo.getValue().toString());
                Credential credential = new Credential(StringUtils.trim(usernameTextField.getText()), StringUtils.trim(passwordField.getText()));
                CodeBase codeBase = download.handle(this);
                currentDownload = null;

                ProcessLauncher process = new ProcessLauncher();
                updateProgressText("Starting application...");
                process.launch(javaConfig, credential, codeBase, showConsoleCheckBox.isSelected());

                updateProgressText("Application launched successfully");

                Thread.sleep(1000);
                Platform.runLater(() -> {
                    if (closeWindowCheckBox.isSelected()) {
                        primaryStage.close();
                    } else {
                        resetUI();
                    }
                });

            } catch (InterruptedException e) {
                Platform.runLater(() -> {
                    progressText.setText("Launch cancelled");
                    resetUI();
                });
            } catch (Exception e ) {
                Platform.runLater(() -> {
                    showErrorDialog(e, "Launch Failed");
                    resetUI();
                });
            } finally {
                currentDownload = null;
            }
        }, "Launch Thread");
        launchThread.start();
    }

    private void cancelLaunch() {
        if (launchThread != null && launchThread.isAlive()) {
            Platform.runLater(() -> progressText.setText("Cancelling..."));
            launchThread.interrupt();
            if (currentDownload != null) {
                currentDownload.cancel();
            }

            // Give a moment for cancellation to propagate
            try {
                launchThread.join(1000); // Wait up to 1 second for thread to exit
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Restore interrupted status
            }
        }
    }

    private void importConnections() {
        if (isLaunching) return;

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Import Connections");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("JSON Files", "*.json")
        );
        File file = fileChooser.showOpenDialog(primaryStage);

        if (file != null) {
            try {
                String content = new String(Files.readAllBytes(file.toPath()));
                ObjectMapper objectMapper = new ObjectMapper();
                List<Connection> importedConnections = objectMapper.readValue(content,
                        new TypeReference<List<Connection>>() {});

                // Handle duplicate names
                for (Connection conn : importedConnections) {
                    String baseName = conn.getName();
                    String newName = baseName;
                    int counter = 1;
                    while (nameExists(newName)) {
                        newName = baseName + " (Imported " + counter++ + ")";
                    }
                    conn.setName(newName);
                    conn.setId(UUID.randomUUID().toString()); // Generate new unique ID
                    connectionsList.add(conn);
                }

                updateTreeTable();
                saveConnections();
            } catch (IOException e) {
                showAlert("Failed to import connections: " + e.getMessage());
            }
        }
    }

    // New method to export selected connection
    private void exportConnections() {
        if (isLaunching || connectionsList.isEmpty()) return;

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export All Connections");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("JSON Files", "*.json")
        );
        fileChooser.setInitialFileName("all_connections.json"); // Default name for all connections
        File file = fileChooser.showSaveDialog(primaryStage);

        if (file != null) {
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                objectMapper.writeValue(file, connectionsList); // Export entire list
            } catch (IOException e) {
                showAlert("Failed to export connections: " + e.getMessage());
            }
        }
    }

    private void setUIEnabled(boolean enabled) {
        TreeItem<Connection> selectedItem = connectionsTreeTableView.getSelectionModel().getSelectedItem();
        boolean isConnectionSelected = selectedItem != null && selectedItem.getValue() != null &&
                selectedItem.getValue().getAddress() != null;
        boolean finalEnabled = enabled && isConnectionSelected;

        // Disable input fields and specific buttons based on selection
        groupTextField.setDisable(!finalEnabled);
        addressTextField.setDisable(!finalEnabled);
        usernameTextField.setDisable(!finalEnabled);
        passwordField.setDisable(!finalEnabled);
        bundledJavaCombo.setDisable(!finalEnabled);
        heapSizeCombo.setDisable(!finalEnabled);
        showConsoleCheckBox.setDisable(!finalEnabled);
        saveButton.setDisable(!finalEnabled);
        duplicateButton.setDisable(!finalEnabled);
        deleteButton.setDisable(!finalEnabled);
        launchButton.setDisable(!finalEnabled);

        // Other UI elements only disabled during launch
        boolean launchEnabled = enabled;
        closeWindowCheckBox.setDisable(!launchEnabled);
        newButton.setDisable(!launchEnabled);
        importButton.setDisable(!launchEnabled);
        exportButton.setDisable(!launchEnabled || connectionsList.isEmpty());
        connectionsTreeTableView.setDisable(!launchEnabled);
    }

    private void resetUI() {
        progressBar.setProgress(0.0);
        progressIndicator.setProgress(-1.0);
        progressText.setText("");
        setUIEnabled(true);
        setProgressControlsVisible(false);
        isLaunching = false;

        // need update Save button after reset UI
        // Apply after launcher
        updateSaveButtonState();
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
        File connectionsFile = new File(dataFolder, "connections.json"); // Use dataFolder directly
        if (connectionsFile.exists()) {
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                connections = objectMapper.readValue(connectionsFile, new TypeReference<List<Connection>>() {});
            } catch (IOException e) {
                showAlert("Unable to load connections from file: " + connectionsFile.getAbsolutePath() +
                        ". Error: " + e.getMessage());
            }
        }
        return connections;
    }

    private void saveConnections() {
        // Ensure the "data" folder exists
        if (!dataFolder.exists()) {
            dataFolder.mkdirs(); // Create the "data" folder if it doesn't exist
        }

        File connectionsFile = new File(dataFolder, "connections.json");
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.writeValue(connectionsFile, this.connectionsList);
        } catch (IOException e) {
            showAlert(e.getMessage());
        }
    }

    private void addConnection(String name, String address, String javaHome, String javaHomeBundledValue,
                               String javaFxHome, String heapSize, String icon, boolean showJavaConsole,
                               String sslProtocols, boolean sslProtocolsCustom, String sslCipherSuites,
                               boolean useLegacyDHSettings, String username, String password, String group) {
        Connection conn = new Connection(UUID.randomUUID().toString(), name, address, javaHome,
                javaHomeBundledValue, javaFxHome, heapSize, icon, showJavaConsole,
                sslProtocolsCustom, sslProtocols, false, sslCipherSuites, useLegacyDHSettings,
                username, password, group);
        this.connectionsList.add(conn);
        updateTreeTable(); // Update tree after adding
        saveConnections();
        this.treeSelectionModel.select(findTreeItem(conn));
        this.saveButton.setDisable(true);
    }

    private void updateUIFromConnection(Connection conn) {
        groupTextField.setText(conn.getGroup() != null ? conn.getGroup() : "");
        addressTextField.setText(conn.getAddress());
        usernameTextField.setText(conn.getUsername());
        passwordField.setText(conn.getPassword());
        showConsoleCheckBox.setSelected(conn.isShowJavaConsole());

        String javaHomeBundledValue = conn.getJavaHomeBundledValue();
        for (BundledJava e : this.bundledJavaCombo.getItems()) {
            if (StringUtils.equalsIgnoreCase(e.getVersion(), javaHomeBundledValue)) {
                this.bundledJavaCombo.getSelectionModel().select(e);
                break;
            }
        }

        String heapSize = conn.getHeapSize();
        for (HeapMemory e : this.heapSizeCombo.getItems()) {
            if (StringUtils.equalsIgnoreCase(e.getValue(), heapSize)) {
                this.heapSizeCombo.getSelectionModel().select(e);
                break;
            }
        }
    }

    private void updateConnectionFromUI(Connection conn) {
        conn.setGroup(this.groupTextField.getText());
        conn.setAddress(this.addressTextField.getText());
        conn.setUsername(this.usernameTextField.getText());
        conn.setPassword(this.passwordField.getText());
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

        TreeItem<Connection> selectedItem = connectionsTreeTableView.getSelectionModel().getSelectedItem();
        if (selectedItem == null) {
            this.saveButton.setDisable(true);
            return;
        }

        Connection selected = selectedItem.getValue();
        if (selected == null) {
            this.saveButton.setDisable(true);
            return;
        }

        boolean unchanged =
                StringUtils.equals(selected.getGroup(), groupTextField.getText()) &&
                StringUtils.equals(selected.getAddress(), this.addressTextField.getText()) &&
                StringUtils.equals(selected.getUsername(), this.usernameTextField.getText()) &&
                StringUtils.equals(selected.getPassword(), this.passwordField.getText()) &&
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
