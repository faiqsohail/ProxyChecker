package co.proxychecker.ProxyChecker.gui.events;

import javafx.event.EventHandler;
import javafx.scene.control.ListView;
import javafx.scene.control.TableView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import co.proxychecker.ProxyChecker.commands.ProxyCheckCommand;
import co.proxychecker.ProxyChecker.components.entities.Proxy;

/**
 * Key event handler for the loaded proxies list (delete) and the result table (copy).
 */
public class ProxyCheckerKeyEvent implements EventHandler<KeyEvent> {

    @Override
    public void handle(KeyEvent event) {
        if (event.getSource() instanceof ListView) {
            @SuppressWarnings("unchecked")
            ListView<String> view = (ListView<String>) event.getSource();
            // allow deleting items from the list, only if the proxy checker isn't running
            if ((event.getCode() == KeyCode.DELETE || event.getCode() == KeyCode.BACK_SPACE)
                    && !ProxyCheckCommand.isRunning()) {
                String selected = view.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    view.getItems().remove(selected);
                }
            }
        } else if (event.getSource() instanceof TableView) {
            if (event.getCode() == KeyCode.C) { // copy "ip:port" of the selected row
                @SuppressWarnings("unchecked")
                TableView<Proxy> tableView = (TableView<Proxy>) event.getSource();
                Proxy proxy = tableView.getSelectionModel().getSelectedItem();
                if (proxy != null) {
                    ClipboardContent content = new ClipboardContent();
                    content.putString(proxy.getIp() + ":" + proxy.getPort());
                    Clipboard.getSystemClipboard().setContent(content);
                }
            }
        }
    }
}
