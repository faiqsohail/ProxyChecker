package co.proxychecker.gui.events;

import javafx.event.EventHandler;
import javafx.scene.control.ListView;
import javafx.scene.control.TableView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import co.proxychecker.commands.ProxyCheckCommand;
import co.proxychecker.components.entities.Proxy;

/**
 * Key handler for the loaded proxies list (Delete/Backspace removes the selected proxy) and the
 * result table (C copies the selected proxy as it was loaded, including credentials).
 */
public class ProxyCheckerKeyEvent implements EventHandler<KeyEvent> {

    @Override
    public void handle(KeyEvent event) {
        if (event.getSource() instanceof ListView) {
            handleListKey(event);
        } else if (event.getSource() instanceof TableView) {
            handleTableKey(event);
        }
    }

    private static void handleListKey(KeyEvent event) {
        boolean delete = event.getCode() == KeyCode.DELETE || event.getCode() == KeyCode.BACK_SPACE;
        if (!delete || ProxyCheckCommand.isRunning()) {
            return;
        }
        ListView<?> view = (ListView<?>) event.getSource();
        Object selected = view.getSelectionModel().getSelectedItem();
        if (selected != null) {
            view.getItems().remove(selected);
        }
    }

    private static void handleTableKey(KeyEvent event) {
        if (event.getCode() != KeyCode.C) {
            return;
        }
        Object selected = ((TableView<?>) event.getSource()).getSelectionModel().getSelectedItem();
        if (selected instanceof Proxy) {
            Proxy proxy = (Proxy) selected;
            ClipboardContent content = new ClipboardContent();
            content.putString(proxy.getSpec());
            Clipboard.getSystemClipboard().setContent(content);
        }
    }
}
