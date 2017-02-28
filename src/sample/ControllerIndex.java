package sample;

import javafx.application.Platform;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ControllerIndex {
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

    //private Task<Void> t1;
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
        listenService.setOnSucceeded(new EventHandler<WorkerStateEvent>() {
            @Override
            public void handle(WorkerStateEvent event) {
                Parent chatnode = null;
                try {
                    chatnode = FXMLLoader.load(getClass().getResource("chatbox.fxml"));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                assert chatnode != null;
                Scene chatbox = new Scene(chatnode, 800, 800);
                chatbox.setUserData(new Name(me.getText(),friend.getText()));
                Stage stage = (Stage) server.getScene().getWindow();
                stage.setTitle("server");
                stage.setScene(chatbox);

            }
        });
    }

    @FXML
    public void actasClient(ActionEvent actionEvent) {
        /*
        Encapsulating the logic in a task to improve GUI response.
        A task which Create a socket to connect to server.
        */
        Task clientConnector = new Task() {
            @Override
            protected Void call() {
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
        clientConnector.addEventHandler(WorkerStateEvent.WORKER_STATE_SUCCEEDED, (Event event) -> {
            //Handler which changes the scene after connection is set.
            // Switch to next window when connection is established
            Parent chatnode=null;
            try {
                chatnode = FXMLLoader.load(getClass().getResource("chatbox.fxml"));
            } catch (IOException e) {
                e.printStackTrace();
            }
            assert chatnode != null;
            Scene chatbox = new Scene(chatnode, 800, 800);
            chatbox.setUserData(new Name(me.getText(),friend.getText()));
            Stage mystage = (Stage) ((Node) actionEvent.getSource()).getScene().getWindow();
            mystage.setTitle("client");
            mystage.setScene(chatbox);
        });
        new Thread(clientConnector).start();

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
class Name{
    public final String ME,FRIEND;

    Name(String me, String f) {
        ME = me;
        FRIEND = f;
    }
}