package sample;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static sample.Constant.*;

public class Main extends Application {
    public static void main(String[] args) {
        launch(args);
    }
        @Override
    public void start(Stage primaryStage) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource("index.fxml"));
        primaryStage.setTitle("ChatApp");
        Scene index = new Scene(root, 600, 275);
            //index.style
        primaryStage.setScene(index);
        primaryStage.setMinHeight(450);
        primaryStage.setMinWidth(400);
        primaryStage.show();
        primaryStage.getOnCloseRequest();
    }

    //To close sockets when close butoon is clicked
    @Override
    public void stop() {
        if (encryptionState && s.isConnected()){
            byte[] sendBuffer;
            try {
                sendBuffer = encryptCipher.doFinal(Base64.getEncoder().encode(EXIT.getBytes(StandardCharsets.UTF_8)));
                outputStream.writeObject(sendBuffer);
                if (outputStream != null)
                    outputStream.close();
                if (listener != null)
                    listener.close();
                if (s != null)
                    s.close();
            } catch (IllegalBlockSizeException | BadPaddingException e) {
                e.printStackTrace();
            } catch (IOException e) {
                System.out.println("Stream already closed.");
            }
        }
    }
}



//TODO Redesign chat box layout