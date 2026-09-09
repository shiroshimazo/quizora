package quizora.controllerAdmin;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.stage.*;
import quizora.auth.*;
import quizora.dashboard.PanelRouter;
import quizora.database.databaseConnection;
public class SubjectManagementViewTest {
 static Stage stage;
 static <T>T fx(Callable<T> c)throws Exception{FutureTask<T> t=new FutureTask<>(c);Platform.runLater(t);return t.get(15,TimeUnit.SECONDS);}
 static void await(Callable<Boolean> c)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);while(!fx(c)){if(System.nanoTime()>end)throw new AssertionError("UI timeout");Thread.sleep(40);}}
 static Window dialog(String title){return Window.getWindows().stream().filter(w->w instanceof Stage s&&s!=stage&&s.getTitle().equals(title)).findFirst().orElse(null);}
 @SuppressWarnings("unchecked") public static void main(String[]args)throws Exception{
  String tag="ui-subject-"+java.util.UUID.randomUUID();var admin=new AuthenticationService().authenticate("admin","Admin@123".toCharArray()).orElseThrow();
  Platform.startup(()->Platform.setImplicitExit(false));
  try{
   fx(()->{UserSession.signIn(admin);stage=new Stage();PanelRouter.open(stage);((ToggleButton)stage.getScene().lookup("#subjectCategoryManagementButton")).fire();return null;});
   await(()->((Label)stage.getScene().lookup("#subjectMessage")).getText().equals("Subject records are up to date."));
   Platform.runLater(()->((Button)stage.getScene().lookup("#addSubjectButton")).fire());await(()->dialog("Add subject")!=null);
   fx(()->{var scene=dialog("Add subject").getScene();((Button)scene.lookup("#saveSubjectButton")).fire();if(((Label)scene.lookup("#subjectFormMessage")).getText().isBlank())throw new AssertionError("Missing validation");((TextField)scene.lookup("#subjectNameField")).setText(tag);((ComboBox<String>)scene.lookup("#subjectCategoryField")).getEditor().setText("Science");((TextArea)scene.lookup("#subjectDescriptionField")).setText("Test description");((Button)scene.lookup("#saveSubjectButton")).fire();return null;});await(()->dialog("Add subject")==null);
   fx(()->{((TextField)stage.getScene().lookup("#subjectSearch")).setText(tag);var table=(TableView<?>)stage.getScene().lookup("#subjectTable");if(table.getItems().size()!=1)throw new AssertionError("Saved subject missing");((ComboBox<String>)stage.getScene().lookup("#subjectCategoryFilter")).setValue("Science");table.applyCss();table.layout();return null;});
   Platform.runLater(()->((Button)stage.getScene().lookup("#editSubjectButton")).fire());await(()->dialog("Edit subject")!=null);
   fx(()->{var scene=dialog("Edit subject").getScene();((TextArea)scene.lookup("#subjectDescriptionField")).setText("Edited description");((Button)scene.lookup("#saveSubjectButton")).fire();return null;});await(()->dialog("Edit subject")==null);
   fx(()->{((TextField)stage.getScene().lookup("#subjectSearch")).setText(tag);var table=(TableView<?>)stage.getScene().lookup("#subjectTable");table.applyCss();table.layout();return null;});
   Platform.runLater(()->((Button)stage.getScene().lookup("#archiveSubjectButton")).fire());await(()->dialog("Archive subject")!=null);
   fx(()->{var pane=(DialogPane)dialog("Archive subject").getScene().getRoot();((Button)pane.lookupButton(ButtonType.CANCEL)).fire();return null;});await(()->dialog("Archive subject")==null);
   Platform.runLater(()->((Button)stage.getScene().lookup("#archiveSubjectButton")).fire());await(()->dialog("Archive subject")!=null);
   fx(()->{var pane=(DialogPane)dialog("Archive subject").getScene().getRoot();((Button)pane.lookupButton(ButtonType.OK)).fire();return null;});await(()->((Label)stage.getScene().lookup("#subjectMessage")).getText().startsWith("Subject archived."));
   fx(()->{((ComboBox<String>)stage.getScene().lookup("#subjectStatusFilter")).setValue("Archived");var table=(TableView<?>)stage.getScene().lookup("#subjectTable");if(table.getItems().size()!=1)throw new AssertionError("Archive filter");table.applyCss();table.layout();if(!stage.getScene().lookup("#editSubjectButton").isDisabled())throw new AssertionError("Archived edit enabled");return null;});
   System.out.println("PASS: subject modal validation/add/edit, search/category/status filters, archive cancel/confirm and archived edit protection.");
  }finally{fx(()->{stage.close();UserSession.clear();return null;});Platform.exit();try(var c=databaseConnection.getConnection();var s=c.prepareStatement("DELETE FROM subjects WHERE subject_name=?")){s.setString(1,tag);s.executeUpdate();}}
 }
}
