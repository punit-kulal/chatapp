package sample;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {
    @Override
    public void start(Stage primaryStage) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource("index.fxml"));
        primaryStage.setTitle("ChatApp");
        Scene index = new Scene(root, 600, 275);
        primaryStage.setScene(index);
        primaryStage.show();
        primaryStage.getOnCloseRequest();
    }

    //To close sockets when close butoon is clicked
    @Override
    public void stop() throws Exception {
        ControllerIndex.outputStream.close();
        ControllerIndex.listener.close();
        ControllerIndex.s.close();
    }

    public static void main(String[] args) {
        launch(args);
    }
}


//TODO Create a service to manage all task
