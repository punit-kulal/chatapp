package sample;

import javafx.event.ActionEvent;
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
    public Button Server;
    public Button Client;
    public TextField ipaddress;
    static DataInputStream inputStream;
    static DataOutputStream outputStream;
    static Socket s;

    @FXML
    public void actasClient(ActionEvent actionEvent) {
        Parent chatnode = null;
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
        mystage.setTitle("Client");
        mystage.setScene(chatbox);

    }

    @FXML
    public void actAsServer(ActionEvent actionEvent) {
        Parent chatnode = null;
        try {
            ServerSocket listener = new ServerSocket(25000);
            s = listener.accept();
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
        Scene chatbox = new Scene(chatnode, 800, 800);
        Stage mystage = (Stage) ((Node) actionEvent.getSource()).getScene().getWindow();
        mystage.setTitle("Server");
        mystage.setScene(chatbox);
    }
}
