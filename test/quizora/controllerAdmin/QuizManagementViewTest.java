package quizora.controllerAdmin;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.stage.*;
import quizora.auth.*;
import quizora.dashboard.PanelRouter;
import quizora.database.databaseConnection;
import quizora.model.QuizChoice;
public class QuizManagementViewTest {
 static Stage stage;
 static <T>T fx(Callable<T> c)throws Exception{FutureTask<T> t=new FutureTask<>(c);Platform.runLater(t);return t.get(15,TimeUnit.SECONDS);}
 static void await(Callable<Boolean> c)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);while(!fx(c)){if(System.nanoTime()>end)throw new AssertionError("UI timeout");Thread.sleep(40);}}
 static Window dialog(){return Window.getWindows().stream().filter(w->w instanceof Stage s&&s!=stage&&s.getTitle().equals("Add quiz")).findFirst().orElse(null);}
 @SuppressWarnings("unchecked") public static void main(String[]args)throws Exception{
  String tag="ui-quiz-"+java.util.UUID.randomUUID();long subject;
  var admin=new AuthenticationService().authenticate("admin","Admin@123".toCharArray()).orElseThrow();
  try(var c=databaseConnection.getConnection();var s=c.prepareStatement("INSERT INTO subjects(subject_name) VALUES(?)",java.sql.Statement.RETURN_GENERATED_KEYS)){s.setString(1,tag);s.executeUpdate();try(var r=s.getGeneratedKeys()){r.next();subject=r.getLong(1);}}
  Platform.startup(()->Platform.setImplicitExit(false));
  try{
   fx(()->{UserSession.signIn(admin);stage=new Stage();PanelRouter.open(stage);((ToggleButton)stage.getScene().lookup("#quizManagementButton")).fire();return null;});
   await(()->((Label)stage.getScene().lookup("#quizMessage")).getText().equals("Quiz records are up to date."));
   Platform.runLater(()->((Button)stage.getScene().lookup("#addQuizButton")).fire());await(()->dialog()!=null);
   fx(()->{var scene=dialog().getScene();((Button)scene.lookup("#saveQuizButton")).fire();if(((Label)scene.lookup("#quizFormMessage")).getText().isBlank())throw new AssertionError("Missing validation");
    ((TextField)scene.lookup("#quizTitleField")).setText(tag);
    var subjects=(ComboBox<QuizChoice>)scene.lookup("#quizSubjectField");subjects.setValue(subjects.getItems().stream().filter(s->s.id()==subject).findFirst().orElseThrow());
    ((ComboBox<QuizChoice>)scene.lookup("#quizTeacherField")).getSelectionModel().selectFirst();
    ((ComboBox<String>)scene.lookup("#quizStateField")).setValue("published");
    ((Button)scene.lookup("#addQuestionButton")).fire();
    ((TextArea)scene.lookup("#questionTextField")).setText("2 + 2?");
    for(String letter:new String[]{"A","B","C","D"})((TextField)scene.lookup("#option"+letter+"Field")).setText(letter);
    ((ComboBox<String>)scene.lookup("#correctAnswerField")).setValue("A");
    ((Button)scene.lookup("#saveQuizButton")).fire();return null;});
   await(()->dialog()==null);
   fx(()->{((TextField)stage.getScene().lookup("#quizSearch")).setText(tag);var table=(TableView<?>)stage.getScene().lookup("#quizTable");if(table.getItems().size()!=1)throw new AssertionError("Saved quiz not shown");return null;});
   System.out.println("PASS: Quiz panel loads; modal validates, creates published quiz with question, closes and updates searchable directory.");
  }finally{
   fx(()->{stage.close();UserSession.clear();return null;});Platform.exit();
   try(var c=databaseConnection.getConnection()){
    try(var s=c.prepareStatement("DELETE x FROM questions x JOIN quizzes q ON q.quiz_id=x.quiz_id WHERE q.title=?")){s.setString(1,tag);s.executeUpdate();}
    try(var s=c.prepareStatement("DELETE FROM quizzes WHERE title=?")){s.setString(1,tag);s.executeUpdate();}
    try(var s=c.prepareStatement("DELETE FROM subjects WHERE subject_id=?")){s.setLong(1,subject);s.executeUpdate();}
   }
  }
 }
}
