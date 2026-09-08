package com.jobwork.util;

import com.jobwork.domain.JobWorker;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Central Job Worker ComboBox utility.
 *
 * Features:
 *  1. Uses the central JobWorker master list.
 *  2. Displays only JobWorker name.
 *  3. Searchable by typing.
 *  4. Case-insensitive search.
 *  5. Partial-name search.
 *  6. Selected value ALWAYS remains JobWorker.
 *  7. Never stores a String inside ComboBox<JobWorker>.
 *  8. Safe with Enter, Tab and focus changes.
 *  9. Existing JobWorker ID/entity relationship is preserved.
 */
public final class JobWorkerComboBoxUtil {

    private JobWorkerComboBoxUtil() {
        // Utility class.
    }

    /**
     * Configure a JobWorker ComboBox.
     *
     * @param comboBox JobWorker ComboBox
     * @param workers  JobWorker master list
     */
    public static void setup(
            ComboBox<JobWorker> comboBox,
            List<JobWorker> workers) {

        if (comboBox == null) {
            return;
        }

        /*
         * Keep our own master list.
         *
         * IMPORTANT:
         * The filtered list is used only for displaying/searching.
         * The actual selected value is always a JobWorker from
         * this master list.
         */
        List<JobWorker> sourceList =
                workers == null
                        ? List.of()
                        : new ArrayList<>(workers);

        ObservableList<JobWorker> masterList =
                FXCollections.observableArrayList(sourceList);

        FilteredList<JobWorker> filteredList =
                new FilteredList<>(masterList, worker -> true);

        comboBox.setItems(filteredList);

        // ============================================================
        // LIST CELL DISPLAY
        // ============================================================

        comboBox.setCellFactory(cb -> new ListCell<>() {

            @Override
            protected void updateItem(
                    JobWorker worker,
                    boolean empty) {

                super.updateItem(worker, empty);

                if (empty || worker == null) {
                    setText(null);
                } else {
                    setText(safeName(worker));
                }
            }
        });

        // ============================================================
        // SELECTED VALUE DISPLAY
        // ============================================================

        comboBox.setButtonCell(new ListCell<>() {

            @Override
            protected void updateItem(
                    JobWorker worker,
                    boolean empty) {

                super.updateItem(worker, empty);

                if (empty || worker == null) {
                    setText(null);
                } else {
                    setText(safeName(worker));
                }
            }
        });

        // ============================================================
        // EDITABLE / SEARCHABLE
        // ============================================================

        comboBox.setEditable(true);

        TextField editor = comboBox.getEditor();

        if (editor == null) {
            return;
        }

        editor.setPromptText("Search Job Worker...");

        /*
         * Guard prevents our own editor updates from being interpreted
         * as user search text.
         */
        final boolean[] internalChange = {false};

        /*
         * Guard prevents multiple commit operations when JavaFX
         * processes Enter/focus changes.
         */
        final boolean[] committing = {false};

        // ============================================================
        // CRITICAL FIX
        // STRING <-> JOBWORKER CONVERTER
        // ============================================================
        //
        // Editable ComboBox normally has to convert the editor String
        // back into the ComboBox value type.
        //
        // Without this converter JavaFX can attempt:
        //
        //     String -> JobWorker
        //
        // by casting, which caused:
        //
        //     ClassCastException:
        //     String cannot be cast to JobWorker
        //
        // This converter guarantees that the value is always a
        // JobWorker object or null.
        // ============================================================

        comboBox.setConverter(new StringConverter<>() {

            @Override
            public String toString(JobWorker worker) {

                if (worker == null) {
                    return "";
                }

                return safeName(worker);
            }

            @Override
            public JobWorker fromString(String text) {

                String typed = normalize(text);

                if (typed.isEmpty()) {

                    /*
                     * Empty editor means no worker.
                     */
                    return null;
                }

                /*
                 * Exact worker-name match.
                 */
                JobWorker exact =
                        findExact(masterList, typed);

                if (exact != null) {
                    return exact;
                }

                /*
                 * IMPORTANT:
                 *
                 * If the user is only typing a search fragment,
                 * do NOT return a String.
                 *
                 * Keep the currently selected JobWorker until our
                 * explicit commit logic decides what to do.
                 */
                JobWorker current = comboBox.getValue();

                return current;
            }
        });

        // ============================================================
        // VALUE CHANGED
        // ============================================================

        comboBox.valueProperty().addListener(
                (obs, oldValue, newValue) -> {

                    if (internalChange[0]) {
                        return;
                    }

                    if (newValue != null) {

                        internalChange[0] = true;

                        try {

                            editor.setText(
                                    safeName(newValue)
                            );

                            editor.positionCaret(
                                    editor.getText().length()
                            );

                        } finally {
                            internalChange[0] = false;
                        }
                    }
                }
        );

        // ============================================================
        // SEARCH WHILE TYPING
        // ============================================================

        editor.textProperty().addListener(
                (obs, oldText, newText) -> {

                    if (internalChange[0]) {
                        return;
                    }

                    String search =
                            normalize(newText)
                                    .toLowerCase(Locale.ROOT);

                    filteredList.setPredicate(worker -> {

                        if (worker == null) {
                            return false;
                        }

                        if (search.isEmpty()) {
                            return true;
                        }

                        String workerName =
                                safeName(worker)
                                        .toLowerCase(Locale.ROOT);

                        return workerName.contains(search);
                    });

                    /*
                     * If user starts typing a different name,
                     * remove the old selected value.
                     *
                     * But do this only when the text is actually
                     * different from the selected worker's name.
                     */
                    JobWorker selected = comboBox.getValue();

                    if (selected != null) {

                        String selectedName =
                                safeName(selected);

                        if (!selectedName.equalsIgnoreCase(
                                newText == null ? "" : newText.trim())) {

                            /*
                             * Do not call setValue(String).
                             *
                             * clearSelection() only changes the
                             * JobWorker selection.
                             */
                            comboBox.getSelectionModel()
                                    .clearSelection();
                        }
                    }

                    /*
                     * Open the dropdown while searching.
                     */
                    if (!search.isEmpty()) {

                        Platform.runLater(() -> {

                            if (!comboBox.isShowing()) {
                                comboBox.show();
                            }
                        });
                    }
                }
        );

        // ============================================================
        // ENTER
        // ============================================================

        editor.setOnAction(event -> {

            if (committing[0]) {
                event.consume();
                return;
            }

            committing[0] = true;

            try {

                commitTypedWorker(
                        comboBox,
                        masterList
                );

            } finally {
                committing[0] = false;
            }

            /*
             * Prevent the editor's default action from performing
             * another commit after our controlled commit.
             */
            event.consume();
        });

        // ============================================================
        // FOCUS LOST
        // ============================================================

        editor.focusedProperty().addListener(
                (obs, wasFocused, isFocused) -> {

                    if (isFocused) {
                        return;
                    }

                    if (committing[0]) {
                        return;
                    }

                    committing[0] = true;

                    try {

                        commitOnFocusLost(
                                comboBox,
                                masterList
                        );

                    } finally {
                        committing[0] = false;
                    }
                }
        );

        // ============================================================
        // ESCAPE
        // ============================================================

        editor.setOnKeyPressed(event -> {

            switch (event.getCode()) {

                case ESCAPE -> {

                    JobWorker selected =
                            comboBox.getValue();

                    internalChange[0] = true;

                    try {

                        if (selected != null) {

                            editor.setText(
                                    safeName(selected)
                            );

                            editor.positionCaret(
                                    editor.getText().length()
                            );

                        } else {
                            editor.clear();
                        }

                    } finally {
                        internalChange[0] = false;
                    }

                    filteredList.setPredicate(
                            worker -> true
                    );

                    comboBox.hide();
                }

                default -> {
                    // No special handling.
                }
            }
        });

        // ============================================================
        // INITIAL VALUE
        // ============================================================

        JobWorker selected = comboBox.getValue();

        if (selected != null) {

            internalChange[0] = true;

            try {

                editor.setText(
                        safeName(selected)
                );

                editor.positionCaret(
                        editor.getText().length()
                );

            } finally {
                internalChange[0] = false;
            }
        }
    }

