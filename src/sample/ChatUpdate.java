package sample;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.TextArea;

import java.io.DataInputStream;

/**
 * Created by Punit on 2/21/2017.
 */
public class ChatUpdate extends Task<Void> {
    TextArea chat;
    String msg;
    final String source = "Friend";
    DataInputStream inputStream;

    public ChatUpdate(DataInputStream inputStream, TextArea chat) {
        this.inputStream = inputStream;
        this.chat = chat;

    }
    @Override
    protected Void call() throws Exception {
        while (!isCancelled()) {
            msg = inputStream.readUTF();
            Platform.runLater(new Runnable() {
                @Override
                public void run() {
                    String chatscreen = chat.getText();
                    chat.setText(chatscreen + "\n" + source + msg);
                }
            });
        }
        return null;
    }
}
