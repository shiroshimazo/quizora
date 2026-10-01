package quizora.controllerStudent;

import java.util.*;
import java.util.function.Function;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import quizora.DAO.AvailableQuizDAO;
import quizora.DAO.AvailableQuizDAO.Quiz;
import quizora.auth.UserSession;

public class availableQuizzesController {
    @FXML private TextField searchField;
    @FXML private ComboBox<String> subjectFilter, progressFilter, sortFilter;
    @FXML private TableView<Quiz> quizTable;
    @FXML private Label statusLabel, countLabel, detailTitle, detailMeta, detailDescription;
    @FXML private Button refreshButton;
    @FXML private Button takeSelectedButton;
    private java.util.function.LongConsumer takeQuizHandler;

    public void setTakeQuizHandler(java.util.function.LongConsumer handler) {
        takeQuizHandler = handler;
        details(quizTable.getSelectionModel().getSelectedItem());
    }
    @FXML private void takeSelected() {
        Quiz quiz = quizTable.getSelectionModel().getSelectedItem();
        if (quiz != null && takeQuizHandler != null) takeQuizHandler.accept(quiz.id());
    }
    private List<Quiz> quizzes = List.of();
    private boolean loading;
    private static final String ALL = "All subjects";

    @FXML private void initialize() {
        column("Quiz", 220, Quiz::title);
        column("Subject", 140, Quiz::subject);
        column("Teacher", 160, Quiz::teacher);
        column("Questions", 95, Quiz::questions);
        column("Time limit", 100, quiz -> quiz.minutes() + " min");
        column("Your progress", 150, availableQuizzesController::progress);
        subjectFilter.getItems().add(ALL); subjectFilter.setValue(ALL);
        progressFilter.getItems().setAll("All progress", "Not attempted", "In progress", "Completed");
        progressFilter.setValue("All progress");
        sortFilter.getItems().setAll("Recently updated", "Title A-Z", "Shortest first");
        sortFilter.setValue("Recently updated");
        searchField.textProperty().addListener((o,a,b) -> filter());
        subjectFilter.valueProperty().addListener((o,a,b) -> filter());
        progressFilter.valueProperty().addListener((o,a,b) -> filter());
        sortFilter.valueProperty().addListener((o,a,b) -> filter());
        quizTable.getSelectionModel().selectedItemProperty().addListener((o,a,b) -> details(b));
        quizTable.setPlaceholder(new Label("Open this panel to load available quizzes."));
    }

    private <T> void column(String title, int width, Function<Quiz,T> value) {
        TableColumn<Quiz,T> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setSortable(false);
        column.setCellValueFactory(row -> new ReadOnlyObjectWrapper<>(value.apply(row.getValue())));
        quizTable.getColumns().add(column);
    }
    static String progress(Quiz quiz) {
        return quiz.inProgress() ? "In progress" : quiz.attempts() > 0 ? "Completed" : "Not attempted";
    }

    @FXML public void refresh() {
        if (loading) return;
        var identity = UserSession.current();
        quizzes = List.of(); filter();
        if (identity == null || !"student".equals(identity.role())) {
            statusLabel.setText("Sign in as a student to browse quizzes."); return;
        }
        loading = true; refreshButton.setDisable(true);
        statusLabel.setText("Loading available quizzes...");
        quizTable.setPlaceholder(new Label("Loading quizzes..."));
        Task<List<Quiz>> task = new Task<>() {
            @Override protected List<Quiz> call() throws Exception { return new AvailableQuizDAO().load(identity); }
        };
        task.setOnSucceeded(event -> {
            loading = false; refreshButton.setDisable(false);
            if (UserSession.current() != identity) { quizzes = List.of(); filter(); return; }
            render(task.getValue());
            statusLabel.setText("Select a quiz to view its description, points, and your attempts.");
        });
        task.setOnFailed(event -> {
            loading = false; refreshButton.setDisable(false);
            quizzes = List.of(); filter();
            statusLabel.setText(UserSession.current() != identity ? "Session changed. Sign in again."
                    : "Unable to load quizzes. Check your connection and student access, then refresh.");
            quizTable.setPlaceholder(new Label("Quizzes unavailable"));
        });
        Thread worker = new Thread(task,"quizora-available-quizzes"); worker.setDaemon(true); worker.start();
    }

    void render(List<Quiz> values) {
        quizzes = List.copyOf(values);
        String selected = subjectFilter.getValue();
        subjectFilter.getItems().setAll(ALL);
        values.stream().map(Quiz::subject).distinct().sorted(String.CASE_INSENSITIVE_ORDER).forEach(subjectFilter.getItems()::add);
        subjectFilter.setValue(subjectFilter.getItems().contains(selected) ? selected : ALL);
        filter();
    }

    @FXML private void clearFilters() {
        searchField.clear(); subjectFilter.setValue(ALL);
        progressFilter.setValue("All progress"); sortFilter.setValue("Recently updated");
        quizTable.getSortOrder().clear(); filter();
    }
    private void filter() {
        String search = searchField.getText().strip().toLowerCase(Locale.ROOT);
        var stream = quizzes.stream().filter(quiz ->
                (quiz.title() + " " + quiz.subject() + " " + quiz.teacher()).toLowerCase(Locale.ROOT).contains(search))
                .filter(quiz -> subjectFilter.getValue() == null || ALL.equals(subjectFilter.getValue()) || quiz.subject().equals(subjectFilter.getValue()))
                .filter(quiz -> progressFilter.getValue() == null || "All progress".equals(progressFilter.getValue()) || progress(quiz).equals(progressFilter.getValue()));
        if ("Title A-Z".equals(sortFilter.getValue())) stream = stream.sorted(Comparator.comparing(Quiz::title,String.CASE_INSENSITIVE_ORDER));
        if ("Shortest first".equals(sortFilter.getValue())) stream = stream.sorted(Comparator.comparingInt(Quiz::minutes));
        Quiz selected = quizTable.getSelectionModel().getSelectedItem();
        quizTable.getItems().setAll(stream.toList());
        if (selected != null && quizTable.getItems().contains(selected)) quizTable.getSelectionModel().select(selected);
        else { quizTable.getSelectionModel().clearSelection(); details(null); }
        quizTable.sort();
        countLabel.setText(quizTable.getItems().size() + " of " + quizzes.size() + " quizzes");
        quizTable.setPlaceholder(new Label(quizzes.isEmpty() ? "No published quizzes are available yet." : "No quizzes match your filters."));
    }
    private void details(Quiz quiz) {
        takeSelectedButton.setDisable(quiz == null || takeQuizHandler == null);
        detailTitle.setText(quiz == null ? "Select a quiz" : quiz.title());
        detailMeta.setText(quiz == null ? "Quiz details appear here." : quiz.subject() + " | " + quiz.teacher()
                + "\n" + quiz.questions() + " questions | " + quiz.minutes() + " minutes | " + quiz.points()
                + " points\n" + quiz.attempts() + " submitted attempts | " + progress(quiz));
        detailDescription.setText(quiz == null ? "" : quiz.description().isBlank() ? "No description provided." : quiz.description());
    }
}
