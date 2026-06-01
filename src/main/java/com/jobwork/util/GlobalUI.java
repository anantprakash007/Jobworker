package com.jobwork.util;

import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Region;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.control.ContentDisplay;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**

 * GlobalUI — centralised UI utility (FULL PROFESSIONAL VERSION)
 */
public class GlobalUI {

    // ═══════════════════════════════════════════════════════════
    //  BUTTON STYLES
    // ═══════════════════════════════════════════════════════════

    public static final String STYLE_EDIT =
            "-fx-background-color:linear-gradient(to right,#3b82f6,#2563eb);" +
                    "-fx-text-fill:white;-fx-font-weight:bold;-fx-font-size:11px;" +
                    "-fx-padding:4 10;-fx-background-radius:8;-fx-cursor:hand;";

    public static final String STYLE_DELETE =
            "-fx-background-color:linear-gradient(to right,#ef4444,#dc2626);" +
                    "-fx-text-fill:white;-fx-font-weight:bold;-fx-font-size:11px;" +
                    "-fx-padding:4 10;-fx-background-radius:8;-fx-cursor:hand;";

    // ═══════════════════════════════════════════════════════════
    //  ICON LOADER (SAFE + FIXED SIZE)
    // ═══════════════════════════════════════════════════════════

    private static ImageView loadIcon(String path) {
        try {
            String url = GlobalUI.class.getResource(path).toExternalForm();

            Image img = new Image(
                    url,
                    12, 12,
                    true, true,
                    true   // ✅ background loading (VERY IMPORTANT)
            );

            ImageView iv = new ImageView(img);
            iv.setFitWidth(12);
            iv.setFitHeight(12);

            return iv;
        } catch (Exception e) {
            return null;
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  BUTTON FACTORIES (ICON + TEXT)
    // ═══════════════════════════════════════════════════════════

    public static Button editButton() {
        ImageView icon = loadIcon("/icons/edit.png");


        Button btn = new Button("Edit");
        if (icon != null) btn.setGraphic(icon);

        btn.setStyle(STYLE_EDIT);
        btn.setContentDisplay(ContentDisplay.LEFT);
        btn.setGraphicTextGap(5);

        btn.setMinWidth(75);     // ✅ FIX
        btn.setPrefHeight(26);   // ✅ FIX

        return btn;


    }

    public static Button deleteButton() {
        ImageView icon = loadIcon("/icons/delete.png");


        Button btn = new Button("Delete");
        if (icon != null) btn.setGraphic(icon);

        btn.setStyle(STYLE_DELETE);
        btn.setContentDisplay(ContentDisplay.LEFT);
        btn.setGraphicTextGap(5);

        btn.setMinWidth(90);     // ✅ FIX
        btn.setPrefHeight(26);   // ✅ FIX

        return btn;


    }

    // ═══════════════════════════════════════════════════════════
    //  GENERIC TABLE ACTION COLUMN
    // ═══════════════════════════════════════════════════════════

    public static <T> TableColumn<T, Void> actionColumn(
            String header,
            double width,
            boolean isEdit,
            java.util.function.Consumer<T> action) {


        TableColumn<T, Void> col = new TableColumn<>(header);

        col.setPrefWidth(width > 0 ? width : 120);  // ✅ SAFE WIDTH
        col.setMinWidth(110);
        col.setStyle("-fx-alignment:CENTER;");
        col.setSortable(false);
        col.setResizable(true);

        col.setCellFactory(c -> new TableCell<>() {

            final Button btn = isEdit ? editButton() : deleteButton();

            {
                btn.setOnAction(e -> {
                    @SuppressWarnings("unchecked")
                    T item = (T) getTableRow().getItem();
                    if (item != null) action.accept(item);
                });
            }

            @Override
            protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(empty ? null : btn);
            }
        });

        return col;


    }

    // ═══════════════════════════════════════════════════════════
    //  DIALOG HELPERS
    // ═══════════════════════════════════════════════════════════

    public static Label boldLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-weight:bold;-fx-min-width:110px;");
        return l;
    }

    public static TextField inputField(String value, double prefWidth) {
        TextField tf = new TextField(value != null ? value : "");
        tf.setPrefWidth(prefWidth);
        return tf;
    }

    public static String safeStr(String s) {
        return s != null ? s : "";
    }

    // ═══════════════════════════════════════════════════════════
    //  DATE FORMATTING
    // ═══════════════════════════════════════════════════════════

    private static final DateTimeFormatter DF =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public static String formatDate(LocalDate date) {
        return date != null ? DF.format(date) : "";
    }

    // ═══════════════════════════════════════════════════════════
    //  ALERTS
    // ═══════════════════════════════════════════════════════════

    public static void success(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("Success");
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    public static void warn(String msg) {
        Alert a = new Alert(Alert.AlertType.WARNING);
        a.setTitle("Warning");
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    public static boolean confirm(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.setTitle("Confirm");
        a.setHeaderText(title);
        a.setContentText(msg);
        return a.showAndWait()
                .filter(b -> b == ButtonType.OK)
                .isPresent();
    }

    public static void showTextDialog(String title, String content) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title);
        a.setHeaderText(null);


        TextArea ta = new TextArea(content);
        ta.setEditable(false);
        ta.setWrapText(true);
        ta.setPrefWidth(700);
        ta.setPrefHeight(400);

        a.getDialogPane().setContent(ta);
        a.showAndWait();


    }

    // ═══════════════════════════════════════════════════════════
    //  EXPORT CHECK
    // ═══════════════════════════════════════════════════════════

    public static boolean validateExport(List<?> data) {
        if (data == null || data.isEmpty()) {
            warn("No data to export.");
            return false;
        }
        return true;
    }
    // ===========================================================
// 🔥 OPEN WINDOW (NEW STAGE LOADER)
// ===========================================================
    public static void openWindow(
            String fxmlPath,
            String title,
            java.util.function.Consumer<Object> controllerConsumer) {

        try {

            javafx.fxml.FXMLLoader loader =
                    new javafx.fxml.FXMLLoader(GlobalUI.class.getResource(fxmlPath));

            // 🔥 THIS IS THE MAIN FIX
            loader.setControllerFactory(
                    com.jobwork.config.SpringContext.getApplicationContext()::getBean
            );

            javafx.scene.Parent root = loader.load();

            Object controller = loader.getController();

            if (controllerConsumer != null) {
                controllerConsumer.accept(controller);
            }

            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.setTitle(title);
            stage.setScene(new javafx.scene.Scene(root));
            stage.show();

        } catch (Exception e) {
            warn("Failed to open window: " + e.getMessage());
        }
    }
}
