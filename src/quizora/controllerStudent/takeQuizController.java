package quizora.controllerStudent;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import quizora.DAO.TakeQuizDAO;
import quizora.DAO.TakeQuizDAO.*;
import quizora.auth.AuthenticatedUser;
import quizora.auth.UserSession;

public class takeQuizController {
    @FXML private ScrollPane takeQuizScroll;
    @FXML private VBox selectionPane, attemptPane, questionPane, answersPane, resultPane;
    @FXML private ComboBox<Choice> quizPicker;
    @FXML private ComboBox<String> questionPicker;
    @FXML private Button startButton, refreshButton, previousButton, nextButton, submitButton, clearButton;
    @FXML private Label statusLabel, selectionInfo, quizTitle, timerLabel, progressLabel, questionNumber, questionText, scoreLabel, resultLabel;
    @FXML private ProgressBar answerProgress;
    private final TakeQuizDAO dao;
    private Attempt attempt;
    private AuthenticatedUser owner;
    private long sessionGeneration;
    private int questionIndex;
    private boolean busy, rendering, expiryFailed;
    private long preferredQuiz;
    private Timeline timer;
    public takeQuizController() { this(new TakeQuizDAO()); }
    public takeQuizController(TakeQuizDAO dao) { this.dao = dao; }

    @FXML private void initialize() {
        quizPicker.valueProperty().addListener((o,a,b) -> {
            selectionInfo.setText(b == null ? "" : b.minutes() + " minutes. " + (b.resume() ? "Resume your saved attempt; the original deadline still applies." : "The timer starts after you confirm."));
            startButton.setText(b != null && b.resume() ? "Resume quiz" : "Start quiz");
            controls();
        });
        questionPicker.getSelectionModel().selectedIndexProperty().addListener((o,a,b) -> {
            if (!rendering && b.intValue() >= 0 && !busy) { questionIndex = b.intValue(); showQuestion(); }
        });
        timer = new Timeline(new KeyFrame(Duration.seconds(1), event -> tick()));
        timer.setCycleCount(Timeline.INDEFINITE);
    }

