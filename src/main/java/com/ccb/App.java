package com.ccb;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class App extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/ccb/main.fxml"));
        Scene scene = new Scene(loader.load(), 1200, 750);
        scene.getStylesheets().add(getClass().getResource("/com/ccb/css/style.css").toExternalForm());
        stage.setTitle("CCB Inventory System");
        stage.setScene(scene);
        stage.setMinWidth(1000);
        stage.setMinHeight(650);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
