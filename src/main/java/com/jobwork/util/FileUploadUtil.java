package com.jobwork.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;

@Component
public class FileUploadUtil {

    // Reads from application.properties: app.upload.dir=./uploads/receipts
    @Value("${app.upload.dir:./uploads/receipts}")
    private String uploadDir;

    /**
     * Copy a source file (chosen by the user via FileChooser)
     * into a challan-specific sub-folder under uploadDir.
     *
     * Final path structure:
     *   ./uploads/receipts/{challanNo}/{challanNo}_{date}_{millis}.{ext}
     *
     * @param sourceFile  the file selected by the user
     * @param challanNo   used as the sub-folder name
     * @return absolute path string stored in entity.receiptPath
     */
    public String saveReceipt(File sourceFile, String challanNo)
            throws IOException {

        if (sourceFile == null || !sourceFile.exists())
            throw new IOException("Source file does not exist.");

        if (challanNo == null || challanNo.isBlank())
            throw new IOException("Challan number must not be blank.");

        // Sanitise challanNo for use as a folder name
        String safeChallan = challanNo.trim()
                .replaceAll("[^a-zA-Z0-9_\\-]", "_");

        Path dir = Paths.get(uploadDir).resolve(safeChallan);
        Files.createDirectories(dir);

        String originalName = sourceFile.getName();
        String ext = originalName.contains(".")
                ? originalName.substring(
                originalName.lastIndexOf('.'))
                : "";

        String fileName = safeChallan
                + "_" + LocalDate.now()
                + "_" + System.currentTimeMillis()
                + ext;

        Path destination = dir.resolve(fileName);
        Files.copy(sourceFile.toPath(), destination,
                StandardCopyOption.REPLACE_EXISTING);

        return destination.toAbsolutePath().toString();
    }

    /**
     * Delete a previously saved receipt file.
     * Called when a record is deleted from the report screen.
     *
     * @param receiptPath the absolute path stored in the entity
     */
    public void deleteReceipt(String receiptPath) {
        if (receiptPath == null || receiptPath.isBlank()) return;
        try {
            Files.deleteIfExists(Paths.get(receiptPath));
        } catch (IOException e) {
            // Log but do not rethrow — receipt cleanup is best-effort
            System.err.println(
                    "Warning: could not delete receipt file: "
                            + receiptPath + " — " + e.getMessage());
        }
    }

    /**
     * Returns true if the file at receiptPath actually exists on disk.
     * Used by controllers to show/hide the "View Receipt" button.
     */
    public boolean receiptExists(String receiptPath) {
        if (receiptPath == null || receiptPath.isBlank())
            return false;
        return Files.exists(Paths.get(receiptPath));
    }

    /**
     * Open a saved receipt in the OS default viewer
     * (PDF reader, image viewer, etc.)
     */
    public void openReceipt(String receiptPath) throws IOException {
        if (!receiptExists(receiptPath))
            throw new IOException("Receipt file not found.");
        java.awt.Desktop.getDesktop()
                .open(new File(receiptPath));
    }
}