package sample;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;

import java.io.IOException;

import static sample.ControllerIndex.*;

/*
  Created by Punit Kulal on 2/21/2017.
 */
public class ControllerChat {

    public TextArea chat;
    public TextArea input;
    public Button send;
    public Button closesession;
    private Task<Void> updater;

    @FXML
    public void initialize() {
        //Task to keep on listening for input from stream
        updater = new Task<Void>() {
            final String SOURCE = "Friend: ";

            @Override
            protected Void call() throws Exception {
                while (!isCancelled()) {
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

    @FXML
    public void sendMessage() {
        String source = "Me: ", msg = input.getText();
        try {
            outputStream.writeUTF(msg);
        } catch (IOException e) {
            e.printStackTrace();
        }
        updateScreen(msg, source);
    }

    //Helper method to update current screen
    private void updateScreen(String msg, String source) {
        String chatScreen = chat.getText();
        chat.setText(chatScreen + "\n" + source + msg);
        input.setText("");
    }

    @FXML
    public void exit(ActionEvent actionEvent) {
        try {
            //Stop current executing task close all sockets
            updater.cancel();
            ControllerIndex.outputStream.close();
            if (listener != null) {
                ControllerIndex.listener.close();
            }
            ControllerIndex.s.close();
            //Load index page
            Parent node = FXMLLoader.load(getClass().getResource("index.fxml"));
            Stage mystage = (Stage) ((Node) actionEvent.getSource()).getScene().getWindow();
            mystage.setScene(new Scene(node, 600, 275));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
