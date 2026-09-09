package quizora.controllerTeacher;

import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.concurrent.Task;
import javafx.fxml.*;
import javafx.scene.Node;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import quizora.DAO.QuizStatisticsDAO;
import quizora.auth.UserSession;
import quizora.model.QuizStatisticsData;
import quizora.model.QuizStatisticsData.Summary;

public class quizStatisticsController implements Initializable {
    @FXML private ScrollPane statisticsScroll;
    @FXML private GridPane kpiGrid,chartGrid;
    @FXML private ComboBox<Summary> quizFilter;
    @FXML private Button allQuizzesButton,refreshButton;
    @FXML private Label statusLabel,attemptsValue,submittedValue,completionValue,averageValue,performanceEmpty,progressEmpty,trendEmpty,periodLabel,quizCount;
    @FXML private BarChart<String,Number> performanceChart;
    @FXML private PieChart progressChart;
    @FXML private LineChart<String,Number> trendChart;
    @FXML private TableView<Summary> quizTable;
    private QuizStatisticsData data;
    private boolean busy,updating;
    @Override public void initialize(URL location,ResourceBundle resources) {
        column("Quiz ID",90,Summary::id);column("Quiz",230,Summary::title);column("Subject",170,Summary::subject);
        column("Status",110,q->q.archived()?"Archived":q.status());column("Attempts",100,Summary::attempts);
        column("Submitted",110,Summary::submitted);column("Scored",100,Summary::scored);column("Average (%)",120,Summary::average);
        quizFilter.valueProperty().addListener((o,old,value)->{if(!updating)renderSelection();});
        statisticsScroll.viewportBoundsProperty().addListener((o,old,b)->{grid(kpiGrid,b.getWidth()<650?1:b.getWidth()<1100?2:4);grid(chartGrid,b.getWidth()<1000?1:2);});
        grid(kpiGrid,2);grid(chartGrid,1);clear("Open Quiz Statistics to load your data.");
    }
    private <T> void column(String title,int width,java.util.function.Function<Summary,T> value) {
        var column=new TableColumn<Summary,T>(title);column.setMinWidth(width);
        column.setCellValueFactory(row->new ReadOnlyObjectWrapper<>(value.apply(row.getValue())));
        column.setCellFactory(c->new TableCell<>() {
            @Override protected void updateItem(T item,boolean empty){super.updateItem(item,empty);String text=empty?null:item==null?"N/A":item instanceof Double number?percent(number):item.toString();setText(text);setTooltip(text==null?null:new Tooltip(text));}
        });quizTable.getColumns().add(column);
    }
    private void grid(GridPane pane,int count) {
        pane.getColumnConstraints().clear();for(int i=0;i<count;i++){var c=new ColumnConstraints();c.setMinWidth(0);c.setPercentWidth(100.0/count);pane.getColumnConstraints().add(c);}
        for(int i=0;i<pane.getChildren().size();i++){var child=pane.getChildren().get(i);GridPane.setColumnIndex(child,i%count);GridPane.setRowIndex(child,i/count);}
    }
    @FXML public void refresh() {
        if(busy)return;var identity=UserSession.current();
        Long selected=quizFilter.getValue()==null?null:quizFilter.getValue().id();
        clear("Loading quiz statistics...");
        if(identity==null||!"teacher".equals(identity.role())){clear("Sign in as a teacher to view quiz statistics.");return;}
        controls(true);
        var task=new Task<QuizStatisticsData>() {
            @Override protected QuizStatisticsData call()throws Exception{return new QuizStatisticsDAO().load(identity);}
        };
        task.setOnSucceeded(e->{controls(false);if(UserSession.current()!=identity){clear("Your session changed. Sign in again.");return;}display(task.getValue());if(selected!=null)quizFilter.setValue(data.quizzes().stream().filter(q->q.id()==selected).findFirst().orElse(null));});
        task.setOnFailed(e->{controls(false);clear(UserSession.current()!=identity?"Your session changed. Sign in again.":task.getException() instanceof SecurityException?"Active teacher access is required. Sign in again.":"Statistics unavailable. Check your database connection and refresh.");});
        var thread=new Thread(task,"quizora-quiz-statistics");thread.setDaemon(true);thread.start();
    }
    void display(QuizStatisticsData snapshot) {
        data=snapshot;updating=true;quizFilter.getItems().setAll(data.quizzes());quizFilter.setValue(null);updating=false;renderSelection();
    }
    @FXML private void allQuizzes(){quizFilter.setValue(null);}
    private void renderSelection() {
        if(data==null)return;var selected=quizFilter.getValue();
        var rows=data.quizzes().stream().filter(q->selected==null||q.id()==selected.id()).toList();
        long attempts=rows.stream().mapToLong(Summary::attempts).sum(),submitted=rows.stream().mapToLong(Summary::submitted).sum(),scored=rows.stream().mapToLong(Summary::scored).sum();
        attemptsValue.setText(Long.toString(attempts));submittedValue.setText(Long.toString(submitted));
        completionValue.setText(attempts==0?"N/A":percent(100.0*submitted/attempts));
        averageValue.setText(scored==0?"N/A":percent(rows.stream().filter(q->q.average()!=null).mapToDouble(q->q.average()*q.scored()).sum()/scored));
        quizTable.getItems().setAll(rows);quizTable.sort();quizTable.setPlaceholder(new Label("No quizzes yet. Use Create Quiz to get started."));quizCount.setText(rows.size()+" of "+data.quizzes().size()+" quizzes shown");
        var performance=new XYChart.Series<String,Number>();
        rows.stream().filter(q->q.scored()>0).sorted(Comparator.comparingLong(Summary::scored).reversed().thenComparingLong(Summary::id)).limit(8).forEach(q->{
            var point=new XYChart.Data<String,Number>("#"+q.id(),q.average());performance.getData().add(point);
            point.nodeProperty().addListener((o,old,node)->describe(node,q.title()+": "+percent(q.average())+" from "+q.scored()+" scored submissions"));
        });
        performanceChart.getData().setAll(List.of(performance));performanceEmpty.setText("No scored submissions");performanceEmpty.setVisible(scored==0);
        progressChart.getData().clear();
        if(submitted>0)progressChart.getData().add(new PieChart.Data("Submitted ("+submitted+")",submitted));
        if(attempts-submitted>0)progressChart.getData().add(new PieChart.Data("In progress ("+(attempts-submitted)+")",attempts-submitted));
        progressEmpty.setText("No attempts yet");progressEmpty.setVisible(attempts==0);
        var days=new LinkedHashMap<LocalDate,Long>();for(int i=13;i>=0;i--)days.put(data.today().minusDays(i),0L);
        data.submissionsByDay().stream().filter(d->selected==null||d.quizId()==selected.id()).forEach(d->{if(days.containsKey(d.date()))days.merge(d.date(),d.count(),Long::sum);});
        var series=new XYChart.Series<String,Number>();var date=DateTimeFormatter.ofPattern("MMM d");
        days.forEach((day,count)->{var point=new XYChart.Data<String,Number>(day.format(date),count);series.getData().add(point);point.nodeProperty().addListener((o,old,node)->describe(node,day+": "+count+" submissions"));});
        trendChart.getData().setAll(List.of(series));long max=days.values().stream().mapToLong(Long::longValue).max().orElse(0);double tick=Math.max(1,Math.ceil(max/5.0));
        var axis=(NumberAxis)trendChart.getYAxis();axis.setAutoRanging(false);axis.setLowerBound(0);axis.setUpperBound(Math.max(5,Math.ceil(max/tick)*tick));axis.setTickUnit(tick);axis.setMinorTickVisible(false);
        trendEmpty.setText("No submissions in this period");trendEmpty.setVisible(max==0);
        periodLabel.setText(data.today().minusDays(13).format(date)+" - "+data.today().format(date)+" | Last 14 database-calendar days");
        statusLabel.setText(data.quizzes().isEmpty()?"No quizzes yet. Create a quiz to start tracking performance.":"Showing "+(selected==null?"all your quizzes":selected.toString())+". KPIs and comparison use all-time data; the trend covers 14 days.");
    }
    private void clear(String message) {
        data=null;updating=true;quizFilter.getItems().clear();quizFilter.setValue(null);updating=false;quizTable.getItems().clear();
        for(var label:List.of(attemptsValue,submittedValue,completionValue,averageValue))label.setText("N/A");
        performanceChart.getData().clear();progressChart.getData().clear();trendChart.getData().clear();
        for(var label:List.of(performanceEmpty,progressEmpty,trendEmpty)){label.setText("Data unavailable");label.setVisible(true);}
        quizTable.setPlaceholder(new Label("Refresh to load quiz statistics."));quizCount.setText("0 quizzes shown");periodLabel.setText("Last 14 database-calendar days");statusLabel.setText(message);
    }
    private void controls(boolean value){busy=value;quizFilter.setDisable(value);refreshButton.setDisable(value);allQuizzesButton.setDisable(value);}
    private static String percent(double value){return String.format(Locale.ROOT,"%.1f%%",value);}
    private static void describe(Node node,String text){if(node!=null){if(!node.accessibleTextProperty().isBound())node.setAccessibleText(text);Tooltip.install(node,new Tooltip(text));}}
}
