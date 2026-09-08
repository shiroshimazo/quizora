package quizora.controllerAdmin;

import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import quizora.DAO.StudentManagementDAO;
import quizora.auth.UserSession;
import quizora.model.StudentChanges;
import quizora.model.StudentRecord;

public class studentManagementController implements Initializable {
    @FXML private ScrollPane studentScroll;
    @FXML private GridPane studentKpiGrid;
    @FXML private Label totalStudentValue, activeStudentValue, inactiveStudentValue;
    @FXML private Label studentMessage, studentResultCount;
    @FXML private TextField studentSearch;
    @FXML private ComboBox<String> studentStatusFilter;
    @FXML private Button refreshStudentsButton, addStudentButton;
    @FXML private TableView<StudentRecord> studentTable;
    @FXML private TableColumn<StudentRecord, Long> idColumn;
    @FXML private TableColumn<StudentRecord, String> nameColumn, usernameColumn, emailColumn, statusColumn;
    @FXML private TableColumn<StudentRecord, StudentRecord> actionsColumn;
    private final ObservableList<StudentRecord> records = FXCollections.observableArrayList();
    private final FilteredList<StudentRecord> filtered = new FilteredList<>(records);
    private final StudentManagementDAO dao = new StudentManagementDAO();
    private boolean busy;
    private int columns;
    private boolean loaded;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        studentStatusFilter.getItems().setAll("All statuses", "Active", "Inactive", "Archived");
        studentStatusFilter.setValue("All statuses");
        studentSearch.textProperty().addListener((o, old, value) -> filter());
        studentStatusFilter.valueProperty().addListener((o, old, value) -> filter());
        idColumn.setCellValueFactory(row -> new ReadOnlyObjectWrapper<>(row.getValue().id()));
        nameColumn.setCellValueFactory(row -> new ReadOnlyStringWrapper(row.getValue().name()));
        usernameColumn.setCellValueFactory(row -> new ReadOnlyStringWrapper(row.getValue().username()));
        emailColumn.setCellValueFactory(row -> new ReadOnlyStringWrapper(row.getValue().email()));
        statusColumn.setCellValueFactory(row -> new ReadOnlyStringWrapper(row.getValue().status()));
        for (var column : List.of(nameColumn, usernameColumn, emailColumn)) {
            column.setCellFactory(c -> new TableCell<>() {
                @Override protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty ? null : item);
                    setTooltip(empty || item == null ? null : new Tooltip(item));
                }
            });
        }
        statusColumn.setCellFactory(column -> new TableCell<>() {
            private final Label badge = new Label();
            @Override protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) { setGraphic(null); return; }
                badge.setText(status);
                badge.getStyleClass().setAll("status-badge", "status-" + status.toLowerCase(java.util.Locale.ROOT));
                setGraphic(badge);
            }
        });
        actionsColumn.setCellValueFactory(row -> new ReadOnlyObjectWrapper<>(row.getValue()));
        actionsColumn.setCellFactory(column -> new StudentActionCell());
        SortedList<StudentRecord> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(studentTable.comparatorProperty());
        studentTable.setItems(sorted);
        studentTable.setPlaceholder(new Label("Select Student Management to load records."));
        studentTable.widthProperty().addListener((o, old, width) -> {
            // Unconstrained mode keeps horizontal scrolling when columns cannot fit.
            studentTable.setColumnResizePolicy(width.doubleValue() < 850
                    ? TableView.UNCONSTRAINED_RESIZE_POLICY : TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        });
        studentScroll.viewportBoundsProperty().addListener((o, old, bounds) -> reflow(bounds.getWidth()));
        reflow(900);
    }

    private void reflow(double width) {
        if (width <= 0) return;
        int next = width < 650 ? 1 : 3;
        if (next == columns) return;
        columns = next;
        studentKpiGrid.getColumnConstraints().clear();
        for (int i=0;i<next;i++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setMinWidth(0);
            column.setPercentWidth(100.0/next);
            studentKpiGrid.getColumnConstraints().add(column);
        }
        for (int i=0;i<studentKpiGrid.getChildren().size();i++) {
            GridPane.setColumnIndex(studentKpiGrid.getChildren().get(i), i%next);
            GridPane.setRowIndex(studentKpiGrid.getChildren().get(i), i/next);
        }
    }

    @FXML
    public void refresh() {
        if (busy) return;
        var identity = UserSession.current();
        if (identity == null || !"admin".equals(identity.role())) {
            unavailable("Sign in as an administrator to manage students.");
            return;
        }
        studentMessage.setText("Loading students...");
        execute(() -> dao.load(identity), data -> {
            display(data);
            studentMessage.setText("Student records are up to date.");
        }, error -> unavailable(errorMessage(error)));
    }

    void display(List<StudentRecord> students) {
        loaded = true;
        records.setAll(students);
        updateTotals();
        filter();
    }

    private void updateTotals() {
        NumberFormat format = NumberFormat.getIntegerInstance();
        totalStudentValue.setText(format.format(records.size()));
        activeStudentValue.setText(format.format(records.stream().filter(s -> !s.archived() && s.active()).count()));
        inactiveStudentValue.setText(format.format(records.stream().filter(s -> !s.archived() && !s.active()).count()));
    }

    private void filter() {
        filtered.setPredicate(student -> student.matches(studentSearch.getText(), studentStatusFilter.getValue()));
        studentResultCount.setText(filtered.size() + " of " + records.size() + " students shown");
        studentTable.setPlaceholder(new Label(!loaded ? "Student data is unavailable."
                : records.isEmpty() ? "No student records yet." : "No students match your search and status filter."));
    }

    private void unavailable(String message) {
        loaded = false;
        records.clear();
        filter();
        for (Label value : List.of(totalStudentValue, activeStudentValue, inactiveStudentValue)) value.setText("—");
        studentMessage.setText(message);
    }

    private void replace(StudentRecord updated, String message) {
        for (int i=0;i<records.size();i++) {
            if (records.get(i).id() == updated.id()) { records.set(i, updated); break; }
        }
        updateTotals();
        filter();
        studentMessage.setText(message);
    }

    private <T> void execute(Callable<T> work, Consumer<T> success, Consumer<Throwable> failure) {
        if (busy) return;
        var identity = UserSession.current();
        setBusy(true);
        Task<T> task = new Task<>() {
            @Override protected T call() throws Exception { return work.call(); }
        };
        task.setOnSucceeded(event -> {
            setBusy(false);
            if (UserSession.current() == identity) success.accept(task.getValue());
            else unavailable("Your session changed. Sign in again.");
        });
        task.setOnFailed(event -> {
            setBusy(false);
            if (UserSession.current() == identity) failure.accept(task.getException());
            else unavailable("Your session changed. Sign in again.");
        });
        Thread worker = new Thread(task, "quizora-student-management");
        worker.setDaemon(true);
        worker.start();
    }

    private void setBusy(boolean value) {
        busy = value;
        studentTable.setDisable(value);
        studentSearch.setDisable(value);
        studentStatusFilter.setDisable(value);
        refreshStudentsButton.setDisable(value);
        addStudentButton.setDisable(value);
    }

    @FXML private void addStudent() {
        if (busy) return;
        var identity = UserSession.current();
        if (identity == null || !"admin".equals(identity.role())) {
            studentMessage.setText("Sign in as an administrator to manage students.");
            return;
        }
        openStudentForm(null);
    }

    private void editStudent(StudentRecord student) {
        if (busy || student == null || student.archived()) return;
        openStudentForm(student);
    }

    private void openStudentForm(StudentRecord student) {
        boolean creating = student == null;
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/Resources/fxml/admin/studentEdit.fxml"));
            Parent form = loader.load();
            StudentEditController editor = loader.getController();
            if (creating) editor.prepareCreate();
            else editor.populate(student);
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle(creating ? "Add student" : "Edit student · ID " + student.id());
            dialog.initOwner(studentTable.getScene().getWindow());
            dialog.getDialogPane().setContent(form);
            dialog.getDialogPane().getStyleClass().add("student-edit-dialog");
            dialog.getDialogPane().getStylesheets().add(getClass().getResource("/Resources/css/dashboard.css").toExternalForm());
            ButtonType saveType = new ButtonType(creating ? "Add student" : "Save changes", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().setAll(saveType, ButtonType.CANCEL);
            Button save = (Button) dialog.getDialogPane().lookupButton(saveType);
            Button cancel = (Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
            save.setId("saveStudentButton"); save.setAccessibleText(creating ? "Save new student" : "Save student changes");
            cancel.setId("cancelStudentEditButton"); cancel.setAccessibleText("Cancel student editing");
            dialog.setOnCloseRequest(event -> { if (busy) event.consume(); });
            save.addEventFilter(ActionEvent.ACTION, event -> {
                event.consume();
                if (busy) return;
                StudentChanges changes;
                String password;
                try {
                    changes = editor.changes();
                    password = creating ? editor.password() : null;
                }
                catch (IllegalArgumentException invalid) { editor.message(invalid.getMessage()); return; }
                var identity = UserSession.current();
                form.setDisable(true); save.setDisable(true); cancel.setDisable(true);
                editor.message("Saving changes...");
                execute(() -> creating ? dao.create(identity, changes, password) : dao.edit(identity, student, changes), updated -> {
                    if (creating) {
                        loaded = true;
                        records.add(0, updated);
                        studentSearch.clear();
                        studentStatusFilter.setValue("All statuses");
                        updateTotals();
                        filter();
                        studentTable.getSelectionModel().select(updated);
                        studentTable.scrollTo(updated);
                        studentMessage.setText("Student added successfully.");
                    } else replace(updated, "Student details saved.");
                    dialog.close();
                }, error -> {
                    form.setDisable(false); save.setDisable(false); cancel.setDisable(false);
                    editor.message(errorMessage(error));
                });
            });
            dialog.showAndWait();
            editor.clearPassword();
        } catch (IOException error) {
            studentMessage.setText("Unable to open the student form.");
        }
    }

    private void archiveStudent(StudentRecord student) {
        if (busy || student == null || student.archived()) return;
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.initOwner(studentTable.getScene().getWindow());
        confirmation.setTitle("Archive student");
        confirmation.setHeaderText("Archive " + student.name() + "?");
        confirmation.setContentText("This disables student login. The account and quiz history will be kept.");
        ButtonType archive = new ButtonType("Archive student", ButtonBar.ButtonData.OK_DONE);
        confirmation.getButtonTypes().setAll(archive, ButtonType.CANCEL);
        if (confirmation.showAndWait().filter(archive::equals).isEmpty()) return;
        var identity = UserSession.current();
        studentMessage.setText("Archiving student...");
        execute(() -> dao.archive(identity, student),
                updated -> replace(updated, "Student archived. Quiz history has been preserved."),
                error -> studentMessage.setText(errorMessage(error)));
    }

    private static String errorMessage(Throwable error) {
        if (error instanceof SecurityException) return "Administrator access is required. Sign in again.";
        if (error instanceof IllegalArgumentException) return error.getMessage();
        if (error instanceof SQLException sql) {
            if (sql.getErrorCode() == 1062) return "That username or email is already in use.";
            if ("40001".equals(sql.getSQLState())) return "This record changed. Close the form, refresh, and try again.";
        }
        return "Unable to complete the request. Check the database connection and try again.";
    }

    public final class StudentActionCell extends TableCell<StudentRecord, StudentRecord> {
        @FXML private Button editStudentButton, archiveStudentButton;
        private final Parent actions;

        public StudentActionCell() {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/Resources/fxml/admin/studentActions.fxml"));
                loader.setController(this);
                actions = loader.load();
            } catch (IOException error) { throw new IllegalStateException("Cannot load student actions", error); }
        }

        @Override protected void updateItem(StudentRecord student, boolean empty) {
            super.updateItem(student, empty);
            if (empty || student == null) { setGraphic(null); return; }
            editStudentButton.setDisable(student.archived());
            archiveStudentButton.setDisable(student.archived());
            archiveStudentButton.setText(student.archived() ? "Archived" : "Archive");
            editStudentButton.setAccessibleText("Edit " + student.name());
            archiveStudentButton.setAccessibleText("Archive " + student.name());
            setGraphic(actions);
        }
        @FXML private void edit() { editStudent(getItem()); }
        @FXML private void archive() { archiveStudent(getItem()); }
    }
}
