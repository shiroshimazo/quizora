package quizora.controllerAdmin;
import java.util.*;
import java.time.LocalDate;
import javafx.fxml.FXML;
import javafx.concurrent.Task;
import javafx.collections.*;
import javafx.collections.transformation.*;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.control.*;
import javafx.scene.chart.*;
import javafx.scene.layout.*;
import quizora.auth.UserSession;
import quizora.DAO.ResultsDAO;
import quizora.model.*;
public class resultsController {
 @FXML private ScrollPane resultsScroll;
 @FXML private GridPane resultsKpiGrid,resultsChartGrid;
 @FXML private Label submissionsValue,resultAverageValue,resultPassValue,resultStudentsValue,resultsMessage,resultCount,distributionEmpty,outcomeEmpty;
 @FXML private TextField resultSearch;
 @FXML private ComboBox<QuizChoice> resultSubject;
 @FXML private ComboBox<String> resultOutcome;
 @FXML private Spinner<Integer> resultThreshold;
 @FXML private DatePicker resultFrom,resultTo;
 @FXML private Button refreshResultsButton,resetResultsButton;
 @FXML private TableView<ResultRecord> resultsTable;
 @FXML private BarChart<String,Number> distributionChart;
 @FXML private PieChart outcomeChart;
 private final ObservableList<ResultRecord> records=FXCollections.observableArrayList();
 private final FilteredList<ResultRecord> filtered=new FilteredList<>(records);
 private boolean busy,loaded,updating;
 @FXML private void initialize(){
  resultOutcome.getItems().setAll("All outcomes","Passed","Failed");resultOutcome.setValue("All outcomes");resultThreshold.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0,100,75));resultThreshold.setEditable(false);
  resultFrom.setEditable(false);resultTo.setEditable(false);resultSubject.setPromptText("All subjects");
  resultSearch.textProperty().addListener((o,a,b)->filter());resultSubject.valueProperty().addListener((o,a,b)->filter());resultOutcome.valueProperty().addListener((o,a,b)->filter());resultThreshold.valueProperty().addListener((o,a,b)->filter());resultFrom.valueProperty().addListener((o,a,b)->filter());resultTo.valueProperty().addListener((o,a,b)->filter());
  column("Attempt ID",95,ResultRecord::attemptId);column("Student",160,ResultRecord::student);column("Username",130,ResultRecord::username);column("Quiz",200,ResultRecord::quiz);column("Subject",150,ResultRecord::subject);column("Score",95,r->r.score()+" / "+r.total());column("Percentage",105,ResultRecord::percentage);column("Outcome",100,r->r.passed(resultThreshold.getValue())?"Passed":"Failed");column("Submitted",180,ResultRecord::submittedAt);
  SortedList<ResultRecord> sorted=new SortedList<>(filtered);sorted.comparatorProperty().bind(resultsTable.comparatorProperty());resultsTable.setItems(sorted);
  distributionChart.setAnimated(false);outcomeChart.setAnimated(false);distributionChart.setLegendVisible(false);
  resultsScroll.viewportBoundsProperty().addListener((o,a,b)->{grid(resultsKpiGrid,b.getWidth()<650?1:b.getWidth()<1050?2:4);grid(resultsChartGrid,b.getWidth()<950?1:2);});grid(resultsKpiGrid,4);grid(resultsChartGrid,2);clear();
 }
 private <T> void column(String title,int width,java.util.function.Function<ResultRecord,T> value){var c=new TableColumn<ResultRecord,T>(title);c.setMinWidth(width);c.setCellValueFactory(r->new ReadOnlyObjectWrapper<>(value.apply(r.getValue())));c.setCellFactory(col->new TableCell<>(){protected void updateItem(T item,boolean empty){super.updateItem(item,empty);String text=empty||item==null?null:title.equals("Percentage")?String.format(Locale.ROOT,"%.1f%%",item):item instanceof java.time.LocalDateTime date?date.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")):item.toString();setText(text);setTooltip(text==null?null:new Tooltip(text));}});resultsTable.getColumns().add(c);}
 private void grid(GridPane pane,int count){pane.getColumnConstraints().clear();for(int i=0;i<count;i++){var c=new ColumnConstraints();c.setPercentWidth(100.0/count);c.setMinWidth(0);pane.getColumnConstraints().add(c);}for(int i=0;i<pane.getChildren().size();i++){GridPane.setColumnIndex(pane.getChildren().get(i),i%count);GridPane.setRowIndex(pane.getChildren().get(i),i/count);}}
 @FXML public void refresh(){if(busy)return;busy=true;clear();controls();resultsMessage.setText("Loading results...");var admin=UserSession.current();Task<List<ResultRecord>> task=new Task<>(){protected List<ResultRecord> call()throws Exception{return new ResultsDAO().load(admin);}};
  task.setOnSucceeded(e->{busy=false;if(UserSession.current()!=admin){clear();resultsMessage.setText("Your session changed. Sign in again.");}else display(task.getValue());controls();});task.setOnFailed(e->{busy=false;clear();controls();resultsMessage.setText(task.getException() instanceof SecurityException?"Administrator access is required. Sign in again.":"Results unavailable. Check the database connection and refresh.");});Thread t=new Thread(task,"quizora-results");t.setDaemon(true);t.start();
 }
 void display(List<ResultRecord> data){updating=true;loaded=true;records.setAll(data);QuizChoice selected=resultSubject.getValue();var choices=new TreeMap<Long,QuizChoice>();data.forEach(r->choices.put(r.subjectId(),new QuizChoice(r.subjectId(),r.subject())));resultSubject.getItems().setAll(choices.values().stream().sorted(Comparator.comparing(QuizChoice::name)).toList());resultSubject.setValue(selected==null?null:choices.get(selected.id()));updating=false;filter();}
 private void clear(){loaded=false;records.clear();filtered.setPredicate(r->true);distributionChart.getData().clear();outcomeChart.getData().clear();for(Label label:List.of(submissionsValue,resultAverageValue,resultPassValue,resultStudentsValue))label.setText("?");distributionEmpty.setText("Data unavailable");outcomeEmpty.setText("Data unavailable");distributionEmpty.setVisible(true);outcomeEmpty.setVisible(true);resultsTable.setPlaceholder(new Label("Refresh to load results."));resultCount.setText("0 results shown");}
 @FXML private void resetFilters(){updating=true;resultSearch.clear();resultSubject.setValue(null);resultOutcome.setValue("All outcomes");resultFrom.setValue(null);resultTo.setValue(null);updating=false;filter();}
 private void filter(){if(updating||!loaded)return;LocalDate from=resultFrom.getValue(),to=resultTo.getValue();boolean invalid=from!=null&&to!=null&&from.isAfter(to);String term=resultSearch.getText().strip().toLowerCase(Locale.ROOT);var subject=resultSubject.getValue();int threshold=resultThreshold.getValue();String outcome=resultOutcome.getValue();
  filtered.setPredicate(r->!invalid&&(subject==null||subject.id()==r.subjectId())&&(from==null||!r.submittedAt().toLocalDate().isBefore(from))&&(to==null||!r.submittedAt().toLocalDate().isAfter(to))&&(outcome.equals("All outcomes")||outcome.equals(r.passed(threshold)?"Passed":"Failed"))&&(r.attemptId()+" "+r.studentId()+" "+r.student()+" "+r.username()+" "+r.quizId()+" "+r.quiz()+" "+r.subject()).toLowerCase(Locale.ROOT).contains(term));
  int size=filtered.size();long passed=filtered.stream().filter(r->r.passed(threshold)).count();submissionsValue.setText(""+size);resultStudentsValue.setText(""+filtered.stream().map(ResultRecord::studentId).distinct().count());resultAverageValue.setText(size==0?"N/A":String.format(Locale.ROOT,"%.1f%%",filtered.stream().mapToDouble(ResultRecord::percentage).average().orElseThrow()));resultPassValue.setText(size==0?"N/A":String.format(Locale.ROOT,"%.1f%%",100.0*passed/size));
  long[] bins=new long[5];filtered.forEach(r->bins[Math.min(4,(int)(r.percentage()/20))]++);String[] names={"0?<20%","20?<40%","40?<60%","60?<80%","80?100%"};var series=new XYChart.Series<String,Number>();for(int i=0;i<5;i++)series.getData().add(new XYChart.Data<>(names[i],bins[i]));distributionChart.getData().setAll(List.of(series));
  var axis=(NumberAxis)distributionChart.getYAxis();long max=Arrays.stream(bins).max().orElse(0);double tick=Math.max(1,Math.ceil(max/5.0));axis.setAutoRanging(false);axis.setLowerBound(0);axis.setUpperBound(Math.max(5,Math.ceil(max/tick)*tick));axis.setTickUnit(tick);axis.setMinorTickVisible(false);
  outcomeChart.getData().clear();if(passed>0)outcomeChart.getData().add(new PieChart.Data("Passed ("+passed+")",passed));if(size-passed>0)outcomeChart.getData().add(new PieChart.Data("Failed ("+(size-passed)+")",size-passed));distributionEmpty.setText("No matching results");outcomeEmpty.setText("No matching results");distributionEmpty.setVisible(size==0);outcomeEmpty.setVisible(size==0);
  resultsTable.refresh();resultsTable.setPlaceholder(new Label(invalid?"Choose a valid date range.":records.isEmpty()?"No scored submissions yet.":"No results match your filters."));resultCount.setText(size+" of "+records.size()+" scored submissions shown");resultsMessage.setText(invalid?"Start date must be on or before end date.":"KPIs and charts reflect the filtered table. Passing score: "+threshold+"%. Dates use database local time.");
 }
 private void controls(){for(javafx.scene.Node node:List.of(resultSearch,resultSubject,resultOutcome,resultThreshold,resultFrom,resultTo,refreshResultsButton,resetResultsButton))node.setDisable(busy);}
}
