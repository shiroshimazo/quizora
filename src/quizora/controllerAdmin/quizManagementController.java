package quizora.controllerAdmin;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import javafx.fxml.FXML;
import javafx.collections.*;
import javafx.collections.transformation.*;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import quizora.auth.UserSession;
import quizora.DAO.QuizManagementDAO;
import quizora.model.*;

public class quizManagementController {
    @FXML private ScrollPane quizScroll;
    @FXML private GridPane quizKpiGrid;
    @FXML private Label totalQuizValue,publishedQuizValue,draftQuizValue,quizMessage,quizResultCount;
    @FXML private TextField quizSearch;
    @FXML private ComboBox<String> quizStatusFilter;
    @FXML private Button addQuizButton,refreshQuizzesButton;
    @FXML private TableView<QuizRecord> quizTable;
    private final QuizManagementDAO dao=new QuizManagementDAO();
    private final ObservableList<QuizRecord> records=FXCollections.observableArrayList();
    private final FilteredList<QuizRecord> filtered=new FilteredList<>(records);
    private QuizManagementDAO.Data data;
    private boolean busy;
    @FXML private void initialize(){
        quizStatusFilter.getItems().setAll("All statuses","Draft","Published","Closed","Archived");quizStatusFilter.setValue("All statuses");
        quizSearch.textProperty().addListener((o,a,b)->filter());quizStatusFilter.valueProperty().addListener((o,a,b)->filter());
        column("ID",70,q->q.id());column("Title",210,QuizRecord::title);column("Subject",150,QuizRecord::subject);column("Teacher",150,QuizRecord::teacher);column("Minutes",85,QuizRecord::minutes);column("Questions",95,QuizRecord::questions);column("Points",75,QuizRecord::points);column("Status",100,QuizRecord::status);
        TableColumn<QuizRecord,QuizRecord> actions=new TableColumn<>("Actions");actions.setMinWidth(235);actions.setSortable(false);actions.setCellValueFactory(c->new ReadOnlyObjectWrapper<>(c.getValue()));
        actions.setCellFactory(c->new TableCell<>(){protected void updateItem(QuizRecord q,boolean empty){super.updateItem(q,empty);if(empty||q==null){setGraphic(null);return;}
            Button view=button("View",()->open(q,true)),edit=button("Edit",()->open(q,false)),archive=button("Archive",()->archive(q));edit.setDisable(q.archived());archive.setDisable(q.archived());setGraphic(new HBox(6,view,edit,archive));}});quizTable.getColumns().add(actions);
        SortedList<QuizRecord> sorted=new SortedList<>(filtered);sorted.comparatorProperty().bind(quizTable.comparatorProperty());quizTable.setItems(sorted);
        quizScroll.viewportBoundsProperty().addListener((o,a,b)->reflow(b.getWidth()));reflow(900);filter();controls();
    }
    private Button button(String title,Runnable action){Button b=new Button(title);b.getStyleClass().add("row-action");b.setOnAction(e->action.run());return b;}
    private <T> void column(String title,int width,java.util.function.Function<QuizRecord,T> value){TableColumn<QuizRecord,T> c=new TableColumn<>(title);c.setMinWidth(width);c.setCellValueFactory(q->new ReadOnlyObjectWrapper<>(value.apply(q.getValue())));quizTable.getColumns().add(c);}
    private void reflow(double width){int count=width<650?1:3;quizKpiGrid.getColumnConstraints().clear();for(int i=0;i<count;i++){var c=new ColumnConstraints();c.setPercentWidth(100.0/count);c.setMinWidth(0);quizKpiGrid.getColumnConstraints().add(c);}for(int i=0;i<3;i++){GridPane.setColumnIndex(quizKpiGrid.getChildren().get(i),i%count);GridPane.setRowIndex(quizKpiGrid.getChildren().get(i),i/count);}}
    private void filter(){filtered.setPredicate(q->q.matches(quizSearch.getText(),quizStatusFilter.getValue()));quizResultCount.setText(filtered.size()+" of "+records.size()+" quizzes shown");quizTable.setPlaceholder(new Label(data==null?"Refresh to load quizzes.":records.isEmpty()?"No quizzes yet. Add your first quiz.":"No quizzes match your filters."));}
    private void totals(){totalQuizValue.setText(""+records.size());publishedQuizValue.setText(""+records.stream().filter(q->!q.archived()&&q.state().equals("published")).count());draftQuizValue.setText(""+records.stream().filter(q->!q.archived()&&q.state().equals("draft")).count());filter();}
    @FXML public void refresh(){if(busy)return;var admin=UserSession.current();quizMessage.setText("Loading quizzes...");run(()->dao.load(admin),loaded->{data=loaded;controls();records.setAll(data.quizzes());totals();quizMessage.setText("Quiz records are up to date.");},e->{data=null;controls();records.clear();totals();quizMessage.setText(error(e));});}
    @FXML private void addQuiz(){if(!busy&&data!=null)editor(null,false);}
    private void open(QuizRecord q,boolean readOnly){if(busy)return;var admin=UserSession.current();run(()->dao.details(admin,q.id()),d->editor(d,readOnly),e->quizMessage.setText(error(e)));}
    private void editor(QuizDetails original,boolean readOnly){
        var form=new QuizEditor(data,original,readOnly);Dialog<ButtonType> dialog=new Dialog<>();dialog.initOwner(quizTable.getScene().getWindow());dialog.setTitle(readOnly?"View quiz":original==null?"Add quiz":"Edit quiz");dialog.setResizable(true);
        Label message=new Label();message.setWrapText(true);message.getStyleClass().add("management-error");message.setId("quizFormMessage");dialog.getDialogPane().setContent(new VBox(8,form,message));dialog.getDialogPane().getStylesheets().add(getClass().getResource("/Resources/css/dashboard.css").toExternalForm());
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
        dialog.setOnCloseRequest(e->{if(busy)e.consume();});
        if(!readOnly){ButtonType type=new ButtonType(original==null?"Add quiz":"Save changes",ButtonBar.ButtonData.OK_DONE);dialog.getDialogPane().getButtonTypes().add(0,type);Button save=(Button)dialog.getDialogPane().lookupButton(type);save.setId("saveQuizButton");save.addEventFilter(ActionEvent.ACTION,e->{e.consume();if(busy)return;try{
            var changes=form.changes();var questions=form.questions();var admin=UserSession.current();form.setDisable(true);save.setDisable(true);dialog.getDialogPane().lookupButton(ButtonType.CANCEL).setDisable(true);message.setText("Saving quiz...");
            run(()->dao.save(admin,original,changes,questions),q->{update(q);quizSearch.clear();quizStatusFilter.setValue("All statuses");quizTable.getSelectionModel().select(q);quizTable.scrollTo(q);quizMessage.setText("Quiz saved successfully.");dialog.close();},failure->{form.setDisable(false);save.setDisable(false);dialog.getDialogPane().lookupButton(ButtonType.CANCEL).setDisable(false);message.setText(error(failure));});
        }catch(IllegalArgumentException invalid){message.setText(invalid.getMessage());}});}
        dialog.showAndWait();
    }
    private void update(QuizRecord q){records.removeIf(old->old.id()==q.id());records.add(0,q);totals();}
    private void archive(QuizRecord q){if(busy)return;Alert a=new Alert(Alert.AlertType.CONFIRMATION,"The quiz will close. Questions, answers and results will be kept.",ButtonType.OK,ButtonType.CANCEL);a.initOwner(quizTable.getScene().getWindow());a.setTitle("Archive quiz");a.setHeaderText("Archive "+q.title()+"?");if(a.showAndWait().filter(ButtonType.OK::equals).isEmpty())return;var admin=UserSession.current();run(()->dao.archive(admin,q),updated->{update(updated);quizMessage.setText("Quiz archived. Assessment history preserved.");},e->quizMessage.setText(error(e)));}
    private <T> void run(Callable<T> work,Consumer<T> success,Consumer<Throwable> failure){if(busy)return;busy=true;controls();var identity=UserSession.current();Task<T> task=new Task<>(){protected T call()throws Exception{return work.call();}};task.setOnSucceeded(e->{busy=false;controls();if(UserSession.current()==identity)success.accept(task.getValue());else failure.accept(new SecurityException());});task.setOnFailed(e->{busy=false;controls();failure.accept(task.getException());});Thread t=new Thread(task,"quizora-quiz-management");t.setDaemon(true);t.start();}
    private void controls(){quizTable.setDisable(busy);quizSearch.setDisable(busy);quizStatusFilter.setDisable(busy);refreshQuizzesButton.setDisable(busy);addQuizButton.setDisable(busy||data==null);}
    private String error(Throwable e){if(e instanceof SecurityException)return "Administrator access is required. Sign in again.";if(e instanceof IllegalArgumentException)return e.getMessage();if(e instanceof java.sql.SQLException sql&&"40001".equals(sql.getSQLState()))return "Quiz changed. Close the form, refresh, and try again.";return "Unable to complete the request. Check the database connection and refresh.";}
}
