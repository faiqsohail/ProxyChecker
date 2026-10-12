package co.proxychecker.commands;

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

import co.proxychecker.components.entities.Proxy;
import co.proxychecker.gui.AlertBox;

/**
 * Loads proxies ({@code host:port}, {@code user:pass@host:port} or {@code host:port:user:pass}) from files or user input onto a ListView.
 */
public final class LoadCommand {

    private LoadCommand() {
    }

    /**
     * Adds the valid, not yet loaded proxies of one or more files to the view.
     *
     * @param view  list to add the proxies to
     * @param files files to load, or null to ask the user with a dialog
     */
    public static void file(ListView<String> view, List<File> files) {
        List<File> selected = files == null ? FileCommand.getFilesToOpen() : files;
        if (selected == null) {
            return;
        }
        // a HashSet keeps de-duplication O(1) per line; checking the ListView items is O(n) per line,
        // which made large lists (hundreds of thousands of lines) freeze the application
        Set<String> known = new HashSet<>(view.getItems());
        List<String> added = new ArrayList<>();
        for (File file : selected) {
            try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
                added.addAll(readNewProxies(reader, known));
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
     * Adds a single proxy entered by the user, telling them if it is invalid or already loaded.
     */
    public static void string(String input, ListView<String> view) {
        String value = normalize(input);
        if (!Proxy.isValidFormat(value)) {
            AlertBox.show(Alert.AlertType.ERROR, "Invalid Proxy Format",
                    "You must enter a valid proxy in the format host:port, user:pass@host:port or host:port:user:pass");
        } else if (view.getItems().contains(value)) {
            AlertBox.show(Alert.AlertType.INFORMATION, "Already Loaded",
                    "The proxy you entered is already loaded and is ready to be checked!");
        } else {
            view.getItems().add(value);
        }
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
     * Trims whitespace and a UTF-8 byte order mark, so "1.2.3.4:80 " and "1.2.3.4:80" are the same proxy.
     */
    static String normalize(String line) {
        String value = line.trim();
        if (!value.isEmpty() && value.charAt(0) == '\uFEFF') {
            value = value.substring(1).trim();
        }
        return value;
    }
}
