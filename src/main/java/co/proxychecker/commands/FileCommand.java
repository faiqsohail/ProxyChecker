package co.proxychecker.commands;

import java.io.File;
import java.util.List;

import javafx.stage.FileChooser;

/**
 * File dialogs for choosing a file to save or files to open.
 */
public final class FileCommand {

    private FileCommand() {
    }

    /**
     * Shows a save dialog.
     *
     * @param table whether a table (CSV) is being saved, otherwise a plain text proxy list
     * @return the selected file, or null if the dialog was cancelled
     */
    public static File getFileToSave(boolean table) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save File");
        fileChooser.getExtensionFilters().add(table
                ? new FileChooser.ExtensionFilter("CSV File", "*.csv")
                : new FileChooser.ExtensionFilter("TXT Files", "*.txt"));
        return fileChooser.showSaveDialog(null);
    }

    /**
     * Shows an open dialog allowing multiple files to be selected.
     *
     * @return the selected files, or null if the dialog was cancelled
     */
    public static List<File> getFilesToOpen() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open File(s)");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("TXT Files", "*.txt"));
        return fileChooser.showOpenMultipleDialog(null);
    }
}
