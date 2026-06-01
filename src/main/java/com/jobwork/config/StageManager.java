package com.jobwork.config;

import com.jobwork.config.FxmlView;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import java.io.IOException;

@Component
public class StageManager {

    private final ApplicationContext ctx;
    private Stage primaryStage;

    public StageManager(ApplicationContext ctx) {
        this.ctx = ctx;
    }

    public void setPrimaryStage(Stage stage) {
        this.primaryStage = stage;
    }

    /** Replace the scene on the primary (main) stage. */
    public void showScene(FxmlView view) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource(view.getFxmlPath()));
            loader.setControllerFactory(ctx::getBean);
            Parent root = load(view);

            Scene scene = primaryStage.getScene();
            if (scene == null) {
                scene = new Scene(root);
                applyStylesheet(scene);
                primaryStage.setScene(scene);
            } else {
                scene.setRoot(root);
            }
            primaryStage.setTitle(view.getTitle());
            primaryStage.show();
        } catch (IOException e) {
            throw new RuntimeException(
                    "Cannot load FXML: " + view.getFxmlPath(), e);
        }
    }

    /** Open a modal child window and return its controller. */
    public <T> T showModal(FxmlView view) {
        try {
            FXMLLoader loader = loader(view);
            Parent root = loader.load();
            Stage modal = new Stage();
            modal.setTitle(view.getTitle());
            Scene scene = new Scene(root);
            applyStylesheet(scene);
            modal.setScene(scene);
            modal.initOwner(primaryStage);
            modal.initModality(Modality.WINDOW_MODAL);
            modal.showAndWait();
            return loader.getController();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Parent load(FxmlView view) throws IOException {

        return loader(view).load();

    }

    private FXMLLoader loader(FxmlView view) {
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource(view.getFxmlPath()));
        // The critical line: Spring supplies controller instances
        loader.setControllerFactory(ctx::getBean);
        return loader;
    }

    private void applyStylesheet(Scene scene) {
        String css = getClass()
                .getResource("/css/style.css")
                .toExternalForm();
        scene.getStylesheets().add(css);
    }
    public <T> T showModalWithData(FxmlView view, java.util.function.Consumer<T> controllerInitializer) {

        try {
            FXMLLoader loader = loader(view);
            Parent root = loader.load();

            T controller = loader.getController();

            // 🔥 PASS DATA TO CONTROLLER
            controllerInitializer.accept(controller);

            Stage modal = new Stage();
            modal.setTitle(view.getTitle());

            Scene scene = new Scene(root);
            applyStylesheet(scene);

            modal.setScene(scene);
            modal.initOwner(primaryStage);
            modal.initModality(Modality.WINDOW_MODAL);

            modal.showAndWait();

            return controller;

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public void showChallanDetail(Long workerId,String challanNo) {

        showModalWithData(
                FxmlView.CHALLAN_DETAIL,
                controller -> {
                    ((com.jobwork.controller.ChallanDetailController) controller)
                            .loadData(workerId,challanNo);
                }
        );
    }

}