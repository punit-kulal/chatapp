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

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;

import static sample.ControllerIndex.*;

/*
  Created by Punit Kulal on 2/21/2017.
 */
public class ControllerChat {

    private final String E = "Iamclosing";
    private final String EXIT = Integer.toString(E.hashCode());
    public TextArea chat;
    public TextArea input;
    public Button send;
    public Button closeSession;
    private Task<Void> inputReader;
    private String ME;
    private boolean set = false;
    private Cipher encryptCipher;
    private Cipher decryptCipher;
    private Task contactUpdater;

    @FXML
    public void initialize() throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException {
        if (encryptionState) {
            encryptCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding");
            encryptCipher.init(Cipher.ENCRYPT_MODE, publicKey);
            decryptCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding");
            decryptCipher.init(Cipher.DECRYPT_MODE, privateKey);
        }
        //A task which updates friends connection in the contact if not present.
        contactUpdater = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                if (Files.exists(Paths.get("contact.json"))) {
                    FileReader reader = new FileReader("contact.json");
                    contacts = converter.fromJson(reader, HashMap.class);
                }
                if (!contacts.containsKey(FRIEND)) {
                    contacts.put(FRIEND.toLowerCase(), s.getInetAddress().getHostAddress());
                    String jsonMap = converter.toJson(contacts);
                    if (!Files.exists(Paths.get("contact.json")))
                        Files.createFile(Paths.get("contact.json"));
                    FileWriter writer = new FileWriter("contact.json");
                    writer.write(jsonMap);
                    writer.close();
                }
                return null;
            }
        };
        chat.textProperty().addListener(observable -> chat.setScrollTop(Double.MAX_VALUE));
        //Task to keep on listening for input from stream
        inputReader = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                String msg;
                byte[] input= new byte[1024*128];
                while (!isCancelled()) {
                    if (encryptionState) {
                        inputStream.read(input);
                        msg = new String(decryptCipher.doFinal(input),StandardCharsets.UTF_8);
                    }
                    else {
                        msg = inputStream.readUTF();
                    }
                    if (msg.equals(EXIT))
                        break;
                    String mymessage = msg;
                    //Updating screen
                    Platform.runLater(new Runnable() {
                        @Override
                        public void run() {
                            updateScreen(mymessage, "");
                        }
                    });
                }
                //Quiting to index because server has left.
                Platform.runLater(() -> forcedExit());
                return null;
            }
        };
        Thread t = new Thread(inputReader);
        t.start();
    }

    //Helper method to initialize the name of chatting
    private void setString() {
        ME = (String) closeSession.getParent().getProperties().get(ControllerIndex.ME);
        FRIEND = (String) closeSession.getParent().getProperties().get(ControllerIndex.FRIEND);
        set = true;
        new Thread(contactUpdater).start();
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
            connectionClosed.setContentText("Seems like " + FRIEND + " has disconnected.");
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
        String output;
        byte[] buffer = new byte[1024*128];
        String source = "Me: ", msg = input.getText();
        if (msg.equals(""))
            return;
        if (!set)
            setString();
        try {
            output = ME + ": " + msg;
            if(encryptionState){
                buffer = encryptCipher.doFinal(output.getBytes(StandardCharsets.UTF_8));
                outputStream.write(buffer);
            }
            else
            {outputStream.writeUTF(output);}
        } catch (IOException e) {
            e.printStackTrace();
        } catch (BadPaddingException e) {
            e.printStackTrace();
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
        }
        updateScreen(msg, source);
    }

    //Helper method to update current screen
    private void updateScreen(String msg, String source) {
        chat.appendText(source + msg + "\n");
        input.setText("");
    }

    @FXML
    public void exit(ActionEvent actionEvent) {
        try {
            //Notifying the other user to exit.
            outputStream.writeUTF(EXIT);
            //Stop current executing task close all sockets
            inputReader.cancel();
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
