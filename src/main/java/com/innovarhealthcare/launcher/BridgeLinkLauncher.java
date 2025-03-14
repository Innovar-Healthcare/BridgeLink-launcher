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
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

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

//    private static final Logger logger = LogManager.getLogger(MirthClientLauncher.class);
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

//        // Connections TableView
//        this.connectionsTableView = new TableView<>();
//        this.connectionsTableView.setPlaceholder(new Label("No saved connections"));
//
//        TableColumn<Connection, String> nameColumn = new TableColumn<>("Connections");
//        nameColumn.setCellValueFactory(param -> new javafx.beans.property.SimpleStringProperty(param.getValue().getName()));
//        this.connectionsTableView.getColumns().add(nameColumn);
//
//        splitPane.getItems().add(this.connectionsTableView);

        // Connections TableView
        this.connectionsTableView = new TableView<>();
        this.connectionsTableView.setPlaceholder(new Label("No saved connections"));
        this.connectionsTableView.setEditable(true);

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

        iconColumn.setCellValueFactory((Callback) new PropertyValueFactory<>("icon"));
        iconColumn.setMinWidth(26.0);
        iconColumn.setMaxWidth(26.0);
        this.connectionsTableView.getColumns().add(iconColumn);

        TableColumn<Connection, String> nameColumn = new TableColumn<>("Connections");
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        nameColumn.setOnEditCommit(event -> {
            String newName = StringUtils.trim(event.getNewValue());
            boolean exists = false;
            for (Connection conn : connectionsList) {
                if (!StringUtils.equals(conn.getId(), event.getRowValue().getId()) &&
                        StringUtils.equalsIgnoreCase(conn.getName(), newName)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) event.getRowValue().setName(newName);
            this.connectionsTableView.refresh();
            saveConnections();
        });
        this.connectionsTableView.getColumns().add(nameColumn);

        // Add other columns (hidden)
        TableColumn<Connection, String> idColumn = new TableColumn<>("Id");
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        idColumn.setVisible(false);
        this.connectionsTableView.getColumns().add(idColumn);

        this.connectionsList.addAll(loadConnections());
        this.connectionsTableView.setItems(this.connectionsList);
        this.connectionsTableView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                this.addressTextField.setText(newVal.getAddress());
                updateUIFromConnection(newVal);
            }
            this.saveButton.setDisable(true);
            this.deleteButton.setDisable(newVal == null);
        });

        this.tableSelectionModel = this.connectionsTableView.getSelectionModel();
        this.tableSelectionModel.setSelectionMode(SelectionMode.SINGLE);
        this.connectionsTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
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
        this.addressTextField.textProperty().addListener((obs, oldVal, newVal) -> updateSaveButtonState());
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
        this.bundledJavaCombo.setOnAction(event -> updateSaveButtonState());
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
        this.heapSizeCombo.setOnAction(event -> updateSaveButtonState());
        gridPane.add(this.heapSizeCombo, 1, row++, 3, 1);

        // Show Java Console
        this.consoleLabel = new Label("Show Java Console:");
        gridPane.add(this.consoleLabel, 0, row);

        ToggleGroup consoleGroup = new ToggleGroup();
        this.consoleYesRadio = new RadioButton("Yes");
        this.consoleYesRadio.setToggleGroup(consoleGroup);
        this.consoleYesRadio.setOnAction(event -> updateSaveButtonState());

        this.consoleNoRadio = new RadioButton("No");
        this.consoleNoRadio.setToggleGroup(consoleGroup);
        this.consoleNoRadio.setSelected(true);
        this.consoleNoRadio.setOnAction(event -> updateSaveButtonState());

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
        this.newButton.setOnAction(event -> {
            if (!isLaunching) {
                String newName = "New Connection";
                int counter = 1;
                while (nameExists(newName)) {
                    newName = "New Connection " + counter++;
                }

                TextInputDialog dialog = new TextInputDialog(newName);
                dialog.setTitle("New Connection");
                dialog.setHeaderText("Enter a name for this connection:");
                dialog.initOwner(stage);
                dialog.showAndWait().ifPresent(name -> {
                    if (StringUtils.isNotBlank(name)) {
                        String finalName = name;
                        int cnt = 1;
                        while (nameExists(finalName)) {
                            finalName = name + " Copy " + cnt++;
                        }
                        addConnection(finalName, "", "BUNDLED", "Java 17", "", "512m", "", false, "", false, "", false);
                    }
                });
            }
        });
        this.saveButton = new Button("Save");
        this.saveButton.setOnAction(event -> {
            if (!isLaunching) {
                Connection selected = this.connectionsTableView.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    updateConnectionFromUI(selected);
                    this.connectionsTableView.refresh();
                    this.saveButton.setDisable(true);
                    saveConnections();
                }
            }
        });
        this.saveAsButton = new Button("Save As...");
        this.deleteButton = new Button("Delete");
        this.deleteButton.setDisable(true);
        this.deleteButton.setOnAction(event -> {
            if (!isLaunching) {
                Connection selected = this.connectionsTableView.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    int index = this.connectionsTableView.getSelectionModel().getSelectedIndex();
                    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                    alert.setTitle("Delete Connection");
                    alert.setHeaderText("Are you sure to delete this connection?");
                    alert.initOwner(primaryStage);
                    alert.showAndWait().ifPresent(response -> {
                        if (response == ButtonType.OK) {
                            this.connectionsList.remove(selected);
                            this.connectionsTableView.refresh();
                            saveConnections();
                            if (index < this.connectionsList.size()) {
                                this.tableSelectionModel.select(index);
                            } else if (!this.connectionsList.isEmpty()) {
                                this.tableSelectionModel.select(this.connectionsList.size() - 1);
                            }
                        }
                    });
                }
            }
        });
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

        if (conn.isShowJavaConsole()) {
            consoleYesRadio.setSelected(true);
        } else {
            consoleNoRadio.setSelected(true);
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
        conn.setAddress(this.addressTextField.getText());
        conn.setJavaHome(getJavaHome());
        conn.setJavaHomeBundledValue(this.bundledJavaCombo.getValue().toString());
        conn.setJavaFxHome("");
        conn.setHeapSize(this.heapSizeCombo.getValue().toString());
        conn.setIcon("");
        conn.setShowJavaConsole(this.consoleYesRadio.isSelected());
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
                        selected.isShowJavaConsole() == this.consoleYesRadio.isSelected() ;

        this.saveButton.setDisable(unchanged);
    }

    public void showAlert(String err){
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(err);
        alert.initOwner(primaryStage);
        alert.showAndWait();
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
