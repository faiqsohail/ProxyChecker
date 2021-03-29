package co.proxychecker.ProxyChecker.commands;

import java.io.*;
import java.util.List;

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
        if (list != null) {
            for (File file : list) {
                try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        addItem(line, view); // only add if its a valid line
                    }
                } catch (FileNotFoundException e) {
                    AlertBox.show(Alert.AlertType.INFORMATION, "File Not Found",
                            "The file you selected: " + file.getName() + ", doesn't seem to exist!");
                } catch (IOException e) {
                    AlertBox.show(Alert.AlertType.ERROR, "File Exception",
                            "Unable to read the file " + file.getName() + ". Error: " + e.getMessage());
                }
            }
        }

    }

    /**
     * Manages the addition of a single string onto the ListView
     * @param string - The String to add to the ListView
     * @param view - The ListView to add the string onto.
     */
    public static void string(String string, ListView<String> view) {
        if(view.getItems().contains(string)) {
            AlertBox.show(Alert.AlertType.INFORMATION,"Already Loaded",
                    "The proxy you entered is already loaded and is ready to be checked!");
        } else if(!addItem(string, view)) { // make sure it was added
            AlertBox.show(Alert.AlertType.ERROR,"Invalid Proxy Format",
                    "You must enter in a valid proxy in the format ip:port !");
        }
    }

    /**
     * Adds a string onto the listview provided its in a valid format
     * @param string - The String to add to the ListView
     * @param view - The ListView to add the string onto.
     * @return Boolean - Whether addition of item onto the listview was successful
     */
    private static boolean addItem(String string, ListView<String> view) {
        if( (Proxy.isValidFormat(string)) && (!view.getItems().contains(string))) {
            return view.getItems().add(string);
        }
        return false;
    }
}
