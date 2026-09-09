package quizora.controllerAdmin;

import java.util.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import quizora.DAO.QuizManagementDAO;
import quizora.model.*;

/** Modal form content, kept separate from persistence and directory behavior. */
public final class QuizEditor extends ScrollPane {
    private final TextField title=new TextField(),minutes=new TextField("30");
    private final TextArea description=new TextArea();
    private final ComboBox<QuizChoice> subject=new ComboBox<>(),teacher=new ComboBox<>();
    private final ComboBox<String> status=new ComboBox<>();
    private final VBox questions=new VBox(14);
    private final List<QuestionForm> forms=new ArrayList<>();
    private final QuizDetails original;
    public QuizEditor(QuizManagementDAO.Data data,QuizDetails original,boolean readOnly){
        this(data, original, readOnly, false);
    }
    public QuizEditor(QuizManagementDAO.Data data,QuizDetails original,boolean readOnly,boolean teacherCreation){
        this.original=original;
        var content=new VBox(10);content.getStyleClass().add("student-editor");setContent(content);setFitToWidth(true);setPrefViewportWidth(620);setPrefViewportHeight(560);
        title.setId("quizTitleField");minutes.setId("quizMinutesField");description.setId("quizDescriptionField");subject.setId("quizSubjectField");teacher.setId("quizTeacherField");status.setId("quizStateField");
        subject.getItems().setAll(data.subjects());teacher.getItems().setAll(data.teachers());status.getItems().setAll("draft","published","closed");status.setValue("draft");
        subject.setMaxWidth(Double.MAX_VALUE);teacher.setMaxWidth(Double.MAX_VALUE);status.setMaxWidth(Double.MAX_VALUE);description.setPrefRowCount(2);description.setWrapText(true);
        field(content,"Title *",title);field(content,"Description",description);field(content,"Subject *",subject);if(!teacherCreation)field(content,"Teacher *",teacher);else teacher.setValue(data.teachers().getFirst());field(content,"Time limit (minutes) *",minutes);if(!teacherCreation)field(content,"Status *",status);
        var heading=new Label("Questions");heading.getStyleClass().add("chart-heading");content.getChildren().addAll(heading,questions);
        Button add=new Button("Add question");add.setId("addQuestionButton");add.getStyleClass().add("row-action");add.setOnAction(e->add(null));content.getChildren().add(add);
        if(original!=null){var q=original.quiz();title.setText(q.title());description.setText(q.description());minutes.setText(""+q.minutes());status.setValue(q.state());
            subject.setValue(choice(subject,q.subjectId(),q.subject()));teacher.setValue(choice(teacher,q.teacherId(),q.teacher()));original.questions().forEach(this::add);
            if(q.attempts()>0){subject.setDisable(true);teacher.setDisable(true);minutes.setDisable(true);questions.setDisable(true);add.setDisable(true);status.getItems().remove("draft");
                Label note=new Label("This quiz has student attempts. Questions, scoring, time limit and assignments are locked.");note.setWrapText(true);content.getChildren().add(0,note);}
        }
        if(data.subjects().isEmpty()||data.teachers().isEmpty()){
            Label note=new Label("Creating a quiz requires an existing subject and an active teacher.");note.setWrapText(true);content.getChildren().add(0,note);
        }
        if(readOnly)content.setDisable(true);
    }
    private QuizChoice choice(ComboBox<QuizChoice> box,long id,String name){return box.getItems().stream().filter(c->c.id()==id).findFirst().orElse(new QuizChoice(id,name));}
    private static void field(VBox box,String text,Control control){Label label=new Label(text);label.setLabelFor(control);control.setAccessibleText(text);control.getStyleClass().add("management-input");box.getChildren().addAll(label,control);}
    private void add(QuizQuestion q){QuestionForm form=new QuestionForm(q);forms.add(form);questions.getChildren().add(form);}
    public QuizChanges changes(){
        int time;try{time=Integer.parseInt(minutes.getText().strip());}catch(NumberFormatException e){throw new IllegalArgumentException("Enter a whole number for the time limit.");}
        return new QuizChanges(title.getText(),description.getText(),subject.getValue()==null?0:subject.getValue().id(),teacher.getValue()==null?0:teacher.getValue().id(),time,status.getValue());
    }
    public List<QuizQuestion> questions(){return forms.stream().map(QuestionForm::value).toList();}
    private final class QuestionForm extends VBox {
        final long id;final TextArea text=new TextArea();final TextField a=new TextField(),b=new TextField(),c=new TextField(),d=new TextField(),points=new TextField("1");final ComboBox<String> answer=new ComboBox<>();
        QuestionForm(QuizQuestion q){
            id=q==null?0:q.id();setSpacing(8);getStyleClass().add("chart-card");text.setPrefRowCount(2);text.setWrapText(true);
            text.setId("questionTextField");a.setId("optionAField");b.setId("optionBField");c.setId("optionCField");d.setId("optionDField");answer.setId("correctAnswerField");points.setId("questionPointsField");
            field(this,"Question *",text);field(this,"Option A *",a);field(this,"Option B *",b);field(this,"Option C *",c);field(this,"Option D *",d);answer.getItems().setAll("A","B","C","D");field(this,"Correct answer *",answer);field(this,"Points *",points);
            Button remove=new Button("Remove question");remove.getStyleClass().add("row-action");remove.setOnAction(e->{forms.remove(this);questions.getChildren().remove(this);});getChildren().add(remove);
            if(q!=null){text.setText(q.text());a.setText(q.a());b.setText(q.b());c.setText(q.c());d.setText(q.d());answer.setValue(q.answer());points.setText(""+q.points());}
        }
        QuizQuestion value(){int value;try{value=Integer.parseInt(points.getText().strip());}catch(NumberFormatException e){throw new IllegalArgumentException("Enter whole-number points for each question.");}return new QuizQuestion(id,text.getText(),a.getText(),b.getText(),c.getText(),d.getText(),answer.getValue(),value);}
    }
}
