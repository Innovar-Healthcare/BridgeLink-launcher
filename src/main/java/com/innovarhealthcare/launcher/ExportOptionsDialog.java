package com.innovarhealthcare.launcher;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * Dialog for selecting export options when exporting connections.
 *
 * @author thait
 */
public class ExportOptionsDialog {

    private final Stage parentStage;
    private final Image icon;
    private boolean exportWithCredential = false;
    private boolean confirmed = false;

    public ExportOptionsDialog(Stage parentStage, Image icon) {
        this.parentStage = parentStage;
        this.icon = icon;
    }

    /**
     * Shows the dialog and blocks until it is closed.
     *
     * @return true if the user clicked OK, false if cancelled
     */
    public boolean showAndWait() {
        Stage dialogStage = new Stage();
        dialogStage.setTitle("Export Connections");
        dialogStage.initOwner(parentStage);
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.setResizable(false);
        if (icon != null) {
            dialogStage.getIcons().add(icon);
        }

        VBox root = new VBox(12);
        root.setPadding(new Insets(15));

        Label promptLabel = new Label("Export options:");

        CheckBox credentialCheckBox = new CheckBox("Include user credentials");
        credentialCheckBox.setSelected(false); // default: strip credentials

        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        Button okButton = new Button("OK");
        okButton.setDefaultButton(true);
        okButton.setOnAction(e -> {
            exportWithCredential = credentialCheckBox.isSelected();
            confirmed = true;
            dialogStage.close();
        });

        Button cancelButton = new Button("Cancel");
        cancelButton.setCancelButton(true);
        cancelButton.setOnAction(e -> dialogStage.close());

        buttonBox.getChildren().addAll(okButton, cancelButton);
        root.getChildren().addAll(promptLabel, credentialCheckBox, buttonBox);

        Scene scene = new Scene(root, 360, 120);
        dialogStage.setScene(scene);
        dialogStage.showAndWait();

        return confirmed;
    }

    public boolean isExportWithCredential() {
        return exportWithCredential;
    }

    public void setExportWithCredential(boolean exportWithCredential) {
        this.exportWithCredential = exportWithCredential;
    }
}