    /**
     * Refresh the JobWorker ComboBox.
     */
    public static void refresh(
            ComboBox<JobWorker> comboBox,
            List<JobWorker> workers) {

        setup(comboBox, workers);
    }

    // ================================================================
    // CONTROLLED COMMIT
    // ================================================================

    /**
     * Commit typed worker.
     *
     * Only an exact worker-name match is accepted.
     *
     * Most importantly:
     *
     *     comboBox.setValue(...)
     *
     * is called ONLY with a JobWorker.
     *
     * A String is NEVER assigned to ComboBox<JobWorker>.
     */
    private static void commitTypedWorker(
            ComboBox<JobWorker> comboBox,
            ObservableList<JobWorker> masterList) {

        if (comboBox == null) {
            return;
        }

        TextField editor = comboBox.getEditor();

        if (editor == null) {
            return;
        }

        String typed =
                normalize(editor.getText());

        // ------------------------------------------------------------
        // EMPTY
        // ------------------------------------------------------------

        if (typed.isEmpty()) {

            comboBox.getSelectionModel()
                    .clearSelection();

            editor.clear();

            comboBox.hide();

            return;
        }

        // ------------------------------------------------------------
        // EXACT MATCH
        // ------------------------------------------------------------

        JobWorker matched =
                findExact(masterList, typed);

        if (matched != null) {

            /*
             * SAFE:
             *
             * matched is JobWorker.
             */
            comboBox.getSelectionModel()
                    .select(matched);

            /*
             * Do NOT call comboBox.setValue(editor.getText()).
             */
            editor.setText(
                    safeName(matched)
            );

            editor.positionCaret(
                    editor.getText().length()
            );

            comboBox.hide();

            return;
        }

        // ------------------------------------------------------------
        // INVALID WORKER
        // ------------------------------------------------------------

        /*
         * Never allow arbitrary typed text to become the selected
         * JobWorker.
         */
        JobWorker current =
                comboBox.getValue();

        if (current != null) {

            editor.setText(
                    safeName(current)
            );

            editor.positionCaret(
                    editor.getText().length()
            );

        } else {

            comboBox.getSelectionModel()
                    .clearSelection();

            editor.clear();
        }

        comboBox.hide();

        /*
         * Warn only for explicit Enter.
         */
        GlobalUI.warn(
                "Select a Job Worker from the master list."
        );
    }

