package quizora.controllerAdmin;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.stage.Stage;
import quizora.auth.*;
import quizora.dashboard.PanelRouter;
public class ReportsViewTest {
 static Stage stage;
 static <T>T fx(Callable<T> c)throws Exception{FutureTask<T> t=new FutureTask<>(c);Platform.runLater(t);return t.get(15,TimeUnit.SECONDS);}
 static void await(Callable<Boolean> c)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);while(!fx(c)){if(System.nanoTime()>end)throw new AssertionError("UI timeout");Thread.sleep(40);}}
 @SuppressWarnings("unchecked") public static void main(String[]args)throws Exception{
  var admin=new AuthenticationService().authenticate("admin","Admin@123".toCharArray()).orElseThrow();Platform.startup(()->Platform.setImplicitExit(false));
  try{
   fx(()->{UserSession.signIn(admin);stage=new Stage();PanelRouter.open(stage);((ToggleButton)stage.getScene().lookup("#reportsButton")).fire();return null;});
   await(()->((Label)stage.getScene().lookup("#reportsMessage")).getText().startsWith("Generated"));
   fx(()->{var table=(TableView<?>)stage.getScene().lookup("#reportTable");if(table.getItems().size()!=10)throw new AssertionError("Quiz metrics missing");
    var types=(ComboBox<String>)stage.getScene().lookup("#reportType");for(String type:new String[]{"Student statistics","Teacher statistics"}){types.setValue(type);if(table.getItems().size()!=4)throw new AssertionError("Account metrics missing");if(!stage.getScene().lookup("#passThreshold").isDisabled())throw new AssertionError("Threshold enabled for accounts");}
    types.setValue("Quiz statistics");((Spinner<Integer>)stage.getScene().lookup("#passThreshold")).getValueFactory().setValue(60);return null;});
   await(()->!stage.getScene().lookup("#exportReportButton").isDisabled());
   fx(()->{var root=(ScrollPane)stage.getScene().lookup("#reportsScroll");for(int width:new int[]{580,1100}){root.resize(width,850);for(int i=0;i<4;i++){root.applyCss();root.layout();}if(root.getContent().getLayoutBounds().getWidth()>root.getViewportBounds().getWidth()+1)throw new AssertionError("Horizontal overflow");}
    javax.imageio.ImageIO.write(javafx.embed.swing.SwingFXUtils.fromFXImage(root.snapshot(null,null),null),"png",new java.io.File("build/reports-preview.png"));return null;});
   System.out.println("PASS: reports navigation, three report types, threshold regeneration, export availability and responsive layouts.");
  }finally{fx(()->{stage.close();UserSession.clear();return null;});Platform.exit();}
 }
}
