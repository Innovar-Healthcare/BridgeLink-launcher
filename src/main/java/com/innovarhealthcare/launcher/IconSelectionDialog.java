package com.innovarhealthcare.launcher;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Dialog for selecting and managing application icons
 * 
 * @author thait
 */
public class IconSelectionDialog {
    private final Stage parentStage;
    private final File dataFolder;
    private final Image defaultIcon;
    private final Consumer<String> onIconSelected;
    
    /**
     * Creates a new icon selection dialog
     * 
     * @param parentStage The parent stage for modal behavior
     * @param dataFolder The data folder where icons are stored
     * @param defaultIcon The default application icon
     * @param onIconSelected Callback function when an icon is selected
     */
    public IconSelectionDialog(Stage parentStage, File dataFolder, Image defaultIcon, Consumer<String> onIconSelected) {
        this.parentStage = parentStage;
        this.dataFolder = dataFolder;
        this.defaultIcon = defaultIcon;
        this.onIconSelected = onIconSelected;
    }
    
    /**
     * Shows the icon selection dialog
     */
    public void show() {
        Stage iconStage = new Stage();
        iconStage.setTitle("Select Icon");
        iconStage.initOwner(parentStage);
        iconStage.initModality(Modality.APPLICATION_MODAL);
        iconStage.getIcons().add(defaultIcon);

        VBox root = new VBox(15);
        root.setPadding(new Insets(15));

        Label titleLabel = new Label("Choose an icon:");
        titleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        // Create grid for icons
        GridPane iconGrid = new GridPane();
        iconGrid.setHgap(10);
        iconGrid.setVgap(10);
        iconGrid.setPadding(new Insets(10));

        // Load available icons
        File iconsFolder = new File(dataFolder, "icons");
        List<File> iconFiles = new ArrayList<>();
        
        // Load all PNG files from data/icons folder (includes both resource icons and user uploads)
        if (iconsFolder.exists()) {
            File[] files = iconsFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".png"));
            if (files != null) {
                for (File file : files) {
                    iconFiles.add(file);
                }
            }
        }

        // Create icon selection buttons
        int col = 0;
        int row = 0;
        int maxCols = 6;

        // Add BridgeLink.png first from /images/ resource
        Button bridgeLinkButton = new Button();
        bridgeLinkButton.setPrefSize(48, 48);
        bridgeLinkButton.setMinSize(48, 48);
        bridgeLinkButton.setMaxSize(48, 48);
        bridgeLinkButton.setStyle("-fx-background-color: transparent; -fx-border-color: #cccccc; -fx-border-radius: 4;");

        try {
            Image bridgeLinkImage = new Image(getClass().getResourceAsStream("/images/BridgeLink.png"));
            ImageView bridgeLinkView = new ImageView(bridgeLinkImage);
            bridgeLinkView.setFitWidth(36);
            bridgeLinkView.setFitHeight(36);
            bridgeLinkView.setPreserveRatio(true);
            bridgeLinkButton.setGraphic(bridgeLinkView);
            bridgeLinkButton.setUserData("BridgeLink.png");
            bridgeLinkButton.setOnAction(e -> {
                onIconSelected.accept("BridgeLink.png");
                iconStage.close();
            });
            iconGrid.add(bridgeLinkButton, col, row);
            
            col++;
            if (col >= maxCols) {
                col = 0;
                row++;
            }
        } catch (Exception e) {
            // Skip if BridgeLink.png resource is not available
        }

        // Add all icon files from data/icons folder
        for (File iconFile : iconFiles) {
            Button iconChoiceButton = new Button();
            iconChoiceButton.setPrefSize(48, 48);
            iconChoiceButton.setMinSize(48, 48);
            iconChoiceButton.setMaxSize(48, 48);
            iconChoiceButton.setStyle("-fx-background-color: transparent; -fx-border-color: #cccccc; -fx-border-radius: 4;");

            ImageView iconView;

            // Load custom icon
            try {
                Image iconImage = new Image(iconFile.toURI().toString());
                iconView = new ImageView(iconImage);
                iconChoiceButton.setUserData(iconFile.getName());
            } catch (Exception e) {
                continue; // Skip invalid images
            }


            iconView.setFitWidth(36);
            iconView.setFitHeight(36);
            iconView.setPreserveRatio(true);
            iconChoiceButton.setGraphic(iconView);

            iconChoiceButton.setOnAction(e -> {
                String selectedIcon = (String) iconChoiceButton.getUserData();
                onIconSelected.accept(selectedIcon);
                iconStage.close();
            });

            iconGrid.add(iconChoiceButton, col, row);

            col++;
            if (col >= maxCols) {
                col = 0;
                row++;
            }
        }

        // Upload new icon button
        Button uploadButton = new Button("Upload New Icon");
        uploadButton.setOnAction(e -> {
            if (uploadNewIcon()) {
                // Icon was successfully uploaded and already applied to connection
                // Just close the dialog
                iconStage.close();
            }
        });

        Button cancelButton = new Button("Cancel");
        cancelButton.setOnAction(e -> iconStage.close());

        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.getChildren().addAll(uploadButton, cancelButton);

        ScrollPane scrollPane = new ScrollPane(iconGrid);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(200);

        root.getChildren().addAll(titleLabel, scrollPane, buttonBox);

        Scene scene = new Scene(root, 450, 300);
        iconStage.setScene(scene);
        iconStage.showAndWait();
    }
    
    /**
     * Handles uploading a new icon file
     * 
     * @return true if upload was successful, false otherwise
     */
    private boolean uploadNewIcon() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Choose Icon");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PNG Images", "*.png")
        );
        File file = fileChooser.showOpenDialog(parentStage);

        if (file != null) {
            try {
                // Check file size (must be less than 1MB)
                long fileSizeInBytes = file.length();
                long maxSizeInBytes = 1024 * 1024; // 1MB
                
                if (fileSizeInBytes > maxSizeInBytes) {
                    showAlert("File size too large. Please select an image smaller than 1MB.\nSelected file size: " + 
                             String.format("%.2f", fileSizeInBytes / 1024.0 / 1024.0) + " MB");
                    return false;
                }

                // Create icons directory if it doesn't exist
                File iconsFolder = new File(dataFolder, "icons");
                if (!iconsFolder.exists()) {
                    iconsFolder.mkdirs();
                }

                // Generate unique filename
                String fileName = System.currentTimeMillis() + "_icon.png";
                File targetFile = new File(iconsFolder, fileName);

                // Load and resize the image to 192x192
                BufferedImage originalImage = ImageIO.read(file);
                BufferedImage resizedImage = resizeImage(originalImage, 192, 192);

                // Save the resized image
                ImageIO.write(resizedImage, "PNG", targetFile);

                // Apply icon to connection but don't save automatically
                onIconSelected.accept(fileName);
                return true;

            } catch (IOException e) {
                showAlert("Failed to process icon: " + e.getMessage());
            } catch (Exception e) {
                showAlert("Error processing image: " + e.getMessage());
            }
        }
        return false;
    }
    
    /**
     * Resizes an image to the specified dimensions with high quality
     * 
     * @param originalImage The original image to resize
     * @param targetWidth Target width in pixels
     * @param targetHeight Target height in pixels
     * @return Resized image
     */
    private BufferedImage resizeImage(BufferedImage originalImage, int targetWidth, int targetHeight) {
        BufferedImage resizedImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = resizedImage.createGraphics();
        
        // Set high-quality rendering hints
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        // Draw the scaled image
        g2d.drawImage(originalImage, 0, 0, targetWidth, targetHeight, null);
        g2d.dispose();
        
        return resizedImage;
    }
    
    /**
     * Shows an error alert dialog
     * 
     * @param message The error message to display
     */
    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(message);
        alert.initOwner(parentStage);
        alert.showAndWait();
    }
}