    // ================================================================
    // FOCUS LOST COMMIT
    // ================================================================

    /**
     * Safe commit when focus moves to another control.
     *
     * No warning is shown here because Tab/focus movement should not
     * produce repeated warning dialogs.
     */
    private static void commitOnFocusLost(
            ComboBox<JobWorker> comboBox,
            ObservableList<JobWorker> masterList) {

        if (comboBox == null) {
            return;
        }

        TextField editor = comboBox.getEditor();

        if (editor == null) {
            return;
        }

        String typed =
                normalize(editor.getText());

        // ------------------------------------------------------------
        // EMPTY
        // ------------------------------------------------------------

        if (typed.isEmpty()) {

            comboBox.getSelectionModel()
                    .clearSelection();

            editor.clear();

            return;
        }

        // ------------------------------------------------------------
        // EXACT MATCH
        // ------------------------------------------------------------

        JobWorker matched =
                findExact(masterList, typed);

        if (matched != null) {

            comboBox.getSelectionModel()
                    .select(matched);

            editor.setText(
                    safeName(matched)
            );

            editor.positionCaret(
                    editor.getText().length()
            );

            return;
        }

        // ------------------------------------------------------------
        // INVALID
        // ------------------------------------------------------------

        /*
         * Restore existing valid worker.
         *
         * If no worker was selected, clear the invalid text.
         */
        JobWorker current =
                comboBox.getValue();

        if (current != null) {

            editor.setText(
                    safeName(current)
            );

            editor.positionCaret(
                    editor.getText().length()
            );

        } else {

            comboBox.getSelectionModel()
                    .clearSelection();

            editor.clear();
        }
    }

    // ================================================================
    // FIND EXACT WORKER
    // ================================================================

    private static JobWorker findExact(
            ObservableList<JobWorker> masterList,
            String text) {

        if (masterList == null || text == null) {
            return null;
        }

        String search =
                text.trim();

        if (search.isEmpty()) {
            return null;
        }

        for (JobWorker worker : masterList) {

            if (worker == null) {
                continue;
            }

            String name =
                    safeName(worker);

            if (name.equalsIgnoreCase(search)) {
                return worker;
            }
        }

        return null;
    }

    // ================================================================
    // NORMALIZE
    // ================================================================

    private static String normalize(String text) {

        if (text == null) {
            return "";
        }

        return text.trim();
    }

    // ================================================================
    // SAFE NAME
    // ================================================================

    private static String safeName(JobWorker worker) {

        if (worker == null) {
            return "";
        }

        if (worker.getName() == null) {
            return "";
        }

        return worker.getName().trim() + " - " + worker.getPhone() ;
    }
}