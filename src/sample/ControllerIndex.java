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
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ControllerIndex {
    public Button server;
    public Button client;
    public TextField ipaddress;
    static DataInputStream inputStream;
    static DataOutputStream outputStream;
    static Socket s;
    static ServerSocket listener;
    public Button cancelServer;
    //private Task<Void> t1;
    private ListenService listenService = new ListenService();
    private EventHandler<WorkerStateEvent> closeEvent= new EventHandler<WorkerStateEvent>() {
        @Override
        public void handle(WorkerStateEvent event) {
            try {
                if(listener!=null)
                listener.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    };

    @FXML
    public void initialize() {
        //Defining a task to create a create a ServerSocket and listen for incoming connection
//        t1 = new Task<Void>() {
//            @Override
//            protected Void call() throws Exception {
//                listener = new ServerSocket(25000);
//                Platform.runLater(() -> {
//                    Stage stage = (Stage) server.getScene().getWindow();
//                    stage.setTitle("Waiting for Friend .... Please wait");
//                    //Disabling connection buttons
//                    server.setDisable(true);
//                    client.setDisable(true);
//                    cancelServer.setVisible(true);
//                });
//                //System.out.println("Reached task server.");
//                s = listener.accept();
//                inputStream = new DataInputStream(s.getInputStream());
//                outputStream = new DataOutputStream(s.getOutputStream());
//                return null;
//            }
//
//        };
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
                Stage stage = (Stage) server.getScene().getWindow();
                stage.setTitle("server");
                stage.setScene(chatbox);

            }
        });
        //Close the Serversocket when listener fails or get cancelled.
        listenService.setOnCancelled(closeEvent);
        listenService.setOnFailed(closeEvent);
    }
//        t1.addEventHandler(WorkerStateEvent.WORKER_STATE_SUCCEEDED,
//        });

    @FXML
    public void actasClient(ActionEvent actionEvent) {
        Parent chatnode = null;
        //Create a socket to connect to server.
        try {
            s = new Socket(ipaddress.getText(), 25000);
            inputStream = new DataInputStream(s.getInputStream());
            outputStream = new DataOutputStream(s.getOutputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            chatnode = FXMLLoader.load(getClass().getResource("chatbox.fxml"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        assert chatnode != null;
        Scene chatbox = new Scene(chatnode, 800, 800);
        Stage mystage = (Stage) ((Node) actionEvent.getSource()).getScene().getWindow();
        mystage.setTitle("client");
        mystage.setScene(chatbox);

    }

    @FXML
    public void actAsServer(ActionEvent actionEvent) {
        //Start a thread to listen as server
        System.out.println(listenService.getState());
        listenService.start();
    }

    public void cancelServer(ActionEvent actionEvent) throws IOException {
        //Close currently listening service;
        listenService.cancel();
        //Recreate task for listening
        /*t1 = new Task<Void>() {
            @Override
            protected Void call() throws IOException {
                listener = new ServerSocket(25000);
                Platform.runLater(() -> {
                    Stage stage = (Stage) server.getScene().getWindow();
                    stage.setTitle("Waiting for Friend .... Please wait");
                    //Disabling connection buttons
                    server.setDisable(true);
                    client.setDisable(true);
                    cancelServer.setVisible(true);
                });
                //System.out.println("Reached task server.");
                s = listener.accept();
                inputStream = new DataInputStream(s.getInputStream());
                outputStream = new DataOutputStream(s.getOutputStream());
                return null;
            }
        };*/
        //Adding Eventhandler to change scene when connection is set up
//        t1.addEventHandler(WorkerStateEvent.WORKER_STATE_SUCCEEDED, new EventHandler<WorkerStateEvent>() {
//            @Override
//            public void handle(WorkerStateEvent event) {
//                Parent chatnode = null;
//                try {
//                    chatnode = FXMLLoader.load(getClass().getResource("chatbox.fxml"));
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//                assert chatnode != null;
//                Scene chatbox = new Scene(chatnode, 800, 800);
//                Stage stage = (Stage) server.getScene().getWindow();
//                stage.setTitle("server");
//                stage.setScene(chatbox);
//            }
//        });
        //Enable buttons when server listen is cancelled.
        cancelServer.setVisible(false);
        server.setDisable(false);
        client.setDisable(false);
        //Reset title
        Stage mystage = (Stage) ((Node) actionEvent.getSource()).getScene().getWindow();
        mystage.setTitle("ChatApp");
    }
    class ListenService extends Service{
        @Override
        protected Task createTask() {
            System.out.println("reached into task ");
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
            //System.out.println("Reached task server.");
            try {
                s = listener.accept();
                inputStream = new DataInputStream(s.getInputStream());
                outputStream = new DataOutputStream(s.getOutputStream());
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }
    }
}
