package quizora.controllerTeacher;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import quizora.Quizora;
import quizora.DAO.QuizManagementDAO;
import quizora.controllerAdmin.QuizEditor;
import quizora.model.*;

public class CreateQuizViewTest {
    public static void main(String[] args)throws Exception {
        var done=new CountDownLatch(1);var failure=new AtomicReference<Throwable>();
        Platform.startup(()->{try {
            Quizora.satoshi(14);
            var data=new QuizManagementDAO.Data(List.of(),List.of(new QuizChoice(7,"Mathematics")),List.of(new QuizChoice(9,"Teacher")));
            var editor=new QuizEditor(data,null,false,true);var scene=new Scene(editor,650,700);
            scene.getStylesheets().add(Quizora.class.getResource("/Resources/css/dashboard.css").toExternalForm());
            editor.applyCss();editor.layout();
            require(editor.lookup("#quizTeacherField")==null&&editor.lookup("#quizStateField")==null,"Ownership and status controls hidden");
            ((TextField)editor.lookup("#quizTitleField")).setText("Fractions");
            @SuppressWarnings("unchecked") var subjects=(ComboBox<QuizChoice>)editor.lookup("#quizSubjectField");subjects.setValue(data.subjects().getFirst());
            require(editor.changes().teacherId()==9&&editor.changes().subjectId()==7,"Automatic ownership and selected subject");
            require(editor.questions().isEmpty(),"New draft starts empty");
            ((Button)editor.lookup("#addQuestionButton")).fire();editor.applyCss();editor.layout();
            try{editor.questions();throw new AssertionError("Incomplete question accepted");}catch(IllegalArgumentException expected){}
            ((TextArea)editor.lookup("#questionTextField")).setText("One half plus one half?");
            for(String option:List.of("A","B","C","D"))((TextField)editor.lookup("#option"+option+"Field")).setText(option);
            @SuppressWarnings("unchecked") var answer=(ComboBox<String>)editor.lookup("#correctAnswerField");answer.setValue("A");
            require(editor.questions().size()==1&&editor.questions().getFirst().points()==1,"Question captured");
            for(int width:new int[]{400,650,1000}){editor.resize(width,700);editor.applyCss();editor.layout();require(editor.getContent().getLayoutBounds().getWidth()<=editor.getViewportBounds().getWidth()+1,"No horizontal overflow");}
            var adminEditor=new QuizEditor(data,null,false);new Scene(adminEditor);adminEditor.applyCss();adminEditor.layout();
            require(adminEditor.lookup("#quizTeacherField")!=null&&adminEditor.lookup("#quizStateField")!=null,"Admin controls preserved");
            System.out.println("PASS: teacher editor, ownership, validation, question fields, responsive widths and admin editor compatibility.");
        }catch(Throwable e){failure.set(e);}finally{done.countDown();}});
        done.await();Platform.exit();if(failure.get()!=null)throw new AssertionError(failure.get());
    }
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
