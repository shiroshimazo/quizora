package quizora.controllerTeacher;

import java.net.URL;
import java.text.NumberFormat;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import quizora.DAO.TeacherDashboardDAO;
import quizora.auth.UserSession;
import quizora.model.TeacherDashboardData;

public class teacherDashboardController implements Initializable {
    @FXML private Label studentsValue, publishedValue, quizzesValue, subjectsValue, submissionsValue, averageValue;
    @FXML private Label statusLabel, barEmptyLabel, lineEmptyLabel, pieEmptyLabel, periodLabel;
    @FXML private Button refreshButton;
    @FXML private BarChart<String, Number> subjectChart;
    @FXML private LineChart<String, Number> submissionChart;
    @FXML private PieChart statusChart;
    @FXML private ScrollPane overviewScroll;
    @FXML private GridPane kpiGrid, chartGrid;
    private int cardColumns;
    private int chartColumns;
    private Task<TeacherDashboardData> loading;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        overviewScroll.viewportBoundsProperty().addListener((observable, previous, bounds) ->
                adaptLayout(bounds.getWidth()));
        adaptLayout(900);
        refresh();
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
        if (identity == null || !"teacher".equals(identity.role())) {
            clear();
            statusLabel.setText("Sign in as an teacher to view your teaching overview.");
            refreshButton.setDisable(true);
            return;
        }
        refreshButton.setDisable(true);
        statusLabel.setText("Loading teaching overview...");
        loading = new Task<>() {
            @Override protected TeacherDashboardData call() throws Exception {
                return new TeacherDashboardDAO().load(identity);
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
            statusLabel.setText("Overview unavailable. Check your connection and teacher access, then refresh.");
        });
        Thread worker = new Thread(loading, "quizora-teacher-overview");
        worker.setDaemon(true);
        worker.start();
    }

    private void clear() {
        for (Label label : new Label[]{studentsValue, publishedValue, quizzesValue, subjectsValue,
                submissionsValue, averageValue}) label.setText("—");
        subjectChart.getData().clear();
        submissionChart.getData().clear();
        statusChart.getData().clear();
        barEmptyLabel.setText("Data unavailable");
        lineEmptyLabel.setText("Data unavailable");
        pieEmptyLabel.setText("Data unavailable");
        barEmptyLabel.setVisible(true);
        lineEmptyLabel.setVisible(true);
        pieEmptyLabel.setVisible(true);
    }

    void render(TeacherDashboardData data) {
        NumberFormat integer = NumberFormat.getIntegerInstance();
        studentsValue.setText(integer.format(data.students()));
        publishedValue.setText(integer.format(data.published()));
        quizzesValue.setText(integer.format(data.quizzes()));
        subjectsValue.setText(integer.format(data.subjects()));
        submissionsValue.setText(integer.format(data.submissions()));
        averageValue.setText(data.averageScore() == null ? "—" : String.format("%.1f%%", data.averageScore()));
        DateTimeFormatter date = DateTimeFormatter.ofPattern("MMM d");
        periodLabel.setText(data.today().minusDays(13).format(date) + " – " + data.today().format(date)
                + " · Last 14 days");
        subjectChart.getData().clear();
        XYChart.Series<String, Number> subjects = new XYChart.Series<>();
        for (var item : data.quizzesBySubject()) {
            var point = new XYChart.Data<String, Number>(item.label(), item.value());
            subjects.getData().add(point);
            point.nodeProperty().addListener((o, old, node) -> describe(node, item.label() + ": " + item.value() + " quizzes"));
        }
        subjectChart.getData().add(subjects);
        configureCounts((NumberAxis) subjectChart.getYAxis(),
                data.quizzesBySubject().stream().mapToLong(TeacherDashboardData.Count::value).max().orElse(0));
        barEmptyLabel.setText("No quizzes yet");
        barEmptyLabel.setVisible(data.quizzes() == 0);
        submissionChart.getData().clear();
        XYChart.Series<String, Number> days = new XYChart.Series<>();
        for (var day : data.submissionsByDay()) {
            var point = new XYChart.Data<String, Number>(day.date().format(date), day.value());
            days.getData().add(point);
            point.nodeProperty().addListener((o, old, node) ->
                    describe(node, day.date() + ": " + day.value() + " submissions"));
        }
        submissionChart.getData().add(days);
        configureCounts((NumberAxis) submissionChart.getYAxis(),
                data.submissionsByDay().stream().mapToLong(TeacherDashboardData.DailyCount::value).max().orElse(0));
        lineEmptyLabel.setText("No submissions in this period");
        lineEmptyLabel.setVisible(data.submissionsByDay().stream().allMatch(d -> d.value() == 0));
        statusChart.getData().clear();
        for (var item : data.quizStatuses()) {
            PieChart.Data slice = new PieChart.Data(item.label() + " (" + item.value() + ")", item.value());
            statusChart.getData().add(slice);
            describe(slice.getNode(), item.label() + ": " + item.value() + " quizzes");
        }
        pieEmptyLabel.setText("No quizzes yet");
        pieEmptyLabel.setVisible(data.quizzes() == 0);
    }

    private static void configureCounts(NumberAxis axis, long max) {
        double tick = Math.max(1, Math.ceil(max / 5.0));
        axis.setAutoRanging(false);
        axis.setLowerBound(0);
        axis.setUpperBound(Math.max(5, Math.ceil(max / tick) * tick));
        axis.setTickUnit(tick);
        axis.setMinorTickVisible(false);
    }

    private static void describe(Node node, String text) {
        if (node != null) {
            // JavaFX binds chart-symbol accessibility to the data item itself.
            if (!node.accessibleTextProperty().isBound()) node.setAccessibleText(text);
            Tooltip.install(node, new Tooltip(text));
        }
    }
}
