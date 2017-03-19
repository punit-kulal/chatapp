package sample;

import javafx.application.Platform;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.*;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.HashMap;

import static sample.Constant.*;

public class ControllerIndex {

    public Button server;
    public Button client;
    public TextField ipaddressField;
    public Button cancelServer;
    public TextField me;
    public TextField friend;
    public CheckBox encryption;
    private boolean myEncryptionState = false;
    private boolean friendEncryptionState = false;
    private Task<KeyPair> keypairGenerator = new Task<KeyPair>() {
        @Override
        protected KeyPair call() throws Exception {
            return KeyPairGenerator.getInstance("RSA").genKeyPair();
        }
    };
    private String ipAddress;
    private KeyPair keyPair = null;
    private ListenService listenService = new ListenService();
    private EventHandler<WorkerStateEvent> closeEvent = event -> {
        try {
            if (listener != null)
                listener.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    };

    @FXML
    public void initialize() {
        keypairGenerator.setOnSucceeded(event -> keyPair = keypairGenerator.getValue());
        new Thread(keypairGenerator).start();
        // Handler to switch to next window when connection is established
        listenService.setOnSucceeded(event -> changeScene(me, "Server"));
    }

    @FXML
    public void actasClient(ActionEvent actionEvent) {
        /*
        Encapsulating the logic in a task to improve GUI response.
        A task which Create a socket to connect to server.
        */
        Task clientConnector = new Task() {
            @Override
            protected Void call() throws IOException, ClassNotFoundException {
                myEncryptionState = encryption.isSelected();
                ipAddress = ipaddressField.getText();
                if (!friend.getText().equals("")) {
                    Friend = friend.getText();
                }
                //Read from existing contact.json file.
                if (Files.exists(Paths.get("contact.json"))) {
                    FileReader reader = new FileReader("contact.json");
                    contacts = converter.fromJson(reader, HashMap.class);
                }
                //Check storage when ipaddress is empty
                if (ipaddressField.getText().equals("")) {
                    //Check if contacts are empty.
                    if (contacts.isEmpty()) {
                        updateMessage("Contacts is empty.\n Please provide a friend name and valid ipaddress.");
                        throw new ContactException();
                    }
                    //check if contact contains the friend name
                    else if (!contacts.containsKey(friend.getText().toLowerCase())) {
                        updateMessage("No such name in contact storage.\nPlease provide an ipaddress");
                        throw new ContactException();
                    }
                    //Assign friend ipadress if friend name is present.
                    if (contacts.containsKey(friend.getText().toLowerCase())) {
                        ipAddress = contacts.get(friend.getText().toLowerCase());
                    }
                }
                s = new Socket(ipAddress, 25000);
                inputStream = new ObjectInputStream(s.getInputStream());
                outputStream = new ObjectOutputStream(s.getOutputStream());
                // Check if friend is not present in contact or (present in contact but ip address is not empty)
                if (!contacts.containsKey(friend.getText().toLowerCase()) ||
                        !ipaddressField.getText().equals("")) {
                    contacts.put(friend.getText().toLowerCase(), ipaddressField.getText().trim());
                    String jsonMap = converter.toJson(contacts);
                    if (!Files.exists(Paths.get("contact.json")))
                        Files.createFile(Paths.get("contact.json"));
                    FileWriter writer = new FileWriter("contact.json");
                    writer.write(jsonMap);
                    writer.close();
                }
                //keypairSharing
                outputStream.writeObject(myEncryptionState);
                friendEncryptionState = (boolean) inputStream.readObject();
                encryptionState = friendEncryptionState || myEncryptionState;
                if (encryptionState) {
                    privateKey = keyPair.getPrivate();
                    outputStream.writeObject(keyPair.getPublic());
                    publicKey = (PublicKey) inputStream.readObject();
                    Platform.runLater(() -> alertBuilder(Alert.AlertType.INFORMATION, "Encryption", "Your messages are now encrypted."));
                }
                //Set friend name if supplied
                if (!friend.getText().equals(""))
                    Friend = friend.getText();
                //Set my name which is used when sending messages.
                if (!me.getText().equals(""))
                    MyName = me.getText();
                return null;
            }
        };
        //Handler which changes the scene after connection is set.
        clientConnector.addEventHandler(WorkerStateEvent.WORKER_STATE_SUCCEEDED,
                event -> changeScene((Node) actionEvent.getSource(), "Client"));

        //Handler which shows alert according to exception.
        clientConnector.addEventHandler(WorkerStateEvent.WORKER_STATE_FAILED, event -> {
            if (clientConnector.getException() instanceof UnknownHostException) {
                Alert incorrectIP = new Alert(Alert.AlertType.ERROR);
                incorrectIP.setTitle("Unable to connect");
                incorrectIP.setContentText("The IP Address entered is invalid.");
                incorrectIP.showAndWait();
            } else if (clientConnector.getException() instanceof ConnectException) {
                Alert incorrectIP = new Alert(Alert.AlertType.ERROR);
                incorrectIP.setTitle("Unable to connect");
                incorrectIP.setContentText("Your friend is offline.");
                incorrectIP.showAndWait();
            } else {
                Alert emptyIP = new Alert(Alert.AlertType.WARNING);
                emptyIP.setTitle("Connection Error");
                emptyIP.setContentText("Reason Unknown.");
                emptyIP.showAndWait();
            }
        });
        new Thread(clientConnector).start();

    }

    // Switch to next window when connection is established
    private void changeScene(Node n, String title) {
        Parent chatnode = null;
        try {
            chatnode = FXMLLoader.load(getClass().getResource("chatbox.fxml"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        assert chatnode != null;
        chatnode.getProperties().put(ME, me.getText());
        chatnode.getProperties().put(Friend, friend.getText());
        Scene chatbox = new Scene(chatnode, 800, 400);
        Stage mystage = (Stage) n.getScene().getWindow();
        mystage.setTitle(title);
        mystage.setScene(chatbox);
    }

    @FXML
    public void actAsServer() {
        //Start a service to listen as server
        myEncryptionState = encryption.isSelected();
        listenService.start();
    }

    @FXML
    public void cancelServer(ActionEvent actionEvent) throws IOException {
        //Close currently listening service and Enable buttons when server listen is cancelled.
        listenService.cancel();
        listenService.reset();
        setButtons(true);
        //Reset title
        Stage mystage = (Stage) ((Node) actionEvent.getSource()).getScene().getWindow();
        mystage.setTitle("ChatApp");
    }

    //helsper maethod to set/unset buttons on server activation
    private void setButtons(boolean b) {
        cancelServer.setVisible(!b);
        cancelServer.setDisable(b);
        server.setVisible(b);
        server.setDisable(!b);
        client.setDisable(!b);
        encryption.setDisable(!b);
    }

    // A service which listens for connection
    class ListenService extends Service {
        @Override
        protected Task createTask() {
            Task t1 = new Task() {
                @Override
                protected Object call() throws IOException, ClassNotFoundException {
                    try {
                        listener = new ServerSocket(25000);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    Platform.runLater(() -> {
                        Stage stage = (Stage) server.getScene().getWindow();
                        stage.setTitle("Waiting for Friend .... Please wait");
                        //Disabling connection buttons
                        setButtons(false);
                    });
                    try {
                        s = listener.accept();
                        outputStream = new ObjectOutputStream(s.getOutputStream());
                        inputStream = new ObjectInputStream(s.getInputStream());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }


                    friendEncryptionState = (Boolean) inputStream.readObject();
                    outputStream.writeObject(myEncryptionState);
                    encryptionState = friendEncryptionState || myEncryptionState;
                    if (encryptionState) {
                        encryptionState = true;
                        privateKey = keyPair.getPrivate();
                        publicKey = (PublicKey) inputStream.readObject();
                        outputStream.writeObject(keyPair.getPublic());
                        Platform.runLater(() -> alertBuilder(Alert.AlertType.INFORMATION, "Encryption", "Your messages are now encrypted."));

                    }
                    //Set friend name if supplied
                    if (!friend.getText().equals(""))
                        Friend = friend.getText();
                    //Set my name which is used when sending messages.
                    if (!me.getText().equals(""))
                        MyName = me.getText();
                    return null;
                }
            };
            t1.addEventHandler(WorkerStateEvent.WORKER_STATE_CANCELLED, closeEvent);
            t1.addEventHandler(WorkerStateEvent.WORKER_STATE_FAILED, closeEvent);
            return t1;
        }
    }
}

class ContactException extends IOException {
}
