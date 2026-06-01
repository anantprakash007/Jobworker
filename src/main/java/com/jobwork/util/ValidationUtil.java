package com.jobwork.util;

import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

public class ValidationUtil {

    private static final List<String> errors = new ArrayList<>();
    private static Control firstErrorField = null;

    // ================= START VALIDATION =================
    public static void start() {
        errors.clear();
        firstErrorField = null;
    }

    // ================= REQUIRED =================
    public static void required(Control field, String message) {

        boolean invalid = false;

        if (field instanceof TextField tf) {
            invalid = tf.getText() == null || tf.getText().trim().isEmpty();
        }
        else if (field instanceof ComboBox<?> cb) {
            invalid = cb.getValue() == null;
        }
        else if (field instanceof DatePicker dp) {
            invalid = dp.getValue() == null;
        }

        if (invalid) {
            addError(field, message);
        } else {
            FormUtil.clearError(field);
        }
    }

    // ================= NUMBER =================
    public static void number(TextField tf, String message) {

        if (tf.getText() == null || tf.getText().trim().isEmpty()) {
            addError(tf, message);
            return;
        }

        try {
            Double.parseDouble(tf.getText().trim());
            FormUtil.clearError(tf);
        } catch (Exception e) {
            addError(tf, message);
        }
    }

    // ================= CUSTOM =================
    public static void custom(boolean condition, Control field, String message) {
        if (!condition) {
            addError(field, message);
        } else {
            FormUtil.clearError(field);
        }
    }

    // ================= ADD ERROR =================
    private static void addError(Control field, String message) {

        FormUtil.setError(field, message);
        errors.add("• " + message);

        if (firstErrorField == null) {
            firstErrorField = field;
        }
    }

    // ================= FINAL CHECK =================
    public static boolean validate() {

        if (errors.isEmpty()) return true;

        showErrorPopup();
        focusFirstError();
        return false;
    }

    // ================= POPUP =================
    private static void showErrorPopup() {

        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Validation Error");
        alert.setHeaderText("Please fix the following:");

        VBox box = new VBox(5);

        for (String err : errors) {
            box.getChildren().add(new Label(err));
        }

        alert.getDialogPane().setContent(box);
        alert.showAndWait();
    }

    // ================= FOCUS =================
    private static void focusFirstError() {
        if (firstErrorField != null) {
            firstErrorField.requestFocus();
        }
    }
}