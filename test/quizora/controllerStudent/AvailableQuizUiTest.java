package quizora.controllerStudent;

import java.util.List;
import javafx.application.*;
import javafx.fxml.FXMLLoader;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.stage.Stage;
import quizora.DAO.AvailableQuizDAO.Quiz;

public class AvailableQuizUiTest extends Application {
    private static void check(boolean ok,String message) { if (!ok) throw new AssertionError(message); }
    @Override public void start(Stage stage) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/Resources/fxml/student/studentDashbaord.fxml"));
            Parent root = loader.load();
            stage.setScene(new Scene(root,1280,850)); stage.show(); root.applyCss(); root.layout();
            Node content = root.lookup("#availableQuizzesContent");
            check(!content.isVisible() && !content.isManaged(),"Catalogue initially hidden");
            ((ToggleButton)root.lookup("#availableQuizzesButton")).fire();
            check(content.isVisible() && content.isManaged(),"Catalogue navigation");
            check(!((Node)loader.getNamespace().get("dashboardContent")).isVisible(),"Overview hidden");
            var controller = loader.getNamespace().get("availableQuizzesContentController");
            var render = availableQuizzesController.class.getDeclaredMethod("render",List.class); render.setAccessible(true);
            var quizzes = List.of(new Quiz(1,"Algebra basics","Math","Alex Rivera","Practice linear equations.",10,20,10,0,false),
                    new Quiz(2,"Biology review","Science","Sam Lee","Review cells and ecosystems.",15,30,30,2,false),
                    new Quiz(3,"Geometry practice","Math","Alex Rivera","Angles and shapes.",8,10,16,0,true));
            render.invoke(controller,quizzes);
            TableView<?> table = (TableView<?>)content.lookup("#quizTable");
            check(table.getItems().size()==3,"All quizzes visible");
            TextField search = (TextField)content.lookup("#searchField");
            search.setText(" ALEX "); check(table.getItems().size()==2,"Case-insensitive teacher search"); search.clear();
            ComboBox<String> subjects = (ComboBox<String>)content.lookup("#subjectFilter");
            subjects.setValue("Science"); check(table.getItems().size()==1,"Subject filter"); subjects.setValue("All subjects");
            ComboBox<String> progress = (ComboBox<String>)content.lookup("#progressFilter");
            progress.setValue("In progress"); check(table.getItems().size()==1,"Progress filter"); progress.setValue("All progress");
            ComboBox<String> sort = (ComboBox<String>)content.lookup("#sortFilter");
            sort.setValue("Shortest first"); check(((Quiz)table.getItems().getFirst()).id()==3,"Duration sort");
            table.getSelectionModel().select(0);
            check(((Label)content.lookup("#detailDescription")).getText().equals("Angles and shapes."),"Selected quiz details");
            search.setText("no such quiz"); check(table.getItems().isEmpty(),"No matching quizzes");
            check(((Label)content.lookup("#detailTitle")).getText().equals("Select a quiz"),"Clear stale details");
            search.clear(); sort.setValue("Recently updated");
            table.getSelectionModel().select(0); root.applyCss(); root.layout();
            stage.setWidth(1000); root.applyCss(); root.layout();
            ((ToggleButton)root.lookup("#dashboardButton")).fire(); check(!content.isVisible(),"Return to overview");
            System.out.println("Available quiz UI checks passed: navigation, search, filters, sort, details, empty state, resize.");
            Platform.exit();
        } catch(Throwable error) { error.printStackTrace(); System.exit(1); }
    }
    public static void main(String[] args) { launch(args); }
}
