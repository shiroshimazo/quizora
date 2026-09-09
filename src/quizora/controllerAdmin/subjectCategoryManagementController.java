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
import quizora.DAO.SubjectManagementDAO;
import quizora.model.*;
public class subjectCategoryManagementController {
 @FXML private ScrollPane subjectScroll;
 @FXML private GridPane subjectKpiGrid;
 @FXML private Label totalSubjectValue,activeSubjectValue,categoryValue,subjectMessage,subjectResultCount;
 @FXML private TextField subjectSearch;
 @FXML private ComboBox<String> subjectStatusFilter,subjectCategoryFilter;
 @FXML private Button addSubjectButton,refreshSubjectsButton;
 @FXML private TableView<SubjectRecord> subjectTable;
 private final SubjectManagementDAO dao=new SubjectManagementDAO();
 private final ObservableList<SubjectRecord> records=FXCollections.observableArrayList();
 private final FilteredList<SubjectRecord> filtered=new FilteredList<>(records);
 private boolean busy,loaded;
 @FXML private void initialize(){
  subjectStatusFilter.getItems().setAll("All statuses","Active","Archived");subjectStatusFilter.setValue("All statuses");
  subjectCategoryFilter.setPromptText("All categories");subjectCategoryFilter.setButtonCell(new ListCell<>(){protected void updateItem(String value,boolean empty){super.updateItem(value,empty);setText(value==null?"All categories":value.isEmpty()?"Uncategorized":value);}});
  subjectCategoryFilter.setCellFactory(c->new ListCell<>(){protected void updateItem(String value,boolean empty){super.updateItem(value,empty);setText(empty?null:value==null?"All categories":value.isEmpty()?"Uncategorized":value);}});
  subjectSearch.textProperty().addListener((o,a,b)->filter());subjectStatusFilter.valueProperty().addListener((o,a,b)->filter());subjectCategoryFilter.valueProperty().addListener((o,a,b)->filter());
  column("ID",65,SubjectRecord::id);column("Subject",180,SubjectRecord::name);column("Category",140,q->q.category().isEmpty()?"Uncategorized":q.category());column("Description",220,SubjectRecord::description);column("Created",110,q->q.createdAt().toLocalDate());column("Status",95,SubjectRecord::status);column("Quizzes",80,SubjectRecord::quizzes);
  var actions=new TableColumn<SubjectRecord,SubjectRecord>("Actions");actions.setMinWidth(230);actions.setSortable(false);actions.setCellValueFactory(c->new ReadOnlyObjectWrapper<>(c.getValue()));
  actions.setCellFactory(c->new TableCell<>(){protected void updateItem(SubjectRecord q,boolean empty){super.updateItem(q,empty);if(empty||q==null){setGraphic(null);return;}Button view=button("View",()->editor(q,true)),edit=button("Edit",()->editor(q,false)),archive=button("Archive",()->archive(q));view.setId("viewSubjectButton");edit.setId("editSubjectButton");archive.setId("archiveSubjectButton");edit.setDisable(q.archived());archive.setDisable(q.archived());setGraphic(new HBox(6,view,edit,archive));}});subjectTable.getColumns().add(actions);
  SortedList<SubjectRecord> sorted=new SortedList<>(filtered);sorted.comparatorProperty().bind(subjectTable.comparatorProperty());subjectTable.setItems(sorted);
  subjectScroll.viewportBoundsProperty().addListener((o,a,b)->reflow(b.getWidth()));reflow(900);filter();
 }
 private Button button(String text,Runnable action){Button b=new Button(text);b.getStyleClass().add("row-action");b.setOnAction(e->action.run());return b;}
 private <T> void column(String title,int width,java.util.function.Function<SubjectRecord,T> value){var c=new TableColumn<SubjectRecord,T>(title);c.setMinWidth(width);c.setCellValueFactory(q->new ReadOnlyObjectWrapper<>(value.apply(q.getValue())));c.setCellFactory(col->new TableCell<>(){protected void updateItem(T item,boolean empty){super.updateItem(item,empty);setText(empty||item==null?null:item.toString());setTooltip(empty||item==null?null:new Tooltip(item.toString()));}});subjectTable.getColumns().add(c);}
 private void reflow(double width){int count=width<650?1:3;subjectKpiGrid.getColumnConstraints().clear();for(int i=0;i<count;i++){var c=new ColumnConstraints();c.setPercentWidth(100.0/count);c.setMinWidth(0);subjectKpiGrid.getColumnConstraints().add(c);}for(int i=0;i<3;i++){GridPane.setColumnIndex(subjectKpiGrid.getChildren().get(i),i%count);GridPane.setRowIndex(subjectKpiGrid.getChildren().get(i),i/count);}}
 private void filter(){filtered.setPredicate(q->q.matches(subjectSearch.getText(),subjectStatusFilter.getValue(),subjectCategoryFilter.getValue()));subjectResultCount.setText(filtered.size()+" of "+records.size()+" subjects shown");subjectTable.setPlaceholder(new Label(!loaded?"Refresh to load subjects.":records.isEmpty()?"No subjects yet. Add your first subject.":"No subjects match your filters."));}
 private void totals(){totalSubjectValue.setText(""+records.size());activeSubjectValue.setText(""+records.stream().filter(q->!q.archived()).count());categoryValue.setText(""+records.stream().map(SubjectRecord::category).filter(c->!c.isEmpty()).map(c->c.toLowerCase(Locale.ROOT)).distinct().count());String selected=subjectCategoryFilter.getValue();var categories=new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);records.forEach(q->categories.add(q.category()));subjectCategoryFilter.getItems().clear();subjectCategoryFilter.getItems().add(null);subjectCategoryFilter.getItems().addAll(categories);subjectCategoryFilter.setValue(categories.contains(selected==null?"":selected)&&selected!=null?selected:null);filter();}
 @FXML public void refresh(){if(busy)return;var admin=UserSession.current();subjectMessage.setText("Loading subjects...");run(()->dao.load(admin),list->{loaded=true;records.setAll(list);totals();subjectMessage.setText("Subject records are up to date.");},e->{loaded=false;records.clear();totals();totalSubjectValue.setText("?");activeSubjectValue.setText("?");categoryValue.setText("?");subjectMessage.setText(error(e));});}
 @FXML private void addSubject(){if(!busy)editor(null,false);}
 private void field(VBox box,String text,Control input){Label label=new Label(text);label.setLabelFor(input);input.setAccessibleText(text);input.getStyleClass().add("management-input");box.getChildren().addAll(label,input);}
 private void editor(SubjectRecord original,boolean readOnly){
  if(busy)return;Dialog<ButtonType> dialog=new Dialog<>();dialog.initOwner(subjectTable.getScene().getWindow());dialog.setTitle(readOnly?"View subject":original==null?"Add subject":"Edit subject");
  VBox form=new VBox(10);form.getStyleClass().add("student-editor");form.setPrefWidth(440);
  TextField name=new TextField(original==null?"":original.name());name.setId("subjectNameField");
  ComboBox<String> category=new ComboBox<>();category.setId("subjectCategoryField");category.setEditable(true);category.setMaxWidth(Double.MAX_VALUE);category.getItems().setAll(records.stream().map(SubjectRecord::category).filter(c->!c.isEmpty()).distinct().sorted().toList());category.getEditor().setText(original==null?"":original.category());category.setPromptText("Choose or enter a category");
  TextArea description=new TextArea(original==null?"":original.description());description.setId("subjectDescriptionField");description.setWrapText(true);description.setPrefRowCount(4);
  field(form,"Subject name *",name);field(form,"Category (optional)",category);field(form,"Description (optional)",description);
  if(original!=null){Label info=new Label("Created: "+original.createdAt().toLocalDate()+" ? "+original.status()+"\nLinked quizzes: "+original.quizzes()+" ? Assigned teachers: "+original.teachers());info.setWrapText(true);form.getChildren().add(info);}
  Label message=new Label();message.setId("subjectFormMessage");message.setWrapText(true);message.getStyleClass().add("management-error");form.getChildren().add(message);
  dialog.getDialogPane().setContent(form);dialog.getDialogPane().getStylesheets().add(getClass().getResource("/Resources/css/dashboard.css").toExternalForm());dialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);dialog.getDialogPane().lookupButton(ButtonType.CANCEL).setId("cancelSubjectButton");dialog.setOnCloseRequest(e->{if(busy)e.consume();});
  if(readOnly){name.setEditable(false);category.setDisable(true);description.setEditable(false);}else{
   ButtonType type=new ButtonType(original==null?"Add subject":"Save changes",ButtonBar.ButtonData.OK_DONE);dialog.getDialogPane().getButtonTypes().add(0,type);Button save=(Button)dialog.getDialogPane().lookupButton(type);save.setId("saveSubjectButton");save.addEventFilter(ActionEvent.ACTION,e->{e.consume();if(busy)return;try{
    var changes=new SubjectChanges(name.getText(),category.getEditor().getText(),description.getText());var admin=UserSession.current();form.setDisable(true);save.setDisable(true);dialog.getDialogPane().lookupButton(ButtonType.CANCEL).setDisable(true);message.setText("Saving subject...");
    run(()->dao.save(admin,original,changes),q->{loaded=true;update(q);subjectSearch.clear();subjectStatusFilter.setValue("All statuses");subjectCategoryFilter.setValue(null);subjectTable.getSelectionModel().select(q);subjectTable.scrollTo(q);subjectMessage.setText("Subject saved successfully.");dialog.close();},failure->{form.setDisable(false);save.setDisable(false);dialog.getDialogPane().lookupButton(ButtonType.CANCEL).setDisable(false);message.setText(error(failure));});
   }catch(IllegalArgumentException invalid){message.setText(invalid.getMessage());}});
  }dialog.showAndWait();
 }
 private void update(SubjectRecord q){records.removeIf(old->old.id()==q.id());records.add(0,q);totals();}
 private void archive(SubjectRecord q){if(busy||q.archived())return;Alert a=new Alert(Alert.AlertType.CONFIRMATION,"Existing quizzes and teacher assignments will be kept. This subject will no longer be offered for new quizzes.",ButtonType.OK,ButtonType.CANCEL);a.initOwner(subjectTable.getScene().getWindow());a.setTitle("Archive subject");a.setHeaderText("Archive "+q.name()+"?");if(a.showAndWait().filter(ButtonType.OK::equals).isEmpty())return;var admin=UserSession.current();run(()->dao.archive(admin,q),updated->{update(updated);subjectMessage.setText("Subject archived. Linked quizzes and assignments preserved.");},e->subjectMessage.setText(error(e)));}
 private <T> void run(Callable<T> work,Consumer<T> success,Consumer<Throwable> failure){if(busy)return;busy=true;controls();var identity=UserSession.current();Task<T> task=new Task<>(){protected T call()throws Exception{return work.call();}};task.setOnSucceeded(e->{busy=false;controls();if(UserSession.current()==identity)success.accept(task.getValue());else failure.accept(new SecurityException());});task.setOnFailed(e->{busy=false;controls();failure.accept(UserSession.current()==identity?task.getException():new SecurityException());});Thread t=new Thread(task,"quizora-subject-management");t.setDaemon(true);t.start();}
 private void controls(){subjectTable.setDisable(busy);subjectSearch.setDisable(busy);subjectStatusFilter.setDisable(busy);subjectCategoryFilter.setDisable(busy);refreshSubjectsButton.setDisable(busy);addSubjectButton.setDisable(busy);}
 private String error(Throwable e){if(e instanceof SecurityException)return "Administrator access is required. Sign in again.";if(e instanceof IllegalArgumentException)return e.getMessage();if(e instanceof java.sql.SQLException sql){if(sql.getErrorCode()==1062)return "A subject with that name already exists, including archived subjects.";if("40001".equals(sql.getSQLState()))return "Subject changed. Close the form, refresh, and try again.";}return "Unable to complete the request. Check the database connection and refresh.";}
}
