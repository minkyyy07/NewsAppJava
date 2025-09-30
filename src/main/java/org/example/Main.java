// Java
package org.example;

import javafx.animation.FadeTransition;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.Region;
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
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.example.NewsArticle;
import org.example.service.NewsService;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import java.util.concurrent.CompletableFuture;

public class Main extends Application {
    private final NewsService service = new NewsService();
    private final ObservableList<NewsArticle> items = FXCollections.observableArrayList();
    private final Set<String> seenUrls = new HashSet<>();
    private final ExecutorService io = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "news-io");
        t.setDaemon(true);
        return t;
    });
    private final AtomicLong nextId = new AtomicLong();
    private final AtomicLong requestId = new AtomicLong();

    private int page = 0;
    private final int pageSize = 20;
    private boolean loading = false;
    private boolean hasNext = true;
    private String currentQuery = "";
    private String currentCategory = "Все"; // Все | Политика | Спорт

    private Button loadMoreBtn;
    private ProgressIndicator progress;
    private Label statusLabel;

    @Override
    public void start(Stage stage) {
        TextField searchField = new TextField();
        searchField.setPromptText("Поиск новостей...");
        Button searchBtn = new Button("Искать");
        Button refreshBtn = new Button("Обновить");
        ComboBox<String> categoryBox = new ComboBox<>(FXCollections.observableArrayList("Все", "Политика", "Спорт"));
        categoryBox.getSelectionModel().select("Все");
        categoryBox.setPrefWidth(140);
        CheckBox darkToggle = new CheckBox("Тёмная тема");

        // --- Modern header/title ---
        Label header = new Label("NewsApp");
        header.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #2d3436; -fx-padding: 16 0 8 0;");
        HBox headerBox = new HBox(header);
        headerBox.setAlignment(Pos.CENTER);
        headerBox.setStyle("-fx-background-color: #f5f6fa; -fx-border-color: #dfe6e9; -fx-border-width: 0 0 1 0;");

        HBox top = new HBox(8, categoryBox, searchField, searchBtn, refreshBtn, darkToggle);
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
        // Улучшенный плейсхолдер
        Button resetBtn = new Button("Сбросить поиск");
        resetBtn.setOnAction(e -> {
            searchField.clear();
            categoryBox.getSelectionModel().select("Все");
            currentQuery = "";
            currentCategory = "Все";
            loadPage(true);
            showStatus("Поиск сброшен");
        });
        VBox placeholder = new VBox(8, new Label("Нет новостей"), resetBtn);
        placeholder.setAlignment(Pos.CENTER);
        placeholder.setPadding(new Insets(16));
        listView.setPlaceholder(placeholder);

        listView.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(NewsArticle a, boolean empty) {
                super.updateItem(a, empty);
                if (empty || a == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("");
                    setContextMenu(null);
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
                d.wrappingWidthProperty().bind(listView.widthProperty().subtract(48));
                d.setStyle("-fx-fill: #636e72;");
                VBox card = new VBox(2, t, m, d);
                card.setPadding(new Insets(10));
                card.getStyleClass().add("card");
                setGraphic(card);
                setStyle("-fx-padding: 6 12 6 12;");

                // Контекстное меню
                if (a.getUrl() != null && !a.getUrl().isBlank()) {
                    MenuItem open = new MenuItem("Открыть в браузере");
                    open.setOnAction(ev -> getHostServices().showDocument(a.getUrl()));
                    MenuItem copy = new MenuItem("Копировать ссылку");
                    copy.setOnAction(ev -> {
                        ClipboardContent content = new ClipboardContent();
                        content.putString(a.getUrl());
                        Clipboard.getSystemClipboard().setContent(content);
                        showStatus("Ссылка скопирована");
                    });
                    ContextMenu cm = new ContextMenu(open, copy);
                    setContextMenu(cm);
                } else {
                    setContextMenu(null);
                }

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

        // Статус-бар и индикатор
        progress = new ProgressIndicator();
        progress.setVisible(false);
        statusLabel = new Label("");
        statusLabel.getStyleClass().add("status-label");
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox statusBar = new HBox(12, statusLabel, spacer, progress);
        statusBar.getStyleClass().add("status-bar");
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setPadding(new Insets(8, 16, 8, 16));
        root.setBottom(statusBar);

        Scene scene = new Scene(root, 800, 650);
        // Attach CSS if available
        var cssUrl = getClass().getResource("/modern.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        }
        stage.setScene(scene);
        stage.setTitle("NewsApp");
        stage.show();

        // Тёмная тема переключатель
        darkToggle.selectedProperty().addListener((obs, was, isNow) -> {
            if (isNow) {
                if (!root.getStyleClass().contains("dark")) root.getStyleClass().add("dark");
            } else {
                root.getStyleClass().remove("dark");
            }
        });

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
            seenUrls.clear();
        }
        if (!hasNext) {
            progress.setVisible(false);
            loadMoreBtn.setDisable(true);
            loading = false;
            return;
        }

        long id = requestId.incrementAndGet();

        int pageToLoad = page;
        CompletableFuture
                .supplyAsync(() -> service.fetchPage(currentCategory, currentQuery, pageToLoad, pageSize), io)
                .whenComplete((result, error) -> Platform.runLater(() -> {
                    if (id != requestId.get()) {
                        progress.setVisible(false);
                        loading = false;
                        return;
                    }
                    try {
                        if (error != null) {
                            showError("Ошибка загрузки: " + error.getMessage());
                            hasNext = true; // позволяем повторить попытку
                            return;
                        }
                        int before = items.size();
                        result.articles().forEach(a -> {
                            String url = a.getUrl();
                            if (url != null && !url.isBlank() && seenUrls.add(url)) {
                                items.add(a);
                            }
                        });
                        int added = items.size() - before;
                        if (added > 0) {
                            showStatus("Добавлено " + added + " новостей");
                        } else if (!result.hasNext()) {
                            showStatus("Больше новостей нет");
                        }
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

    @Override public void stop() {
        io.shutdownNow();
    }

    private void showStatus(String msg) {
        statusLabel.setText(msg == null ? "" : msg);
        PauseTransition pt = new PauseTransition(Duration.seconds(3));
        pt.setOnFinished(e -> statusLabel.setText(""));
        pt.playFromStart();
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

