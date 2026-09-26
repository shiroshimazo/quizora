package quizora.controllerStudent;

import java.net.URL;
import java.text.NumberFormat;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;
import java.util.function.Function;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.util.StringConverter;
import quizora.DAO.StudentDashboardDAO;
import quizora.auth.UserSession;
import quizora.model.StudentDashboardData;
import quizora.model.StudentDashboardData.AvailableQuiz;
import quizora.model.StudentDashboardData.RecentResult;

public class studentDashboardController implements Initializable {
    @FXML private Label availableValue, notAttemptedValue, submissionsValue, completedValue, averageValue, bestValue;
    @FXML private Label statusLabel, barEmptyLabel;
    @FXML private Button refreshButton;
    @FXML private TableView<AvailableQuiz> availableTable;
    @FXML private TableView<RecentResult> recentTable;
    @FXML private BarChart<String, Number> subjectChart;
    @FXML private ScrollPane overviewScroll;
    @FXML private GridPane kpiGrid, chartGrid;
    private int cardColumns;
    private int chartColumns;
    private Task<StudentDashboardData> loading;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        column(availableTable, "Quiz", 200, AvailableQuiz::title);
        column(availableTable, "Subject", 140, AvailableQuiz::subject);
        column(availableTable, "Teacher", 140, AvailableQuiz::teacher);
        column(availableTable, "Questions", 90, AvailableQuiz::questions);
        column(availableTable, "Time limit", 90, q -> q.minutes() + " min");
        column(availableTable, "Your attempts", 110, AvailableQuiz::attempts);
        DateTimeFormatter submitted = DateTimeFormatter.ofPattern("MMM d, HH:mm");
        column(recentTable, "Quiz", 150, RecentResult::quiz);
        column(recentTable, "Score", 70, r -> r.score() + " / " + r.totalPoints());
        column(recentTable, "Percent", 70, r -> String.format("%.1f%%", r.percent()));
        column(recentTable, "Submitted", 130, r -> r.submittedAt().format(submitted));
        // The FXML upper bound is 100.001 because JavaFX drops a tick that lands exactly on it;
        // rounding keeps that top label reading "100".
        ((NumberAxis) subjectChart.getYAxis()).setTickLabelFormatter(new StringConverter<>() {
            @Override public String toString(Number value) { return Long.toString(Math.round(value.doubleValue())); }
            @Override public Number fromString(String text) { return Double.valueOf(text); }
        });
        overviewScroll.viewportBoundsProperty().addListener((observable, previous, bounds) ->
                adaptLayout(bounds.getWidth()));
        adaptLayout(900);
        refresh();
    }

    private static <S, T> void column(TableView<S> table, String title, int width, Function<S, T> value) {
        TableColumn<S, T> column = new TableColumn<>(title);
        column.setMinWidth(width);
        column.setCellValueFactory(row -> new ReadOnlyObjectWrapper<>(value.apply(row.getValue())));
        table.getColumns().add(column);
    }

    private void adaptLayout(double viewportWidth) {
        if (viewportWidth <= 0) return;
        int cards = viewportWidth < 600 ? 1 : viewportWidth < 1050 ? 2 : 3;
        if (cards != cardColumns) {
            cardColumns = cards;
            columns(kpiGrid, cards);
            for (int i = 0; i < kpiGrid.getChildren().size(); i++) {
                GridPane.setColumnIndex(kpiGrid.getChildren().get(i), i % cards);
                GridPane.setRowIndex(kpiGrid.getChildren().get(i), i / cards);
            }
        }
        int charts = viewportWidth < 1100 ? 1 : 2;
        if (charts != chartColumns) {
            chartColumns = charts;
            columns(chartGrid, charts);
            for (int i = 0; i < chartGrid.getChildren().size(); i++) {
                GridPane.setColumnIndex(chartGrid.getChildren().get(i), i % charts);
                GridPane.setRowIndex(chartGrid.getChildren().get(i), i / charts);
            }
        }
    }

    private static void columns(GridPane grid, int count) {
        grid.getColumnConstraints().clear();
        for (int i = 0; i < count; i++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setMinWidth(0);
            column.setPercentWidth(100.0 / count);
            grid.getColumnConstraints().add(column);
        }
    }

    @FXML
    private void refresh() {
        if (loading != null && loading.isRunning()) return;
        var identity = UserSession.current();
        if (identity == null || !"student".equals(identity.role())) {
            clear();
            statusLabel.setText("Sign in as a student to view your learning overview.");
            refreshButton.setDisable(true);
            return;
        }
        refreshButton.setDisable(true);
        statusLabel.setText("Loading learning overview...");
        loading = new Task<>() {
            @Override protected StudentDashboardData call() throws Exception {
                return new StudentDashboardDAO().load(identity);
            }
        };
        loading.setOnSucceeded(event -> {
            if (UserSession.current() != identity) { clear(); refreshButton.setDisable(true); return; }
            render(loading.getValue());
            refreshButton.setDisable(false);
            statusLabel.setText("Updated " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        });
        loading.setOnFailed(event -> {
            if (UserSession.current() != identity) { clear(); refreshButton.setDisable(true); return; }
            clear();
            refreshButton.setDisable(false);
            statusLabel.setText("Overview unavailable. Check your connection and student access, then refresh.");
        });
        Thread worker = new Thread(loading, "quizora-student-overview");
        worker.setDaemon(true);
        worker.start();
    }

    private void clear() {
        for (Label label : new Label[]{availableValue, notAttemptedValue, submissionsValue, completedValue,
                averageValue, bestValue}) label.setText("—");
        availableTable.getItems().clear();
        availableTable.setPlaceholder(new Label("Data unavailable"));
        recentTable.getItems().clear();
        recentTable.setPlaceholder(new Label("Data unavailable"));
        subjectChart.getData().clear();
        barEmptyLabel.setText("Data unavailable");
        barEmptyLabel.setVisible(true);
    }

    void render(StudentDashboardData data) {
        NumberFormat integer = NumberFormat.getIntegerInstance();
        availableValue.setText(integer.format(data.available()));
        notAttemptedValue.setText(integer.format(data.notAttempted()));
        submissionsValue.setText(integer.format(data.submissions()));
        completedValue.setText(integer.format(data.completedQuizzes()));
        averageValue.setText(percent(data.averageScore()));
        bestValue.setText(percent(data.bestScore()));
        availableTable.getItems().setAll(data.availableQuizzes());
        availableTable.setPlaceholder(new Label("No quizzes are available right now"));
        recentTable.getItems().setAll(data.recentResults());
        recentTable.setPlaceholder(new Label("No results yet. Finished quizzes appear here."));
        subjectChart.getData().clear();
        XYChart.Series<String, Number> subjects = new XYChart.Series<>();
        for (var item : data.averageBySubject()) {
            var point = new XYChart.Data<String, Number>(item.subject(), item.average());
            subjects.getData().add(point);
            String text = String.format("%s: %.1f%% average over %d attempt%s", item.subject(), item.average(),
                    item.attempts(), item.attempts() == 1 ? "" : "s");
            point.nodeProperty().addListener((o, old, node) -> describe(node, text));
        }
        subjectChart.getData().add(subjects);
        barEmptyLabel.setText("No scores yet");
        barEmptyLabel.setVisible(data.averageBySubject().isEmpty());
    }

    private static String percent(Double value) {
        return value == null ? "—" : String.format("%.1f%%", value);
    }

    private static void describe(Node node, String text) {
        if (node != null) {
            // JavaFX binds chart-symbol accessibility to the data item itself.
            if (!node.accessibleTextProperty().isBound()) node.setAccessibleText(text);
            Tooltip.install(node, new Tooltip(text));
        }
    }
}
