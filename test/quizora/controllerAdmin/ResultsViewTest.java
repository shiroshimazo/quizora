package quizora.controllerAdmin;
import java.util.*;
import java.time.*;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.chart.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import quizora.model.*;
public class ResultsViewTest {
 static <T>T fx(Callable<T> c)throws Exception{FutureTask<T> t=new FutureTask<>(c);Platform.runLater(t);return t.get(20,TimeUnit.SECONDS);}
 @SuppressWarnings("unchecked") public static void main(String[]args)throws Exception{
  Platform.startup(()->Platform.setImplicitExit(false));
  try{fx(()->{
   quizora.Quizora.satoshi(14);FXMLLoader loader=new FXMLLoader(ResultsViewTest.class.getResource("/Resources/fxml/admin/results.fxml"));ScrollPane root=loader.load();resultsController controller=loader.getController();root.setVisible(true);root.setManaged(true);Stage stage=new Stage();Scene scene=new Scene(root,1100,850);scene.getStylesheets().add(ResultsViewTest.class.getResource("/Resources/css/dashboard.css").toExternalForm());stage.setScene(scene);stage.show();
   try{
    var date=LocalDateTime.of(2026,9,9,10,0);var rows=List.of(new ResultRecord(1,1,"Alice","alice",1,"Math Quiz",1,"Math",0,10,date),new ResultRecord(2,1,"Alice","alice",1,"Math Quiz",1,"Math",2,10,date),new ResultRecord(3,2,"Bob","bob",2,"Science Quiz",2,"Science",3,4,date.minusDays(1)),new ResultRecord(4,3,"Carol","carol",2,"Science Quiz",2,"Science",10,10,date));
    controller.display(rows);
    require(((Label)root.lookup("#submissionsValue")).getText().equals("4"),"Submission count");require(((Label)root.lookup("#resultStudentsValue")).getText().equals("3"),"Unique students");require(((Label)root.lookup("#resultAverageValue")).getText().equals("48.8%"),"Mean normalized score");require(((Label)root.lookup("#resultPassValue")).getText().equals("50.0%"),"Pass rate");
    var chart=(BarChart<String,Number>)root.lookup("#distributionChart");int[] expected={1,1,0,1,1};for(int i=0;i<5;i++)require(chart.getData().getFirst().getData().get(i).getYValue().intValue()==expected[i],"Score bins");
    ((TextField)root.lookup("#resultSearch")).setText("Alice");require(((Label)root.lookup("#submissionsValue")).getText().equals("2"),"Search drives KPIs");require(((PieChart)root.lookup("#outcomeChart")).getData().size()==1,"Search drives chart");
    ((Button)root.lookup("#resetResultsButton")).fire();((ComboBox<String>)root.lookup("#resultOutcome")).setValue("Passed");require(((TableView<?>)root.lookup("#resultsTable")).getItems().size()==2,"Outcome filter");
    ((Spinner<Integer>)root.lookup("#resultThreshold")).getValueFactory().setValue(80);require(((TableView<?>)root.lookup("#resultsTable")).getItems().size()==1,"Threshold updates outcome");
    ((Button)root.lookup("#resetResultsButton")).fire();((DatePicker)root.lookup("#resultFrom")).setValue(date.toLocalDate());((DatePicker)root.lookup("#resultTo")).setValue(date.toLocalDate());require(((TableView<?>)root.lookup("#resultsTable")).getItems().size()==3,"Inclusive date range");
    ((DatePicker)root.lookup("#resultFrom")).setValue(date.toLocalDate().plusDays(1));require(((TableView<?>)root.lookup("#resultsTable")).getItems().isEmpty(),"Invalid date range");require(((Label)root.lookup("#resultsMessage")).getText().startsWith("Start date"),"Date validation message");
    ((Button)root.lookup("#resetResultsButton")).fire();for(int width:new int[]{580,1100}){root.resize(width,850);for(int i=0;i<4;i++){root.applyCss();root.layout();}require(root.getContent().getLayoutBounds().getWidth()<=root.getViewportBounds().getWidth()+1,"No outer overflow");require(((GridPane)root.lookup("#resultsChartGrid")).getColumnConstraints().size()==(width<950?1:2),"Chart reflow");}
    javax.imageio.ImageIO.write(javafx.embed.swing.SwingFXUtils.fromFXImage(root.snapshot(null,null),null),"png",new java.io.File("build/results-preview.png"));
    controller.display(List.of());require(((Label)root.lookup("#resultAverageValue")).getText().equals("N/A"),"Empty average");require(root.lookup("#outcomeEmpty").isVisible(),"Empty chart");
    System.out.println("PASS: Results KPIs, charts, score boundaries, search/outcome/threshold/date filters, empty states and responsive layout.");
   }finally{stage.close();}return null;
  });}finally{Platform.exit();}
 }
 static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
