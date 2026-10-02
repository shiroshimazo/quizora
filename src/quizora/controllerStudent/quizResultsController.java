package quizora.controllerStudent;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.*;
import javafx.collections.transformation.*;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import quizora.DAO.StudentResultsDAO;
import quizora.auth.UserSession;
import quizora.model.QuizChoice;
import quizora.model.ResultRecord;

public class quizResultsController {
    @FXML private TextField searchField;
    @FXML private ComboBox<QuizChoice> subjectFilter;
    @FXML private DatePicker fromDate,toDate;
    @FXML private Label totalLabel,averageLabel,bestLabel,statusLabel,countLabel,detailTitle,detailLabel;
    @FXML private Button refreshButton,clearButton;
    @FXML private TableView<ResultRecord> resultsTable;
    private final ObservableList<ResultRecord> records=FXCollections.observableArrayList();
    private final FilteredList<ResultRecord> filtered=new FilteredList<>(records);
    private final StudentResultsDAO dao;
    private boolean busy,updating;
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm");
    /** At least half the points passes: 3/5 and 5/10 pass, 2/5 and 4/10 fail. */
    private static final int PASSING_PERCENT=50;
    public quizResultsController(){this(new StudentResultsDAO());}
    public quizResultsController(StudentResultsDAO dao){this.dao=dao;}
    @FXML private void initialize(){
        column("Attempt",85,ResultRecord::attemptId);
        column("Quiz",240,ResultRecord::quiz);
        column("Subject",160,ResultRecord::subject);
        column("Score",110,r->r.score()+" / "+r.total());
        column("Percentage",110,ResultRecord::percentage);
        column("Result",100,r->r.passed(PASSING_PERCENT)?"Passed":"Failed");
        column("Submitted",190,ResultRecord::submittedAt);
        SortedList<ResultRecord> sorted=new SortedList<>(filtered);
        sorted.comparatorProperty().bind(resultsTable.comparatorProperty());resultsTable.setItems(sorted);
        searchField.textProperty().addListener((o,a,b)->filter());subjectFilter.valueProperty().addListener((o,a,b)->filter());
        fromDate.valueProperty().addListener((o,a,b)->filter());toDate.valueProperty().addListener((o,a,b)->filter());
        resultsTable.getSelectionModel().selectedItemProperty().addListener((o,a,b)->details(b));
        filter();
    }
    private <T> void column(String title,int width,Function<ResultRecord,T> value){
        TableColumn<ResultRecord,T> column=new TableColumn<>(title);column.setPrefWidth(width);
        column.setCellValueFactory(row->new ReadOnlyObjectWrapper<>(value.apply(row.getValue())));
        column.setCellFactory(c->new TableCell<>(){
            @Override protected void updateItem(T item,boolean empty){
                super.updateItem(item,empty);
                String text=empty||item==null?null:item instanceof Double number?percent(number):item instanceof java.time.LocalDateTime date?DATE.format(date):item.toString();
                setText(text);setTooltip(text==null?null:new Tooltip(text));
            }
        });resultsTable.getColumns().add(column);
    }
    @FXML public void refresh(){
        if(busy)return;busy=true;controls();records.clear();details(null);filter();statusLabel.setText("Loading your results...");
        var identity=UserSession.current();long session=UserSession.generation();
        Task<List<ResultRecord>> task=new Task<>(){@Override protected List<ResultRecord> call()throws Exception{return dao.load(identity);}};
        task.setOnSucceeded(event->{busy=false;controls();if(UserSession.generation()!=session){records.clear();filter();statusLabel.setText("Session changed. Sign in again.");return;}render(task.getValue());});
        task.setOnFailed(event->{busy=false;controls();records.clear();filter();statusLabel.setText(task.getException() instanceof SecurityException?"Active student access is required. Sign in again.":"Unable to load results. Check your connection and refresh.");resultsTable.setPlaceholder(new Label("Results unavailable"));});
        Thread worker=new Thread(task,"quizora-student-results");worker.setDaemon(true);worker.start();
    }
    void render(List<ResultRecord> values){
        updating=true;records.setAll(values);QuizChoice selected=subjectFilter.getValue();
        TreeMap<Long,QuizChoice> subjects=new TreeMap<>();values.forEach(r->subjects.put(r.subjectId(),new QuizChoice(r.subjectId(),r.subject())));
        subjectFilter.getItems().setAll(subjects.values().stream().sorted(Comparator.comparing(QuizChoice::name)).toList());
        subjectFilter.setValue(selected==null?null:subjects.get(selected.id()));updating=false;filter();
    }
    @FXML private void clearFilters(){updating=true;searchField.clear();subjectFilter.setValue(null);fromDate.setValue(null);toDate.setValue(null);updating=false;filter();}
    private void filter(){
        if(updating)return;
        LocalDate from=fromDate.getValue(),to=toDate.getValue();boolean invalid=from!=null&&to!=null&&from.isAfter(to);
        String search=searchField.getText().strip().toLowerCase(Locale.ROOT);QuizChoice subject=subjectFilter.getValue();
        filtered.setPredicate(r->!invalid&&(subject==null||r.subjectId()==subject.id())
                &&(from==null||!r.submittedAt().toLocalDate().isBefore(from))&&(to==null||!r.submittedAt().toLocalDate().isAfter(to))
                &&(r.attemptId()+" "+r.quiz()+" "+r.subject()).toLowerCase(Locale.ROOT).contains(search));
        int count=filtered.size();totalLabel.setText(Integer.toString(count));
        averageLabel.setText(count==0?"--":percent(filtered.stream().mapToDouble(ResultRecord::percentage).average().orElseThrow()));
        bestLabel.setText(count==0?"--":percent(filtered.stream().mapToDouble(ResultRecord::percentage).max().orElseThrow()));
        countLabel.setText(count+" of "+records.size()+" results");
        statusLabel.setText(invalid?"From date must be on or before To date.":"Summary reflects the filtered results. Select a row for submission details.");
        resultsTable.setPlaceholder(new Label(invalid?"Choose a valid date range.":records.isEmpty()?"No submitted quizzes yet. Complete a quiz to see your results.":"No results match your filters."));
        details(resultsTable.getSelectionModel().getSelectedItem());
    }
    private void details(ResultRecord result){
        detailTitle.setText(result==null?"Select a result":result.quiz());
        detailLabel.setText(result==null?"Submission details appear here.":"Attempt #"+result.attemptId()+" | "+result.subject()
                +"\nScore: "+result.score()+" / "+result.total()+" ("+percent(result.percentage())+")\nSubmitted: "+DATE.format(result.submittedAt()));
    }
    private static String percent(double value){return String.format(Locale.ROOT,"%.1f%%",value);}
    private void controls(){for(var node:List.of(searchField,subjectFilter,fromDate,toDate,refreshButton,clearButton))node.setDisable(busy);}
}
