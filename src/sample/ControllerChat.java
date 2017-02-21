package sample;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;

import java.io.IOException;

import static sample.ControllerIndex.*;

/**
 * Created by Punit on 2/21/2017.
 */
public class ControllerChat {

    public TextArea chat;
    public TextArea input;
    public Button send;

    @FXML
    public void initialize() {
        System.out.println("reached initialize");
        Task<Void> updater = new Task<Void>() {
            final String SOURCE = "Friend: ";

            @Override
            protected Void call() throws Exception {
                while (!isCancelled()) {
                    System.out.print("Entered task");
                    String msg = inputStream.readUTF();
                    Platform.runLater(() -> {
                        String chatscreen = chat.getText();
                        chat.setText(chatscreen + "\n" + SOURCE + msg);
                    });
                }
                return null;
            }
        };
        Thread t = new Thread(updater);
        t.start();
    }

    public void sendMessage() {
        String source = "Me: ", msg = input.getText();
        try {
            outputStream.writeUTF(msg);
        } catch (IOException e) {
            e.printStackTrace();
        }
        updateScreen(msg, source);
    }

    private void updateScreen(String msg, String source) {
        String chatScreen = chat.getText();
        chat.setText(chatScreen + "\n" + source + msg);
        input.setText("");
    }

    public void exit(ActionEvent actionEvent) {
        try {
            s.close();
            Parent node = FXMLLoader.load(getClass().getResource("index.fxml"));
            Stage mystage = (Stage) ((Node) actionEvent.getSource()).getScene().getWindow();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
