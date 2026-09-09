package quizora.controllerTeacher;
import java.util.*;
import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.chart.*;
import javafx.scene.layout.*;
import quizora.model.QuizStatisticsData;
import quizora.model.QuizStatisticsData.*;

public class QuizStatisticsViewTest {
 @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception {
  var done=new CountDownLatch(1);var failure=new AtomicReference<Throwable>();
  Platform.startup(()->{try {
   quizora.Quizora.satoshi(14);var loader=new FXMLLoader(QuizStatisticsViewTest.class.getResource("/Resources/fxml/teacher/quizStatistics.fxml"));
   ScrollPane root=loader.load();root.setVisible(true);root.setManaged(true);var scene=new Scene(root,1200,1800);scene.getStylesheets().add(QuizStatisticsViewTest.class.getResource("/Resources/css/dashboard.css").toExternalForm());
   quizStatisticsController controller=loader.getController();root.applyCss();root.layout();
   var today=LocalDate.of(2026,9,9);
   var rows=List.of(new Summary(1,"Fractions","Math","published",false,4,3,2,50.0),new Summary(2,"Fractions","Math","closed",true,1,1,1,100.0),new Summary(3,"Empty quiz","Science","draft",false,0,0,0,null));
   controller.display(new QuizStatisticsData(today,rows,List.of(new Daily(1,today,2),new Daily(2,today.minusDays(13),1))));
   require(((Label)root.lookup("#averageValue")).getText().equals("66.7%"),"Average weighted by scored attempts");require(((Label)root.lookup("#completionValue")).getText().equals("80.0%"),"Completion denominator");
   var bar=(BarChart<String,Number>)root.lookup("#performanceChart");require(bar.getData().getFirst().getData().size()==2,"Only scored quizzes charted");require(!bar.getData().getFirst().getData().get(0).getXValue().equals(bar.getData().getFirst().getData().get(1).getXValue()),"Duplicate quiz titles use distinct IDs");
   var line=(LineChart<String,Number>)root.lookup("#trendChart");require(line.getData().getFirst().getData().size()==14,"Zero-filled fourteen days");require(line.getData().getFirst().getData().get(1).getYValue().longValue()==0,"Missing date is zero");
   var select=(ComboBox<Summary>)root.lookup("#quizFilter");select.setValue(rows.get(0));require(((Label)root.lookup("#attemptsValue")).getText().equals("4"),"Selected KPI");require(((TableView<?>)root.lookup("#quizTable")).getItems().size()==1,"Selected table");require(line.getData().getFirst().getData().getFirst().getYValue().longValue()==0,"Selected trend isolation");
   select.setValue(rows.get(2));require(((Label)root.lookup("#averageValue")).getText().equals("N/A")&&root.lookup("#progressEmpty").isVisible(),"Quiz without attempts");
   ((Button)root.lookup("#allQuizzesButton")).fire();require(((TableView<?>)root.lookup("#quizTable")).getItems().size()==3,"All quizzes restored");
   for(int width:new int[]{580,900,1300}){root.resize(width,1800);for(int i=0;i<4;i++){root.applyCss();root.layout();}require(root.getContent().getLayoutBounds().getWidth()<=root.getViewportBounds().getWidth()+1,"No outer overflow");require(((GridPane)root.lookup("#chartGrid")).getColumnConstraints().size()==(width<1000?1:2),"Chart reflow");}
   ((Label)root.lookup("#statusLabel")).setText("Illustrative test data - not database records");
   javax.imageio.ImageIO.write(javafx.embed.swing.SwingFXUtils.fromFXImage(root.snapshot(null,null),null),"png",new java.io.File("build/quiz-statistics-preview.png"));
   controller.display(new QuizStatisticsData(today,List.of(),List.of()));require(((Label)root.lookup("#attemptsValue")).getText().equals("0"),"Empty data");
   quizora.auth.UserSession.clear();controller.refresh();require(((Label)root.lookup("#attemptsValue")).getText().equals("N/A")&&bar.getData().isEmpty(),"Denied access clears charts");
   System.out.println("PASS: statistics metrics, quiz selection, charts, zero days, empty states and responsive layout.");
  }catch(Throwable e){failure.set(e);}finally{done.countDown();}});
  done.await();Platform.exit();if(failure.get()!=null)throw new AssertionError(failure.get());
 }
 static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
