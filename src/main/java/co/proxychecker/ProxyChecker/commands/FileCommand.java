package co.proxychecker.ProxyChecker.commands;

import java.io.File;
import java.util.List;

import javafx.stage.FileChooser;

/**
 * Contains static methods that allow selecting a file to save or files to open in a dialog.
 */
public class FileCommand {

    private static FileChooser fileChooser;

    /**
     * Show a dialog and get a File to save
     * @param table - Whether or not the file dialog is to save a table (csv).
     * @return File - The selected File object.
     */
    public static File getFileToSave(boolean table) {
        fileChooser = new FileChooser();
        fileChooser.setTitle("Save File");
        if(table) {
            fileChooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("CSV File", "*.csv")
            );
        } else {
            fileChooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("TXT Files", "*.txt")
            );
        }
        return fileChooser.showSaveDialog(null);
    }

    /**
     * Shows a dialog and gets one or more files to open
     * @return List - A list of files selected
     */
    public static List<File> getFilesToOpen() {
        fileChooser = new FileChooser();
        fileChooser.setTitle("Open File(s)");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("TXT Files", "*.txt")
        );
        return fileChooser.showOpenMultipleDialog(null);
    }
}
