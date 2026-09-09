package quizora.controllerTeacher;

import java.net.URL;
import java.util.ResourceBundle;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import quizora.DAO.QuizManagementDAO;
import quizora.auth.UserSession;
import quizora.controllerAdmin.QuizEditor;
import quizora.model.*;

public class createQuizController implements Initializable {
    @FXML private Button refreshSubjectsButton,draftButton,publishButton;
    @FXML private Label statusLabel;
    @FXML private StackPane editorPane;
    private QuizEditor editor;
    private boolean busy;
    @Override public void initialize(URL location,ResourceBundle resources) { }
    public void open() { if(editor==null&&!busy)refreshSubjects(); }
    @FXML private void refreshSubjects() {
        if(busy)return;
        var identity=UserSession.current();
        if(identity==null||!"teacher".equals(identity.role())){message("Sign in as a teacher to create quizzes.",true);return;}
        setBusy(true);message("Loading assigned subjects...",false);
        var task=new Task<QuizManagementDAO.Data>() {
            @Override protected QuizManagementDAO.Data call()throws Exception{return new QuizManagementDAO().teacherCreationData(identity);}
        };
        task.setOnSucceeded(e->{
            if(UserSession.current()!=identity){invalidate();return;}
            var data=task.getValue();
            if(editor==null){editor=new QuizEditor(data,null,false,true);editorPane.getChildren().setAll(editor);}
            else {
                @SuppressWarnings("unchecked") var subjects=(ComboBox<QuizChoice>)editor.lookup("#quizSubjectField");
                var selected=subjects.getValue();subjects.getItems().setAll(data.subjects());
                subjects.setValue(selected==null?null:data.subjects().stream().filter(s->s.id()==selected.id()).findFirst().orElse(null));
            }
            setBusy(false);
            draftButton.setDisable(data.subjects().isEmpty());publishButton.setDisable(data.subjects().isEmpty());
            message(data.subjects().isEmpty()?"No assigned subjects. Ask an administrator to assign a subject, then refresh.":"Required fields are marked *. Drafts may have no questions; publishing requires at least one complete question.",false);
        });
        task.setOnFailed(e->{if(UserSession.current()!=identity){invalidate();return;}setBusy(false);message("Could not load subjects. Check your connection and teacher access, then refresh.",true);});
        run(task,"quizora-teacher-subjects");
    }
    @FXML private void saveDraft(){save("draft");}
    @FXML private void publish(){save("published");}
    private void save(String state) {
        if(busy||editor==null)return;
        var identity=UserSession.current();
        if(identity==null||!"teacher".equals(identity.role())){invalidate();return;}
        final QuizChanges changes;final java.util.List<QuizQuestion> questions;
        try {
            var values=editor.changes();
            changes=new QuizChanges(values.title(),values.description(),values.subjectId(),identity.id(),values.minutes(),state);
            questions=editor.questions();
            if(state.equals("published")&&questions.isEmpty())throw new IllegalArgumentException("Add at least one complete question before publishing.");
        }catch(IllegalArgumentException ex){message(ex.getMessage(),true);return;}
        setBusy(true);message(state.equals("draft")?"Saving draft...":"Publishing quiz...",false);
        var task=new Task<QuizRecord>() {
            @Override protected QuizRecord call()throws Exception{return new QuizManagementDAO().createForTeacher(identity,changes,questions);}
        };
        task.setOnSucceeded(e->{
            if(UserSession.current()!=identity){invalidate();return;}
            var result=task.getValue();editor=null;editorPane.getChildren().clear();setBusy(false);
            message("Quiz #"+result.id()+" "+(state.equals("draft")?"saved as a draft":"published")+" ? "+result.title()+". Select Refresh subjects to create another quiz.",false);
        });
        task.setOnFailed(e->{
            if(UserSession.current()!=identity){invalidate();return;}
            setBusy(false);var error=task.getException();
            message(error instanceof IllegalArgumentException||error instanceof SecurityException?error.getMessage():"Could not confirm the save. Your form is preserved. Check quiz records with an administrator before retrying.",true);
        });
        run(task,"quizora-teacher-create-quiz");
    }
    private void invalidate(){editor=null;editorPane.getChildren().clear();setBusy(false);message("Session changed. Sign in as a teacher again.",true);}
    private void setBusy(boolean value){busy=value;refreshSubjectsButton.setDisable(value);editorPane.setDisable(value);draftButton.setDisable(value||editor==null);publishButton.setDisable(value||editor==null);}
    private void message(String text,boolean error){statusLabel.setText(text);statusLabel.getStyleClass().remove("management-error");if(error)statusLabel.getStyleClass().add("management-error");}
    private static void run(Task<?> task,String name){var thread=new Thread(task,name);thread.setDaemon(true);thread.start();}
}
