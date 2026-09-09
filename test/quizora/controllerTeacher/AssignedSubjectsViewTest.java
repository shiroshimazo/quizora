package quizora.controllerTeacher;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import quizora.Quizora;
import quizora.model.AssignedSubject;

public class AssignedSubjectsViewTest {
    public static void main(String[] args)throws Exception {
        var done=new CountDownLatch(1);var failure=new AtomicReference<Throwable>();
        Platform.startup(()->{try {
            Quizora.satoshi(14);var loader=new FXMLLoader(Quizora.class.getResource("/Resources/fxml/teacher/assignedSubjects.fxml"));
            ScrollPane root=loader.load();root.setVisible(true);root.setManaged(true);var scene=new Scene(root,1000,900);
            scene.getStylesheets().add(Quizora.class.getResource("/Resources/css/dashboard.css").toExternalForm());
            assignedSubjectsController controller=loader.getController();root.applyCss();root.layout();
            controller.render(List.of());require(((Label)root.lookup("#subjectsValue")).getText().equals("0"),"Empty KPI");
            controller.render(List.of(new AssignedSubject(1,"Mathematics","STEM","Fractions and ratios",4,2),new AssignedSubject(2,"English","Languages","",0,0)));
            require(((Label)root.lookup("#quizzesValue")).getText().equals("4"),"Quiz total");
            @SuppressWarnings("unchecked") var table=(TableView<AssignedSubject>)root.lookup("#subjectTable");
            table.getSelectionModel().selectFirst();require(((Label)root.lookup("#detailDescription")).getText().equals("Fractions and ratios"),"Selected details");
            var search=(TextField)root.lookup("#subjectSearch");search.setText("LANGUAGES");require(table.getItems().size()==1,"Category search");
            table.getSelectionModel().selectFirst();require(((Label)root.lookup("#detailDescription")).getText().startsWith("No description"),"Empty description");
            search.setText("no match");require(table.getItems().isEmpty(),"No-match state");
            require(((Label)root.lookup("#subjectsValue")).getText().equals("2"),"Search preserves overall KPIs");search.clear();
            for(int width:new int[]{580,900,1300}){root.resize(width,800);for(int i=0;i<4;i++){root.applyCss();root.layout();}require(root.getContent().getLayoutBounds().getWidth()<=root.getViewportBounds().getWidth()+1,"No horizontal content overflow");require(((GridPane)root.lookup("#kpiGrid")).getColumnConstraints().size()==(width<600?1:width<1000?2:3),"Responsive cards");}
            quizora.auth.UserSession.clear();controller.refresh();require(table.getItems().isEmpty()&&((Label)root.lookup("#subjectsValue")).getText().equals("?"),"Denied access clears records");
            System.out.println("PASS: empty/populated subjects, search, details, KPI scope, responsive layout and denied-access state.");
        }catch(Throwable e){failure.set(e);}finally{done.countDown();}});
        done.await();Platform.exit();if(failure.get()!=null)throw new AssertionError(failure.get());
    }
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
