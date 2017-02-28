package sample;

import com.google.gson.Gson;
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
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;

public class ControllerIndex {
    static final String ME = "ME", FRIEND = "FRIEND";
    static DataInputStream inputStream;
    static DataOutputStream outputStream;
    static Socket s;
    static ServerSocket listener;
    public Button server;
    public Button client;
    public TextField ipaddress;
    public Button cancelServer;
    public TextField me;
    public TextField friend;
    private HashMap<String, String> contacts = new HashMap<>();
    private Gson converter = new Gson();
    private ListenService listenService = new ListenService();
    private EventHandler<WorkerStateEvent> closeEvent = new EventHandler<WorkerStateEvent>() {
        @Override
        public void handle(WorkerStateEvent event) {
            try {
                if (listener != null)
                    listener.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    };

    @FXML
    public void initialize() {
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
            protected Void call() throws IOException {
                if (Files.exists(Paths.get("contact.json"))) {
                    FileReader reader = new FileReader("contact.json");
                    contacts = converter.fromJson(reader, HashMap.class);
                }
                //if(ipaddress.getText().equals(""))
                //TODO Get ipaddress from hashmap or inform not in storage.
                if (!contacts.containsKey(friend.getText().toLowerCase())) {
                    contacts.put(friend.getText().toLowerCase(), ipaddress.getText().trim());
                    String jsonMap = converter.toJson(contacts);
                    System.out.println(jsonMap);
                    if (!Files.exists(Paths.get("contact.json")))
                        Files.createFile(Paths.get("contact.json"));
                    FileWriter writer = new FileWriter("contact.json");
                    writer.write(jsonMap);
                    writer.close();
                }
                try {
                    s = new Socket(ipaddress.getText(), 25000);
                    inputStream = new DataInputStream(s.getInputStream());
                    outputStream = new DataOutputStream(s.getOutputStream());
                } catch (IOException e) {
                    e.printStackTrace();
                }

                return null;
            }
        };
        //Handler which changes the scene after connection is set.
        clientConnector.addEventHandler(WorkerStateEvent.WORKER_STATE_SUCCEEDED,
                event -> changeScene((Node) actionEvent.getSource(), "Client"));
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
        chatnode.getProperties().put(FRIEND, friend.getText());
        Scene chatbox = new Scene(chatnode, 800, 800);
        System.out.println(chatnode);
        Stage mystage = (Stage) n.getScene().getWindow();
        mystage.setTitle(title);
        mystage.setScene(chatbox);
    }

    @FXML
    public void actAsServer() {
        //Start a service to listen as server
        listenService.start();
    }

    @FXML
    public void cancelServer(ActionEvent actionEvent) throws IOException {
        //Close currently listening service and Enable buttons when server listen is cancelled.
        listenService.cancel();
        listenService.reset();
        cancelServer.setVisible(false);
        server.setDisable(false);
        client.setDisable(false);
        //Reset title
        Stage mystage = (Stage) ((Node) actionEvent.getSource()).getScene().getWindow();
        mystage.setTitle("ChatApp");
    }

    // A service which listens for connection
    class ListenService extends Service {
        @Override
        protected Task createTask() {
            Task t1 = new Task() {
                @Override
                protected Object call() {
                    try {
                        listener = new ServerSocket(25000);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    Platform.runLater(() -> {
                        Stage stage = (Stage) server.getScene().getWindow();
                        stage.setTitle("Waiting for Friend .... Please wait");
                        //Disabling connection buttons
                        server.setDisable(true);
                        client.setDisable(true);
                        cancelServer.setVisible(true);
                    });
                    try {
                        s = listener.accept();
                        inputStream = new DataInputStream(s.getInputStream());
                        outputStream = new DataOutputStream(s.getOutputStream());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return null;
                }
            };
            t1.addEventHandler(WorkerStateEvent.WORKER_STATE_CANCELLED, closeEvent);
            t1.addEventHandler(WorkerStateEvent.WORKER_STATE_FAILED, closeEvent);
            return t1;
        }
    }
}
//class Name{
//    public final String ME,FRIEND;
//
//    Name(String me, String f) {
//        ME = me;
//        FRIEND = f;
//    }
//}