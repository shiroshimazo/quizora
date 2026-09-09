package quizora.controllerTeacher;

import java.net.URL;
import java.text.NumberFormat;
import java.util.*;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.concurrent.Task;
import javafx.fxml.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import quizora.DAO.AssignedSubjectsDAO;
import quizora.auth.UserSession;
import quizora.model.AssignedSubject;

public class assignedSubjectsController implements Initializable {
    @FXML private ScrollPane assignedScroll;
    @FXML private GridPane kpiGrid;
    @FXML private Label subjectsValue,quizzesValue,publishedValue,statusLabel,resultCount,detailTitle,detailDescription;
    @FXML private TextField subjectSearch;
    @FXML private Button refreshButton;
    @FXML private TableView<AssignedSubject> subjectTable;
    private List<AssignedSubject> records=List.of();
    private boolean busy;
    @Override public void initialize(URL location,ResourceBundle resources) {
        column("Subject",AssignedSubject::name,220);
        column("Category",s->s.category().isBlank()?"Uncategorized":s.category(),160);
        column("My quizzes",AssignedSubject::quizzes,100);
        column("Published",AssignedSubject::published,100);
        subjectTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        subjectTable.setPlaceholder(new Label("No assignments loaded"));
        subjectSearch.textProperty().addListener((o,old,value)->filter());
        subjectTable.getSelectionModel().selectedItemProperty().addListener((o,old,value)->details(value));
        assignedScroll.viewportBoundsProperty().addListener((o,old,value)->layout(value.getWidth()));
        layout(900);
    }
    private <T> void column(String title,java.util.function.Function<AssignedSubject,T> value,double width) {
        var column=new TableColumn<AssignedSubject,T>(title);column.setPrefWidth(width);column.setMinWidth(60);
        column.setCellValueFactory(cell->new ReadOnlyObjectWrapper<>(value.apply(cell.getValue())));
        subjectTable.getColumns().add(column);
    }
    private void layout(double width) {
        if(width<=0)return;int count=width<600?1:width<1000?2:3;
        kpiGrid.getColumnConstraints().clear();
        for(int i=0;i<count;i++){var c=new ColumnConstraints();c.setMinWidth(0);c.setPercentWidth(100.0/count);kpiGrid.getColumnConstraints().add(c);}
        for(int i=0;i<kpiGrid.getChildren().size();i++){var node=kpiGrid.getChildren().get(i);GridPane.setColumnIndex(node,i%count);GridPane.setRowIndex(node,i/count);}
    }
    @FXML public void refresh() {
        if(busy)return;
        var identity=UserSession.current();
        if(identity==null||!"teacher".equals(identity.role())){clear("Sign in as a teacher to view assignments.");return;}
        busy=true;refreshButton.setDisable(true);statusLabel.setText("Loading assigned subjects...");
        var task=new Task<List<AssignedSubject>>() {
            @Override protected List<AssignedSubject> call()throws Exception{return new AssignedSubjectsDAO().load(identity);}
        };
        task.setOnSucceeded(e->{
            busy=false;refreshButton.setDisable(false);
            if(UserSession.current()!=identity){clear("Session changed. Sign in again.");return;}
            render(task.getValue());
            statusLabel.setText(records.isEmpty()?"No subjects are assigned to you. Ask your administrator to assign a subject, then refresh.":"Assignments updated. Select a row for subject details.");
        });
        task.setOnFailed(e->{busy=false;refreshButton.setDisable(false);clear(UserSession.current()!=identity?"Session changed. Sign in again.":"Assignments unavailable. Check your connection and teacher access, then refresh.");});
        var worker=new Thread(task,"quizora-assigned-subjects");worker.setDaemon(true);worker.start();
    }
    void render(List<AssignedSubject> data) {
        records=List.copyOf(data);var numbers=NumberFormat.getIntegerInstance();
        subjectsValue.setText(numbers.format(records.size()));
        quizzesValue.setText(numbers.format(records.stream().mapToLong(AssignedSubject::quizzes).sum()));
        publishedValue.setText(numbers.format(records.stream().mapToLong(AssignedSubject::published).sum()));filter();
    }
    private void filter() {
        subjectTable.getItems().setAll(records.stream().filter(s->s.matches(subjectSearch.getText())).toList());subjectTable.sort();
        subjectTable.setPlaceholder(new Label(records.isEmpty()?"No assigned subjects":"No subjects match your search"));
        resultCount.setText(subjectTable.getItems().size()+" of "+records.size()+" subjects shown");details(subjectTable.getSelectionModel().getSelectedItem());
    }
    private void details(AssignedSubject value) {
        detailTitle.setText(value==null?"Subject details":value.name());
        detailDescription.setText(value==null?"Select a subject to read its description.":value.description().isBlank()?"No description has been added for this subject.":value.description());
    }
    private void clear(String message) {
        records=List.of();filter();for(var label:List.of(subjectsValue,quizzesValue,publishedValue))label.setText("?");
        subjectTable.setPlaceholder(new Label("Assignments unavailable"));statusLabel.setText(message);
    }
}
