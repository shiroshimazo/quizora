package quizora.controllerStudent;

import java.time.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import javafx.application.*;
import javafx.fxml.FXMLLoader;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.stage.*;
import quizora.DAO.*;
import quizora.auth.UserSession;

public class StudentPanelsUiTest extends Application {
    private Parent root;private Stage stage;private TakeQuizTest.Fixture fixture;
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static <T>T fx(Callable<T> work)throws Exception{FutureTask<T> task=new FutureTask<>(work);Platform.runLater(task);return task.get(10,TimeUnit.SECONDS);}
    private static void await(BooleanSupplier condition)throws Exception{
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(12);
        while(!fx(condition::getAsBoolean)){if(System.nanoTime()>deadline)throw new AssertionError("UI wait timed out");Thread.sleep(30);}
    }
    private DialogPane dialog(){return Window.getWindows().stream().filter(w->w!=stage&&w.isShowing()).map(w->w.getScene().getRoot()).filter(n->n instanceof DialogPane).map(n->(DialogPane)n).findFirst().orElse(null);}
    @Override public void start(Stage window)throws Exception{
        Platform.setImplicitExit(false);stage=window;fixture=new TakeQuizTest.Fixture();
        var attempts=new TakeQuizDAO(fixture::connect,Clock.systemDefaultZone());
        var attempt=attempts.open(TakeQuizTest.STUDENT,1);attempts.save(TakeQuizTest.STUDENT,attempt.id(),1,"A");attempts.submit(TakeQuizTest.STUDENT,attempt.id());
        FXMLLoader loader=new FXMLLoader(getClass().getResource("/Resources/fxml/student/studentDashbaord.fxml"));
        loader.setControllerFactory(type->{
            if(type==quizResultsController.class)return new quizResultsController(new StudentResultsDAO(fixture::connect));
            if(type==profileController.class)return new profileController(new StudentProfileDAO(fixture::connect));
            try{return type.getDeclaredConstructor().newInstance();}catch(Exception e){throw new RuntimeException(e);}
        });
        UserSession.clear();root=loader.load();UserSession.signIn(TakeQuizTest.STUDENT);
        stage.setScene(new Scene(root,1280,850));stage.show();root.applyCss();root.layout();
        Node results=(Node)loader.getNamespace().get("studentQuizResultsContent");
        Node profile=(Node)loader.getNamespace().get("studentProfileContent");
        Object profileController=loader.getNamespace().get("studentProfileContentController");
        new Thread(()->{
            try{
                fx(()->{((ToggleButton)root.lookup("#quizResultsButton")).fire();check(results.isVisible()&&!profile.isVisible(),"Results navigation");return null;});
                TableView<?> table=fx(()->(TableView<?>)results.lookup("#resultsTable"));
                await(()->table.getItems().size()==1);
                fx(()->{
                    check(((Label)results.lookup("#averageLabel")).getText().equals("60.0%"),"Average summary");
                    table.getSelectionModel().selectFirst();check(((Label)results.lookup("#detailLabel")).getText().contains("3 / 5"),"Result details");
                    ((TextField)results.lookup("#searchField")).setText("nothing");check(table.getItems().isEmpty(),"Search empty state");
                    ((Button)results.lookup("#clearButton")).fire();check(table.getItems().size()==1,"Clear filters");
                    ((DatePicker)results.lookup("#fromDate")).setValue(LocalDate.now().plusDays(1));
                    ((DatePicker)results.lookup("#toDate")).setValue(LocalDate.now());check(table.getItems().isEmpty(),"Invalid date range");
                    check(((Label)results.lookup("#statusLabel")).getText().startsWith("From date"),"Date validation message");
                    ((Button)results.lookup("#clearButton")).fire();
                    ((ToggleButton)root.lookup("#profileButton")).fire();check(profile.isVisible()&&!results.isVisible(),"Profile navigation");return null;
                });
                await(()->!((Button)profile.lookup("#editProfileButton")).isDisabled());
                check(fx(()->((Label)profile.lookup("#profileName")).getText()).equals("Student"),"Profile loaded");
                long session=UserSession.generation();
                Platform.runLater(()->((Button)profile.lookup("#editProfileButton")).fire());await(()->dialog()!=null);
                fx(()->{((TextField)dialog().lookup("#profileNameField")).clear();((Button)dialog().lookup("#saveProfileButton")).fire();check(!((Label)dialog().lookup("#profileFormMessage")).getText().isEmpty(),"Required name validation");return null;});
                fx(()->{((TextField)dialog().lookup("#profileNameField")).setText("New Student");((TextField)dialog().lookup("#profileUsernameField")).setText("other");((Button)dialog().lookup("#saveProfileButton")).fire();return null;});
                await(()->dialog()!=null&&((Label)dialog().lookup("#profileFormMessage")).getText().contains("already in use"));
                fx(()->{((TextField)dialog().lookup("#profileUsernameField")).setText("newstudent");((TextField)dialog().lookup("#profileContactField")).setText("12345");((Button)dialog().lookup("#saveProfileButton")).fire();return null;});
                await(()->dialog()==null&&((Label)profile.lookup("#profileName")).getText().equals("New Student"));
                check(UserSession.generation()==session,"Profile edit keeps active session");
                byte[] picture=StudentPanelsTest.picture();
                var preview=quizora.controllerAdmin.accountManagementController.class.getDeclaredMethod("previewPicture",byte[].class);preview.setAccessible(true);
                Platform.runLater(()->{try{preview.invoke(profileController,(Object)picture);}catch(Exception e){e.printStackTrace();System.exit(1);}});
                await(()->dialog()!=null&&dialog().lookup("#savePictureButton")!=null);
                fx(()->{((Button)dialog().lookup("#savePictureButton")).fire();return null;});
                await(()->dialog()==null&&((javafx.scene.image.ImageView)profile.lookup("#profileImage")).getImage()!=null);
                fx(()->{((Button)profile.lookup("#refreshAccountButton")).fire();return null;});
                await(()->!((Button)profile.lookup("#editProfileButton")).isDisabled());
                check(fx(()->((Label)profile.lookup("#profileContact")).getText()).equals("12345"),"Profile changes survive refresh");
                fx(()->{((ToggleButton)root.lookup("#dashboardButton")).fire();check(!profile.isVisible()&&!results.isVisible(),"Return to dashboard");UserSession.clear();stage.close();return null;});
                fixture.close();System.out.println("Student panels UI checks passed: navigation, results, search, dates, profile editing, duplicate errors, picture preview/save, refresh.");Platform.exit();
            }catch(Throwable failure){failure.printStackTrace();System.exit(1);}
        },"student-panels-ui-checks").start();
    }
    public static void main(String[]args){launch(args);}
}
