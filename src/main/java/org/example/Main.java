// Java
package org.example;

import javafx.animation.PauseTransition;
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
import javafx.util.Duration;
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
    private String currentCategory = "Все"; // Все | Политика | Спорт

    private Button loadMoreBtn;
    private ProgressIndicator progress;

    @Override
    public void start(Stage stage) {
        TextField searchField = new TextField();
        searchField.setPromptText("Поиск новостей...");
        Button searchBtn = new Button("Искать");
        Button refreshBtn = new Button("Обновить");
        ComboBox<String> categoryBox = new ComboBox<>(FXCollections.observableArrayList("Все", "Политика", "Спорт"));
        categoryBox.getSelectionModel().select("Все");
        categoryBox.setPrefWidth(140);

        // --- Modern header/title ---
        Label header = new Label("NewsApp");
        header.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #2d3436; -fx-padding: 16 0 8 0;");
        HBox headerBox = new HBox(header);
        headerBox.setAlignment(Pos.CENTER);
        headerBox.setStyle("-fx-background-color: #f5f6fa; -fx-border-color: #dfe6e9; -fx-border-width: 0 0 1 0;");

        HBox top = new HBox(8, categoryBox, searchField, searchBtn, refreshBtn);
        top.setAlignment(Pos.CENTER_LEFT);
        top.setPadding(new Insets(10));
        HBox.setHgrow(searchField, Priority.ALWAYS);

        // --- Modernize top controls ---
        top.setStyle("-fx-background-color: #f5f6fa; -fx-border-color: #dfe6e9; -fx-border-width: 0 0 1 0; -fx-padding: 16 16 16 16;");
        searchField.setStyle("-fx-background-radius: 8; -fx-border-radius: 8; -fx-padding: 6 12; -fx-background-color: #fff; -fx-border-color: #b2bec3;");
        searchBtn.setStyle("-fx-background-radius: 8; -fx-background-color: #0984e3; -fx-text-fill: white; -fx-font-weight: bold;");
        refreshBtn.setStyle("-fx-background-radius: 8; -fx-background-color: #636e72; -fx-text-fill: white; -fx-font-weight: bold;");
        categoryBox.setStyle("-fx-background-radius: 8; -fx-border-radius: 8; -fx-background-color: #fff; -fx-border-color: #b2bec3;");

        ListView<NewsArticle> listView = new ListView<>(items);
        listView.setPlaceholder(new Label("Нет новостей"));
        listView.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(NewsArticle a, boolean empty) {
                super.updateItem(a, empty);
                if (empty || a == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("");
                    return;
                }
                String title = a.getTitle();
                String meta = (a.getSource() != null ? a.getSource() : "") +
                        (a.getPublishedAt() != null ? " • " + a.getPublishedAt() : "");
                String desc = a.getSummary() != null ? a.getSummary() : "";
                Text t = new Text(title);
                t.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-fill: #222f3e;");
                Text m = new Text(meta);
                m.setStyle("-fx-fill: #636e72; -fx-font-size: 11px;");
                Text d = new Text(desc);
                d.setWrappingWidth(600);
                d.setStyle("-fx-fill: #636e72;");
                VBox v = new VBox(2, t, m, d);
                v.setPadding(new Insets(10));
                v.setStyle("-fx-background-color: #fff; -fx-background-radius: 10; -fx-border-radius: 10; -fx-border-color: #dfe6e9; -fx-border-width: 1; -fx-effect: dropshadow(three-pass-box, rgba(44,62,80,0.07), 4, 0, 0, 2);");
                setGraphic(v);
                setStyle("-fx-padding: 6 12 6 12;");
                setOnMouseEntered(ev -> v.setStyle("-fx-background-color: #dfe6e9; -fx-background-radius: 10; -fx-border-radius: 10; -fx-border-color: #b2bec3; -fx-border-width: 1;"));
                setOnMouseExited(ev -> v.setStyle("-fx-background-color: #fff; -fx-background-radius: 10; -fx-border-radius: 10; -fx-border-color: #dfe6e9; -fx-border-width: 1; -fx-effect: dropshadow(three-pass-box, rgba(44,62,80,0.07), 4, 0, 0, 2);"));
                // Автодогрузка при прокрутке к концу
                if (getIndex() >= Main.this.items.size() - 5 && Main.this.hasNext && !Main.this.loading) {
                    Main.this.loadPage(false);
                }
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

        // --- Modernize ListView ---
        listView.setStyle("-fx-background-color: #f5f6fa;");

        loadMoreBtn = new Button("Загрузить ещё");
        loadMoreBtn.setOnAction(e -> loadPage(false));
        loadMoreBtn.setStyle("-fx-background-radius: 8; -fx-background-color: #00b894; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 24;");

        VBox centerBox = new VBox(listView, loadMoreBtn);
        centerBox.setSpacing(8);
        centerBox.setPadding(new Insets(8, 16, 16, 16));
        VBox.setVgrow(listView, Priority.ALWAYS);

        // --- Layout with header ---
        BorderPane root = new BorderPane();
        root.setTop(new VBox(headerBox, top));
        root.setCenter(centerBox);

        progress = new ProgressIndicator();
        progress.setVisible(false);
        VBox progressBox = new VBox(progress);
        progressBox.setAlignment(Pos.CENTER);
        progressBox.setPadding(new Insets(16));
        root.setBottom(progressBox);

        Scene scene = new Scene(root, 800, 650);
        // Attach CSS if available
        var cssUrl = getClass().getResource("/modern.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        }
        stage.setScene(scene);
        stage.setTitle("NewsApp");
        stage.show();

        // Поиск: дебаунс + Enter
        PauseTransition searchDebounce = new PauseTransition(Duration.millis(350));
        searchField.textProperty().addListener((obs, oldV, newV) -> {
            currentQuery = newV == null ? "" : newV.trim();
            searchDebounce.stop();
            searchDebounce.setOnFinished(ev -> loadPage(true));
            searchDebounce.playFromStart();
        });
        searchField.setOnAction(e -> loadPage(true));

        searchBtn.setOnAction(e -> {
            currentQuery = searchField.getText() == null ? "" : searchField.getText().trim();
            loadPage(true);
        });
        refreshBtn.setOnAction(e -> loadPage(true));

        categoryBox.valueProperty().addListener((obs, oldV, newV) -> {
            currentCategory = newV == null ? "Все" : newV;
            loadPage(true);
        });

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
                .supplyAsync(() -> service.fetchPage(currentCategory, currentQuery, pageToLoad, pageSize))
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