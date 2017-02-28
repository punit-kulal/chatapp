package sample;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
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
    public Button closeSession;
    private Task<Void> updater;
    String ME,FRIEND;
    private final String E = "Iamclosing";
    private final String EXIT = Integer.toString(E.hashCode());


    @FXML
    public void initialize() {
        Name chatters= (Name) chat.getScene().getUserData();
        ME=chatters.ME;
        FRIEND = chatters.FRIEND;
        chat.textProperty().addListener(observable -> chat.setScrollTop(Double.MAX_VALUE));
        //Task to keep on listening for input from stream
        updater = new Task<Void>() {
            final String SOURCE = "Friend: ";

            @Override
            protected Void call() throws Exception {
                while (!isCancelled()) {
                    String msg = inputStream.readUTF();
                    if (msg.equals(EXIT))
                        break;
                    //Updating screen
                    Platform.runLater(() -> updateScreen(msg,""));
                }
                //Quiting to index because server has left.
                Platform.runLater(() -> forcedExit());
                return null;
            }
        };
        Thread t = new Thread(updater);
        t.start();
    }

    private void forcedExit() {
        try {
            ControllerIndex.outputStream.close();
            if (listener != null) {
                ControllerIndex.listener.close();
            }
            ControllerIndex.s.close();
            //Alert for user
            Alert connectionClosed = new Alert(Alert.AlertType.ERROR);
            connectionClosed.setTitle("Connection closed.");
            connectionClosed.setContentText("Seems like "+ "friend "+ "has disconnected.");
            connectionClosed.showAndWait();
            //Load index page
            Parent node = FXMLLoader.load(getClass().getResource("index.fxml"));
            Stage mystage = (Stage) send.getScene().getWindow();
            mystage.setTitle("ChatApp");
            mystage.setScene(new Scene(node, 600, 275));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    public void sendMessage() {
        String source = "Me: ", msg = input.getText();
        if(msg.equals(""))
            return;
        try {
            outputStream.writeUTF(ME+": "+msg);
        } catch (IOException e) {
            e.printStackTrace();
        }
        updateScreen(msg, source);
    }

    //Helper method to update current screen
    private void updateScreen(String msg, String source) {
        String chatScreen = chat.getText();
        chat.appendText("\n" + source + msg);
        input.setText("");
    }

    @FXML
    public void exit(ActionEvent actionEvent) {
        try {
            //Notifying the other user to exit.
            outputStream.writeUTF(EXIT);
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
            mystage.setTitle("ChatApp");
            mystage.setScene(new Scene(node, 600, 275));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
