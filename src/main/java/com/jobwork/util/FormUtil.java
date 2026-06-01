package com.jobwork.util;

import javafx.scene.control.*;
import javafx.scene.input.KeyCode;

import java.math.BigDecimal;

import static java.lang.String.format;

public class FormUtil {

    // ================= NUMERIC INPUT =================
    public static void allowNumeric(TextField tf, boolean decimal) {
        tf.textProperty().addListener((obs, old, val) -> {
            if (val == null) return;

            String regex = decimal ? "\\d*(\\.\\d*)?" : "\\d*";

            if (!val.matches(regex)) {
                tf.setText(old);
            }
        });
    }

    // ================= KEYBOARD FLOW =================
    public static void moveNext(Control current, Control next) {
        current.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.TAB) {
                next.requestFocus();
            }
        });
    }

    // ================= COMBOBOX FLOW =================
    public static void moveNextCombo(ComboBox<?> current, Control next) {
        current.setOnAction(e -> next.requestFocus());
    }

    // ================= VALIDATION =================
    public static boolean validateRequired(Control field) {

        if (field instanceof TextField tf) {
            if (tf.getText() == null || tf.getText().trim().isEmpty()) {
                setError(field, "Required field");
                return false;
            }

        } else if (field instanceof ComboBox<?> cb) {
            if (cb.getValue() == null) {
                setError(field, "Please select value");
                return false;
            }

        } else if (field instanceof DatePicker dp) {
            if (dp.getValue() == null) {
                setError(field, "Please select date");
                return false;
            }
        }

        clearError(field);
        return true;
    }

    // ================= NUMBER VALIDATION =================
    public static boolean validateNumber(TextField tf) {

        if (tf.getText() == null || tf.getText().trim().isEmpty()) {
            setError(tf, "Required number");
            return false;
        }

        try {
            Double.parseDouble(tf.getText().trim());
            clearError(tf);
            return true;
        } catch (Exception e) {
            setError(tf, "Invalid number");
            return false;
        }
    }

    // ================= ERROR UI =================
    public static void setError(Control field, String message) {

        field.setStyle("-fx-border-color:red; -fx-border-width:2;");

        Tooltip tooltip = new Tooltip(message);
        field.setTooltip(tooltip);
    }

    public static void clearError(Control field) {
        field.setStyle("");
        field.setTooltip(null);
    }

    // ================= AUTO SELECT TEXT =================
    public static void autoSelect(TextField tf) {
        tf.focusedProperty().addListener((obs, old, now) -> {
            if (now) {
                tf.selectAll();
            }
        });
    }

    // ================= SAFE GET TEXT =================
    public static String getText(TextField tf) {
        return tf.getText() == null ? "" : tf.getText().trim();
    }
    public static void allowDecimal(TextField tf, int decimalPlaces) {

        tf.textProperty().addListener((obs, old, val) -> {

            if (val == null) return;

            String regex;

            if (decimalPlaces == 0) {
                regex = "\\d*";
            } else {
                regex = "\\d*(\\.\\d{0," + decimalPlaces + "})?";
            }

            if (!val.matches(regex)) {
                tf.setText(old);
            }
        });


    }
    // ================= SAFE DECIMAL PARSE =================
    public static BigDecimal parseDecimal(String value) {

        try {
            if (value == null || value.trim().isEmpty()) {
                return BigDecimal.ZERO;
            }

            return new BigDecimal(value.trim());

        } catch (Exception e) {
            return BigDecimal.ZERO; // fallback safe
        }
    }


}