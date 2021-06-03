import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.scene.control.*;
import java.util.HashMap;
import javafx.geometry.Pos;
import javafx.scene.layout.Priority;
import javafx.event.*;
import javafx.application.Platform;
import javafx.stage.WindowEvent;
import javafx.scene.image.*;

public class RPSLS extends Application
{
    //UI elements
    Label portL;
    TextField portField;
    Button startButton;
    HashMap<String, Scene> sceneMap;
    ListView serverInfo;
    //Server thread and client listening threads
    Server server;
    private final Image rockImg = new Image("/rock.jpg"), paperImg = new Image("/paper.jpg"), scissorImg = new Image("/scissors.jpg"), lizardImg = new Image("/lizard.jpg"), spockImg = new Image("/spock.jpg");

    public static void main(String[] args) {
        // TODO Auto-generated method stub
        launch(args);
    }

    //feel free to remove the starter code from this method
    @Override
    public void start(Stage primaryStage) throws Exception {
        // TODO Auto-generated method stub
        primaryStage.setTitle("RPLS Server");

        portL = new Label("Port: ");
        portField = new TextField();
        startButton = new Button("Start");
        startButton.setDefaultButton(true);
        startButton.setOnAction(new EventHandler<ActionEvent>(){
            public void handle(ActionEvent e)
            {
                //Is our port an integer?
                try
                {
                    int port = Integer.parseInt(portField.getText());
                    primaryStage.setScene(sceneMap.get("run"));
                    primaryStage.sizeToScene();
                    server = new Server(data -> {
                        Platform.runLater(()->{
                            serverInfo.getItems().add(data.toString());
                            serverInfo.scrollTo(serverInfo.getItems().size() - 1);
                        });
                    }, port);
                    server.start();
                }
                catch (Exception ex)
                {
                    new Alert(Alert.AlertType.ERROR, "Port must be an integer number.", ButtonType.OK).show();
                }
            }
        });
        serverInfo = new ListView();
        serverInfo.getItems().add("Server Info: ");
        //Code to assign images to each item in listview if it contains a move report from the server
        serverInfo.setCellFactory(param -> new ListCell<String>(){
            private ImageView imageView = new ImageView();
            @Override
            public void updateItem(String name, boolean empty)
            {
                super.updateItem(name, empty);
                if (empty || name == null)
                {
                    setText("");
                    setGraphic(null);
                }
                else
                {
                    if (name.contains("rock"))
                        imageView.setImage(rockImg);
                    else if (name.contains("paper"))
                        imageView.setImage(paperImg);
                    else if (name.contains("scissors"))
                        imageView.setImage(scissorImg);
                    else if (name.contains("lizard"))
                        imageView.setImage(lizardImg);
                    else if (name.contains("spock"))
                        imageView.setImage(spockImg);
                    else
                        imageView.setImage(null);
                    setText(name);
                    setGraphic(imageView);
                }
            }
        });
        HBox sBox = new HBox(startButton);
        sBox.setAlignment(Pos.CENTER);
        HBox pBox = new HBox(portL, portField);
        pBox.setHgrow(portField, Priority.ALWAYS);
        Scene startScene = new Scene(new VBox(25, pBox, sBox));
        sceneMap = new HashMap<String, Scene>();
        sceneMap.put("start", startScene);
        VBox vBox = new VBox(serverInfo);
        serverInfo.setPrefHeight(125);
        //serverInfo.setPrefWidth(60);
        Scene runScene = new Scene(vBox);
        sceneMap.put("run", runScene);
        //Set the first scene to the start
        primaryStage.setScene(sceneMap.get("start"));
        primaryStage.setHeight(150);
        primaryStage.setWidth(300);
        primaryStage.setResizable(false);
        primaryStage.setOnCloseRequest(new EventHandler<WindowEvent>(){
            public void handle(WindowEvent e)
            {
                if (server != null)
                    server.shutdown();
                Platform.exit();
            }
        });
        primaryStage.show();
    }

}
