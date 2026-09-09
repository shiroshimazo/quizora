package quizora.controllerAdmin;

import javafx.fxml.FXML;
import javafx.concurrent.Task;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.stage.FileChooser;
import quizora.DAO.ReportsDAO;
import quizora.auth.UserSession;
import quizora.model.*;

public class reportsController {
 @FXML private ScrollPane reportsScroll;
 @FXML private GridPane reportsKpiGrid;
 @FXML private Label reportQuizzesValue,reportStudentsValue,reportTeachersValue,reportsMessage,reportNotes,reportHeading;
 @FXML private ComboBox<String> reportType;
 @FXML private Spinner<Integer> passThreshold;
 @FXML private Button refreshReportsButton,exportReportButton;
 @FXML private TableView<ReportData.Metric> reportTable;
 @FXML private TableColumn<ReportData.Metric,String> metricColumn,valueColumn;
 private ReportData data;
 private boolean busy;
 @FXML private void initialize(){
  reportType.getItems().setAll("Quiz statistics","Student statistics","Teacher statistics");reportType.setValue("Quiz statistics");
  passThreshold.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0,100,75));passThreshold.setEditable(false);
  reportType.valueProperty().addListener((o,a,b)->render());passThreshold.valueProperty().addListener((o,a,b)->{if(data!=null)refresh();});
  metricColumn.setCellValueFactory(c->new ReadOnlyStringWrapper(c.getValue().label()));valueColumn.setCellValueFactory(c->new ReadOnlyStringWrapper(c.getValue().value()));
  reportTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);reportTable.setPlaceholder(new Label("Refresh to generate the report."));
  reportsScroll.viewportBoundsProperty().addListener((o,a,b)->reflow(b.getWidth()));reflow(900);controls();
 }
 private void reflow(double width){int count=width<650?1:3;reportsKpiGrid.getColumnConstraints().clear();for(int i=0;i<count;i++){var c=new ColumnConstraints();c.setPercentWidth(100.0/count);c.setMinWidth(0);reportsKpiGrid.getColumnConstraints().add(c);}for(int i=0;i<3;i++){GridPane.setColumnIndex(reportsKpiGrid.getChildren().get(i),i%count);GridPane.setRowIndex(reportsKpiGrid.getChildren().get(i),i/count);}}
 @FXML public void refresh(){
  if(busy)return;var admin=UserSession.current();int threshold=passThreshold.getValue();busy=true;clear();controls();reportsMessage.setText("Generating reports...");
  Task<ReportData> task=new Task<>(){protected ReportData call()throws Exception{return new ReportsDAO().load(admin,threshold);}};
  task.setOnSucceeded(e->{busy=false;if(UserSession.current()!=admin){clear();reportsMessage.setText("Your session changed. Sign in again.");}else{data=task.getValue();render();reportsMessage.setText("Generated "+data.generatedAt().toString().replace('T',' ')+" (database local time). All-time statistics.");}controls();});
  task.setOnFailed(e->{busy=false;clear();reportsMessage.setText(task.getException() instanceof SecurityException?"Administrator access is required. Sign in again.":"Reports unavailable. Check the database connection and refresh.");controls();});
  Thread t=new Thread(task,"quizora-reports");t.setDaemon(true);t.start();
 }
 private void clear(){data=null;reportTable.getItems().clear();reportQuizzesValue.setText("?");reportStudentsValue.setText("?");reportTeachersValue.setText("?");reportNotes.setText("");}
 private void render(){
  reportHeading.setText(reportType.getValue());if(data==null)return;
  reportQuizzesValue.setText(""+data.quizzes());reportStudentsValue.setText(""+data.students().total());reportTeachersValue.setText(""+data.teachers().total());reportTable.getItems().setAll(data.metrics(reportType.getValue()));
  reportNotes.setText(reportType.getValue().equals("Quiz statistics")?"Scores use submitted attempts with finalized results. Average score is the mean percentage per scored attempt, including retakes. Pass rate uses the displayed threshold. N/A means no scored submissions.":"Active and inactive counts exclude archived accounts. Totals include archived accounts.");controls();
 }
 private void controls(){refreshReportsButton.setDisable(busy);reportType.setDisable(busy);passThreshold.setDisable(busy||!"Quiz statistics".equals(reportType.getValue()));exportReportButton.setDisable(busy||data==null);}
 @FXML private void exportPdf(){
  if(busy||data==null)return;var identity=UserSession.current();if(identity==null||!"admin".equals(identity.role())){clear();controls();reportsMessage.setText("Administrator access is required.");return;}
  FileChooser chooser=new FileChooser();chooser.setTitle("Export report as PDF");chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF report","*.pdf"));chooser.setInitialFileName(reportType.getValue().toLowerCase(java.util.Locale.ROOT).replace(' ','-')+".pdf");var file=chooser.showSaveDialog(reportsScroll.getScene().getWindow());if(file==null)return;
  ReportData snapshot=data;String type=reportType.getValue();busy=true;controls();Task<Void> task=new Task<>(){protected Void call()throws Exception{ReportPdf.write(file.toPath(),snapshot,type);return null;}};
  task.setOnSucceeded(e->{busy=false;controls();reportsMessage.setText("PDF exported: "+file.getName());});task.setOnFailed(e->{busy=false;controls();reportsMessage.setText("Unable to export PDF. Check the destination and try again.");});Thread t=new Thread(task,"quizora-report-export");t.setDaemon(true);t.start();
 }
}
