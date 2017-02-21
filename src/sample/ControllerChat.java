package sample;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;

import java.io.IOException;

import static sample.ControllerIndex.inputStream;
import static sample.ControllerIndex.outputStream;
import static sample.ControllerIndex.s;

/**
 * Created by Punit on 2/21/2017.
 */
public class ControllerChat  {

    public TextArea chat;
    public TextArea input;
    public Button send;

    @FXML public void initialize(){
        System.out.println("reached initialize");
        Task<Void> updater = new Task<Void>() {
            final String SOURCE= "Friend: ";
            @Override
            protected Void call() throws Exception {
                while (!isCancelled()) {
                    System.out.print("Entered task");
                    String msg = inputStream.readUTF();
                    Platform.runLater(new Runnable() {
                        @Override
                        public void run() {
                            String chatscreen = chat.getText();
                            chat.setText(chatscreen + "\n" + SOURCE + msg);
                        }
                    });
                }
                return null;
            }
        };
        Thread t = new Thread(updater);
        t.start();
    }
    public void sendMessage(ActionEvent actionEvent) {
        String source="Me: ",msg = input.getText();
        try {
            outputStream.writeUTF(msg);
        } catch (IOException e) {
            e.printStackTrace();
        }
        updateScreen(msg,source);
    }
    private void updateScreen(String msg, String source) {
        String chatScreen = chat.getText();
        chat.setText(chatScreen + "\n" + source + msg);
        input.setText("");
    }

    public void exit(ActionEvent actionEvent) {
        try {
            s.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
