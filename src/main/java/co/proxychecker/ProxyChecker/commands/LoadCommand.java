package co.proxychecker.ProxyChecker.commands;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javafx.scene.control.Alert;
import javafx.scene.control.ListView;

import co.proxychecker.ProxyChecker.components.entities.Proxy;
import co.proxychecker.ProxyChecker.gui.AlertBox;

/**
 * Loads a file or string onto a ListView in the form ip:port
 */
public class LoadCommand {

    /**
     * Manages the addition of one or more files onto the ListView
     * @param view - Listview to add file contents onto
     * @param list - List of files to add onto the view (null to show dialog)
     */
    public static void file(ListView<String> view, List<File> list) {
        list = list == null ? FileCommand.getFilesToOpen() : list;
        if (list == null) {
            return;
        }
        // a HashSet keeps de-duplication O(1) per line; checking the ListView items is O(n) per line,
        // which made large lists (hundreds of thousands of lines) freeze the application
        Set<String> known = new HashSet<>(view.getItems());
        List<String> added = new ArrayList<>();
        for (File file : list) {
            try (BufferedReader br = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
                added.addAll(readNewProxies(br, known));
            } catch (NoSuchFileException e) {
                AlertBox.show(Alert.AlertType.INFORMATION, "File Not Found",
                        "The file you selected: " + file.getName() + ", doesn't seem to exist!");
            } catch (IOException e) {
                AlertBox.show(Alert.AlertType.ERROR, "File Exception",
                        "Unable to read the file " + file.getName() + ". Error: " + e.getMessage());
            }
        }
        // a single bulk update instead of one change event (and UI refresh) per line
        view.getItems().addAll(added);
    }

    /**
     * Reads valid proxies that aren't in {@code known} yet, adding them to {@code known}.
     */
    static List<String> readNewProxies(BufferedReader reader, Set<String> known) throws IOException {
        List<String> added = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null) {
            String value = normalize(line);
            if (Proxy.isValidFormat(value) && known.add(value)) {
                added.add(value);
            }
        }
        return added;
    }

    /**
     * Manages the addition of a single string onto the ListView
     * @param string - The String to add to the ListView
     * @param view - The ListView to add the string onto.
     */
    public static void string(String string, ListView<String> view) {
        if(view.getItems().contains(normalize(string))) {
            AlertBox.show(Alert.AlertType.INFORMATION,"Already Loaded",
                    "The proxy you entered is already loaded and is ready to be checked!");
        } else if(!addItem(string, view)) { // make sure it was added
            AlertBox.show(Alert.AlertType.ERROR,"Invalid Proxy Format",
                    "You must enter in a valid proxy in the format ip:port !");
        }
    }

    /**
     * Trims whitespace and a UTF-8 byte order mark, so "1.2.3.4:80 " and "1.2.3.4:80" are the same proxy.
     */
    static String normalize(String line) {
        String value = line.trim();
        if (!value.isEmpty() && value.charAt(0) == '\uFEFF') {
            value = value.substring(1).trim();
        }
        return value;
    }

    /**
     * Adds a string onto the listview provided its in a valid format
     * @param string - The String to add to the ListView
     * @param view - The ListView to add the string onto.
     * @return Boolean - Whether addition of item onto the listview was successful
     */
    private static boolean addItem(String string, ListView<String> view) {
        String value = normalize(string);
        if( (Proxy.isValidFormat(value)) && (!view.getItems().contains(value))) {
            return view.getItems().add(value);
        }
        return false;
    }
}