    public void selectQuiz(long quizId) { preferredQuiz = quizId; }
    @FXML public void open() {
        if (busy) return;
        if (attempt != null && sameSession() && (attempt.score() == null || preferredQuiz == 0)) {
            if (preferredQuiz != 0 && preferredQuiz != attempt.quizId())
                statusLabel.setText("Finish your current quiz before starting another quiz.");
            preferredQuiz = 0;
            tick(); return;
        }
        reset();
        owner = UserSession.current();
        sessionGeneration = UserSession.generation();
        if (owner == null || !"student".equals(owner.role())) {
            statusLabel.setText("Sign in as a student to take a quiz."); return;
        }
        var identity = owner;
        run(() -> dao.choices(identity), choices -> {
            quizPicker.getItems().setAll(choices);
            choices.stream().filter(quiz -> quiz.id() == preferredQuiz).findFirst().ifPresent(quizPicker::setValue);
            statusLabel.setText(choices.isEmpty() ? "No quizzes are available yet." : "Choose a quiz to begin or resume an attempt.");
            preferredQuiz = 0;
        }, "Loading quizzes...");
    }
    @FXML private void start() {
        Choice quiz = quizPicker.getValue();
        if (busy || quiz == null) return;
        if (!quiz.resume() && !confirm("Start this quiz?", "You have " + quiz.minutes() + " minutes. The timer continues even if you leave.")) return;
        var identity = owner;
        questionIndex = 0;
        run(() -> dao.open(identity,quiz.id()), this::render, quiz.resume() ? "Resuming quiz..." : "Starting quiz...");
    }
    private <T> void run(Callable<T> work, Consumer<T> success, String message) {
        if (busy) return;
        var identity = owner;
        busy = true; controls(); statusLabel.setText(message);
        Task<T> task = new Task<>() { @Override protected T call() throws Exception { return work.call(); } };
        task.setOnSucceeded(event -> {
            busy = false;
            if (!sameSession()) { reset(); statusLabel.setText("Session changed. Sign in again."); return; }
            success.accept(task.getValue()); controls();
        });
        task.setOnFailed(event -> {
            busy = false;
            if (!sameSession()) { reset(); statusLabel.setText("Session changed. Sign in again."); return; }
            if (attempt != null) showQuestion();
            Throwable failure = task.getException();
            statusLabel.setText(failure instanceof IllegalArgumentException || failure instanceof SecurityException
                    ? failure.getMessage() : "Changes could not be saved. Check your connection and try again.");
            if (expired()) {
                expiryFailed = true;
                statusLabel.setText("Time ended. Submission failed; saved answers are safe. Click Retry submission.");
            }
            controls();
        });
        Thread worker = new Thread(task,"quizora-take-quiz"); worker.setDaemon(true); worker.start();
    }
    private void render(Attempt value) {
        attempt = value;
        show(selectionPane,false);
        show(resultPane,value.score() != null);
        show(attemptPane,value.score() == null);
        if (value.score() != null) {
            timer.stop();
            scoreLabel.setText(value.score().earned() + " / " + value.score().possible());
            resultLabel.setText(value.title() + "\n" + String.format(java.util.Locale.ROOT,"%.1f%%",100.0 * value.score().earned()/value.score().possible()) + " — Your result has been saved.");
            statusLabel.setText("Quiz submitted successfully.");
            return;
        }
        quizTitle.setText(value.title());
        rendering = true;
        questionPicker.getItems().clear();
        for (int i = 0; i < value.questions().size(); i++) questionPicker.getItems().add("Question " + (i+1) + (value.questions().get(i).answer() == null ? " — unanswered" : " — answered"));
        rendering = false;
        showQuestion();
        statusLabel.setText("All selected answers are saved.");
        timer.play(); tick();
    }
    private void showQuestion() {
        if (attempt == null || attempt.questions().isEmpty()) return;
        questionIndex = Math.min(questionIndex,attempt.questions().size()-1);
        Question question = attempt.questions().get(questionIndex);
        questionNumber.setText("QUESTION " + (questionIndex+1) + " OF " + attempt.questions().size() + " · " + question.points() + " points");
        questionText.setText(question.text());
        answersPane.getChildren().clear();
        ToggleGroup answers = new ToggleGroup();
        for (int i = 0; i < 4; i++) {
            String letter = String.valueOf((char)('A'+i));
            RadioButton button = new RadioButton(letter + ". " + question.options().get(i));
            button.setId("answer" + letter); button.setWrapText(true); button.setMaxWidth(Double.MAX_VALUE);
            button.getStyleClass().add("quiz-answer"); button.setToggleGroup(answers);
            button.setSelected(letter.equals(question.answer()));
            button.setOnAction(event -> save(letter));
            answersPane.getChildren().add(button);
        }
        long answered = attempt.questions().stream().filter(q -> q.answer() != null).count();
        progressLabel.setText(answered + " of " + attempt.questions().size() + " answered");
        answerProgress.setProgress((double)answered/attempt.questions().size());
        rendering = true; questionPicker.getSelectionModel().select(questionIndex); rendering = false;
        controls();
    }
    private void save(String answer) {
        if (busy || attempt == null || expired()) { showQuestion(); tick(); return; }
        var identity = owner; long id = attempt.id(), questionId = attempt.questions().get(questionIndex).id();
        run(() -> dao.save(identity,id,questionId,answer),this::render,"Saving answer...");
    }
    @FXML private void clearAnswer() { save(null); }
    @FXML private void previous() { if (!busy && questionIndex > 0) { questionIndex--; showQuestion(); } }
    @FXML private void next() { if (!busy && attempt != null && questionIndex+1 < attempt.questions().size()) { questionIndex++; showQuestion(); } }
    @FXML private void confirmSubmit() {
        if (busy || attempt == null || attempt.score() != null) return;
        if (!expired()) {
            long unanswered = attempt.questions().stream().filter(q -> q.answer() == null).count();
            if (!confirm("Submit this quiz?", unanswered + " unanswered questions. After submission, answers cannot be changed.")) return;
        }
        submit();
    }
    private void submit() {
        if (busy || attempt == null || attempt.score() != null) return;
        expiryFailed = false;
        var identity = owner; long id = attempt.id();
        run(() -> dao.submit(identity,id),this::render,"Submitting quiz...");
    }
    private void tick() {
        if (!sameSession()) { timer.stop(); return; }
        if (attempt == null || attempt.score() != null) return;
        long seconds = Math.max(0,(long)Math.ceil(java.time.Duration.between(LocalDateTime.now(),attempt.deadline()).toMillis()/1000.0));
        timerLabel.setText(String.format(java.util.Locale.ROOT,"Time remaining: %02d:%02d",seconds/60,seconds%60));
        controls();
        if (expired() && !busy && !expiryFailed) submit();
    }
    private boolean expired() { return attempt != null && !LocalDateTime.now().isBefore(attempt.deadline()); }
    private boolean sameSession() { return owner != null && UserSession.current() != null && sessionGeneration == UserSession.generation(); }
    private void controls() {
        quizPicker.setDisable(busy); refreshButton.setDisable(busy);
        startButton.setDisable(busy || quizPicker.getValue() == null);
        questionPane.setDisable(busy || expired()); questionPicker.setDisable(busy || expired());
        previousButton.setDisable(busy || questionIndex==0);
        nextButton.setDisable(busy || attempt == null || questionIndex+1 >= attempt.questions().size());
        submitButton.setDisable(busy);
        submitButton.setText(expiryFailed ? "Retry submission" : "Submit quiz");
    }
    @FXML private void another() { if (!busy) { reset(); open(); } }
    private void reset() {
        timer.stop(); attempt = null; questionIndex = 0; expiryFailed = false;
        quizPicker.getItems().clear();
        show(selectionPane,true); show(attemptPane,false); show(resultPane,false); controls();
    }
    private boolean confirm(String title,String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,message,ButtonType.OK,ButtonType.CANCEL);
        alert.setHeaderText(title); alert.initOwner(takeQuizScroll.getScene().getWindow());
        return alert.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }
    private static void show(Node node,boolean visible) { node.setVisible(visible); node.setManaged(visible); }
}
