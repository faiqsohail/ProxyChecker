package co.proxychecker.ProxyChecker.gui.events;

import java.awt.*;
import java.awt.datatransfer.StringSelection;

import javafx.event.EventHandler;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.control.ListView;
import javafx.scene.control.TableView;

import co.proxychecker.ProxyChecker.components.entities.Proxy;
import co.proxychecker.ProxyChecker.commands.ProxyCheckCommand;

/**
 * Event Handler for ProxyCheckerController
 */
public class ProxyCheckerKeyEvent implements EventHandler<KeyEvent> {
    @Override
    public void handle(KeyEvent event) {
        if(event.getSource() instanceof ListView) {
            ListView<String> view = (ListView)event.getSource();
            // allow deleting items from a ListView
            if ((event.getCode().equals(KeyCode.DELETE)) || (event.getCode().equals(KeyCode.BACK_SPACE))) {
                if(!ProxyCheckCommand.isRunning()) { // only if proxy checker isn't running
                    view.getItems().remove(view.getSelectionModel().getSelectedItem());
                }
            }
        } else if(event.getSource() instanceof TableView) {
            if (event.getCode().equals(KeyCode.C)) { // copy from TableView
                TableView<Proxy> tableView = (TableView)event.getSource();

                Proxy proxy = tableView.getSelectionModel().getSelectedItem();
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                        new StringSelection(proxy.getIp() + ":" + proxy.getPort()), null
                );
            }
        }
    }
}
