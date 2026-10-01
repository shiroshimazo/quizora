package quizora.controllerStudent;

import java.time.Clock;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import javafx.application.*;
import javafx.fxml.FXMLLoader;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.stage.*;
import quizora.DAO.TakeQuizDAO;
import quizora.DAO.TakeQuizTest;
import quizora.auth.UserSession;

public class TakeQuizUiTest extends Application {
    private Parent root;
    private Stage stage;
    private takeQuizController controller;
    private TakeQuizTest.Fixture fixture;
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static <T> T fx(Callable<T> work) throws Exception {
        FutureTask<T> task=new FutureTask<>(work);Platform.runLater(task);return task.get(10,TimeUnit.SECONDS);
    }
    private static void await(BooleanSupplier condition) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(12);
        while(!fx(condition::getAsBoolean)) {
            if(System.nanoTime()>deadline)throw new AssertionError("UI wait timed out");
            Thread.sleep(30);
        }
    }
    private Button button(String id){return (Button)root.lookup("#"+id);}
    private Label label(String id){return (Label)root.lookup("#"+id);}
    private void acceptDialog() {
        Platform.runLater(()->{
            try {
                var pane=Window.getWindows().stream().filter(w->w!=stage&&w.isShowing())
                        .map(w->w.getScene().getRoot()).filter(n->n instanceof DialogPane)
                        .map(n->(DialogPane)n).findFirst().orElseThrow();
                ((Button)pane.lookupButton(ButtonType.OK)).fire();
            } catch(Throwable failure){failure.printStackTrace();System.exit(1);}
        });
    }
    private void load() throws Exception {
        FXMLLoader loader=new FXMLLoader(getClass().getResource("/Resources/fxml/student/takeQuiz.fxml"));
        loader.setControllerFactory(type->new takeQuizController(new TakeQuizDAO(fixture::connect,Clock.systemDefaultZone())));
        root=loader.load();controller=loader.getController();
        root.getStylesheets().add(getClass().getResource("/Resources/css/dashboard.css").toExternalForm());
        stage.setScene(new Scene(root,1000,800));stage.show();root.applyCss();root.layout();controller.open();
    }
    @Override public void start(Stage window) throws Exception {
        Platform.setImplicitExit(false);stage=window;fixture=new TakeQuizTest.Fixture();
        UserSession.signIn(TakeQuizTest.STUDENT);load();
        new Thread(()->{
            try {
                await(()->!button("refreshButton").isDisabled());
                fx(()->{((ComboBox<?>)root.lookup("#quizPicker")).getSelectionModel().selectFirst();check(!button("startButton").isDisabled(),"Quiz selectable");acceptDialog();button("startButton").fire();return null;});
                await(()->root.lookup("#attemptPane").isVisible()&&!button("submitButton").isDisabled());
                fixture.sql("CREATE TRIGGER reject_answer BEFORE INSERT ON student_answers BEGIN SELECT RAISE(ABORT,'test failure'); END");
                fx(()->{((RadioButton)root.lookup("#answerA")).fire();return null;});
                await(()->label("statusLabel").getText().startsWith("Changes could not be saved")&&!button("submitButton").isDisabled());
                fx(()->{check(!((RadioButton)root.lookup("#answerA")).isSelected(),"Failed save restores persisted answer");return null;});
                fixture.sql("DROP TRIGGER reject_answer");
                fx(()->{check(button("previousButton").isDisabled(),"First question boundary");((RadioButton)root.lookup("#answerA")).fire();return null;});
                await(()->label("progressLabel").getText().startsWith("1 of")&&!button("submitButton").isDisabled());
                fx(()->{button("nextButton").fire();check(button("nextButton").isDisabled(),"Last question boundary");((RadioButton)root.lookup("#answerB")).fire();return null;});
                await(()->label("progressLabel").getText().startsWith("2 of")&&!button("submitButton").isDisabled());
                fx(()->{button("clearButton").fire();return null;});
                await(()->label("progressLabel").getText().startsWith("1 of")&&!button("submitButton").isDisabled());
                fx(()->{((RadioButton)root.lookup("#answerB")).fire();return null;});
                await(()->label("progressLabel").getText().startsWith("2 of")&&!button("submitButton").isDisabled());
                fixture.sql("CREATE TRIGGER reject_result BEFORE INSERT ON quiz_results BEGIN SELECT RAISE(ABORT,'test failure'); END");
                fx(()->{button("previousButton").fire();check(((RadioButton)root.lookup("#answerA")).isSelected(),"Previous saved answer restored");acceptDialog();button("submitButton").fire();return null;});
                await(()->label("statusLabel").getText().startsWith("Changes could not be saved")&&!button("submitButton").isDisabled());
                check(fixture.count("SELECT COUNT(*) FROM quiz_results")==0,"Failed submission does not show or persist success");
                fixture.sql("DROP TRIGGER reject_result");
                fx(()->{acceptDialog();button("submitButton").fire();return null;});
                await(()->root.lookup("#resultPane").isVisible());
                fx(()->{check(label("scoreLabel").getText().equals("5 / 5"),"Score displayed");button("anotherButton").fire();return null;});
                await(()->!button("refreshButton").isDisabled());
                check(fixture.count("SELECT COUNT(*) FROM quiz_results")==1,"One persisted result");
                // Resume an almost-expired attempt and let the real UI timer submit it.
                new TakeQuizDAO(fixture::connect,Clock.systemDefaultZone()).open(TakeQuizTest.STUDENT,1);
                fixture.sql("UPDATE quiz_attempts SET started_at=datetime('now','localtime','-57 seconds') WHERE status='in_progress'");
                fx(()->{controller.open();return null;});
                await(()->!button("refreshButton").isDisabled());
                fx(()->{((ComboBox<?>)root.lookup("#quizPicker")).getSelectionModel().selectFirst();check(button("startButton").getText().equals("Resume quiz"),"Resume action");button("startButton").fire();return null;});
                await(()->root.lookup("#attemptPane").isVisible()&&!button("submitButton").isDisabled());
                fx(()->{UserSession.updateProfile(new quizora.auth.AuthenticatedUser(1,"Updated Student","student"));return null;});
                await(()->root.lookup("#resultPane").isVisible());
                fx(()->{check(label("scoreLabel").getText().equals("0 / 5"),"Timer submits unanswered quiz");return null;});
                check(fixture.count("SELECT COUNT(*) FROM quiz_results")==2,"Auto-submit persisted");
                System.out.println("Take quiz UI checks passed: start confirmation, autosave, clear, navigation, failed saves, submission retry, result, resume, timer submission.");
                fx(()->{UserSession.clear();stage.close();return null;});fixture.close();Platform.exit();
            }catch(Throwable failure){failure.printStackTrace();System.exit(1);}
        },"take-quiz-ui-checks").start();
    }
    public static void main(String[] args){launch(args);}
}
