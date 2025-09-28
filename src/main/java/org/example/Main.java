package org.example;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import org.example.NewsArticle;
import org.example.service.NewsService;

import java.util.concurrent.CompletableFuture;

public class Main extends Application {
    private final NewsService service = new NewsService();
    private final ObservableList<NewsArticle> items = FXCollections.observableArrayList();

    private int page = 0;
    private final int pageSize = 20;
    private boolean loading = false;
    private boolean hasNext = true;
    private String currentQuery = "";

    private Button loadMoreBtn;
    private ProgressIndicator progress;

    @Override
    public void start(Stage stage) {
        TextField searchField = new TextField();
        searchField.setPromptText("Searching...");
        Button searchBtn = new Button("Search");
        Button refreshBtn = new Button("Refresh");
        HBox top = new HBox(8, searchField, searchBtn, refreshBtn);
        top.setAlignment(Pos.CENTER_LEFT);
        top.setPadding(new Insets(10));
        HBox.setHgrow(searchField, Priority.ALWAYS);

        ListView<NewsArticle> listView = new ListView<>(items);
        listView.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(NewsArticle a, boolean empty) {
                super.updateItem(a, empty);
                if (empty || a == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                String title = a.getTitle();
                String meta = (a.getSource() != null ? a.getSource() : "") +
                        (a.getPublishedAt() != null ? " • " + a.getPublishedAt() : "");
                String desc = a.getSummary() != null ? a.getSummary() : "";
                Text t = new Text(title);
                t.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
                Text m = new Text(meta);
                m.setStyle("-fx-fill: -fx-text-inner-color; -fx-opacity: 0.7;");
                Text d = new Text(desc);
                d.setWrappingWidth(600);
                VBox v = new VBox(2, t, m, d);
                v.setPadding(new Insets(8));
                setGraphic(v);
            }
        });
        listView.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                NewsArticle sel = listView.getSelectionModel().getSelectedItem();
                if (sel != null && sel.getUrl() != null && !sel.getUrl().isBlank()) {
                    getHostServices().showDocument(sel.getUrl());
                }
            }
        });

        loadMoreBtn = new Button("Load More");
        loadMoreBtn.setOnAction(e -> loadPage(false));
        progress = new ProgressIndicator();
        progress.setVisible(false);
        HBox bottom = new HBox(10, loadMoreBtn, progress);
        bottom.setAlignment(Pos.CENTER);
        bottom.setPadding(new Insets(10));

        BorderPane root = new BorderPane();
        root.setTop(top);
        root.setCenter(listView);
        root.setBottom(bottom);

        searchBtn.setOnAction(e -> {
            currentQuery = searchField.getText() == null ? "" : searchField.getText().trim();
            loadPage(true);
        });
        refreshBtn.setOnAction(e -> loadPage(true));

        stage.setTitle("News");
        stage.setScene(new Scene(root, 800, 600));
        stage.show();

        loadPage(true);
    }

    private void loadPage(boolean reset) {
        if (loading) return;
        loading = true;
        progress.setVisible(true);
        loadMoreBtn.setDisable(true);

        if (reset) {
            page = 0;
            hasNext = true;
            items.clear();
        }
        if (!hasNext) {
            progress.setVisible(false);
            loadMoreBtn.setDisable(true);
            loading = false;
            return;
        }

        int pageToLoad = page;
        CompletableFuture
                .supplyAsync(() -> service.fetchPage(currentQuery, pageToLoad, pageSize))
                .whenComplete((result, error) -> Platform.runLater(() -> {
                    try {
                        if (error != null) {
                            showError("Ошибка загрузки: " + error.getMessage());
                            hasNext = true; // позволяем повторить попытку
                            return;
                        }
                        items.addAll(result.articles());
                        hasNext = result.hasNext();
                        if (result.articles().isEmpty()) {
                            hasNext = false;
                        } else {
                            page++;
                        }
                    } finally {
                        progress.setVisible(false);
                        loadMoreBtn.setDisable(!hasNext);
                        loading = false;
                    }
                }));
    }

    private void showError(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        a.setHeaderText("Ошибка");
        a.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}