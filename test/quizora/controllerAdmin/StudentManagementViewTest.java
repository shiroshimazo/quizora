package quizora.controllerAdmin;

import java.sql.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import quizora.Quizora;
import quizora.auth.*;
import quizora.dashboard.PanelRouter;
import quizora.database.databaseConnection;
import quizora.model.StudentRecord;

public class StudentManagementViewTest {
    private static Stage stage;
    private static final AtomicReference<Throwable> errors=new AtomicReference<>();
    private static <T>T fx(Callable<T> work)throws Exception{
        FutureTask<T> task=new FutureTask<>(work);Platform.runLater(task);return task.get(15,TimeUnit.SECONDS);
    }
    private static void await(Callable<Boolean> check)throws Exception{
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
        while(!fx(check)){if(System.nanoTime()>end)throw new AssertionError("UI wait timed out");Thread.sleep(40);}
    }
    private static Window dialog(String title){
        return Window.getWindows().stream().filter(w->w instanceof Stage s && s!=stage && s.getTitle().startsWith(title))
                .findFirst().orElse(null);
    }
    private static Button rowButton(long id,String selector){
        for(Node node:stage.getScene().getRoot().lookupAll(selector)){
            Node parent=node.getParent();
            while(parent!=null && !(parent instanceof TableCell))parent=parent.getParent();
            if(parent instanceof TableCell<?,?> cell && cell.getItem() instanceof StudentRecord s && s.id()==id)return (Button)node;
        }
        throw new AssertionError("Row button missing: "+selector);
    }
    @SuppressWarnings("unchecked")
    public static void main(String[]args)throws Exception{
        var admin=new AuthenticationService().authenticate("admin","Admin@123".toCharArray()).orElseThrow();
        String tag="ui-"+java.util.UUID.randomUUID();
        long fixture;
        try(var c=databaseConnection.getConnection();
            var s=c.prepareStatement("INSERT INTO users(full_name,username,email,password_hash,role) VALUES('UI Student',?,?,?,'student')",Statement.RETURN_GENERATED_KEYS)){
            s.setString(1,tag);s.setString(2,tag+"@example.invalid");s.setString(3,PasswordHasher.hash("Test@123".toCharArray()));
            s.executeUpdate();try(var keys=s.getGeneratedKeys()){keys.next();fixture=keys.getLong(1);}
        }
        Platform.startup(()->{Platform.setImplicitExit(false);Thread.currentThread().setUncaughtExceptionHandler((t,e)->errors.set(e));});
        try{
            fx(()->{
                Quizora.satoshi(14);UserSession.signIn(admin);stage=new Stage();PanelRouter.open(stage);
                ((ToggleButton)stage.getScene().lookup("#studentManagementButton")).fire();return null;
            });
            await(()->((Label)stage.getScene().lookup("#studentMessage")).getText().equals("Student records are up to date."));
            String total=fx(()->((Label)stage.getScene().lookup("#totalStudentValue")).getText());
            fx(()->{
                ((TextField)stage.getScene().lookup("#studentSearch")).setText(tag);
                TableView<StudentRecord> table=(TableView<StudentRecord>)stage.getScene().lookup("#studentTable");
                require(table.getItems().size()==1,"Search returns fixture only");
                require(((Label)stage.getScene().lookup("#totalStudentValue")).getText().equals(total),"Filters preserve KPI totals");
                ((ComboBox<String>)stage.getScene().lookup("#studentStatusFilter")).setValue("Archived");
                require(table.getItems().isEmpty(),"Combined search and status");
                ((ComboBox<String>)stage.getScene().lookup("#studentStatusFilter")).setValue("Active");
                require(table.getItems().size()==1,"Active filter");
                table.applyCss();table.layout();return null;
            });
            Platform.runLater(()->rowButton(fixture,"#editStudentButton").fire());
            await(()->dialog("Edit student")!=null);
            fx(()->{
                Window edit=dialog("Edit student");
                ((TextField)edit.getScene().lookup("#nameField")).clear();
                ((Button)edit.getScene().lookup("#saveStudentButton")).fire();
                require(!((Label)edit.getScene().lookup("#editMessage")).getText().isBlank(),"Validation message");
                ((TextField)edit.getScene().lookup("#nameField")).setText("Edited UI Student");
                ((ComboBox<String>)edit.getScene().lookup("#editStatusFilter")).setValue("Inactive");
                ((Button)edit.getScene().lookup("#saveStudentButton")).fire();return null;
            });
            await(()->dialog("Edit student")==null);
            fx(()->{
                TableView<StudentRecord> table=(TableView<StudentRecord>)stage.getScene().lookup("#studentTable");
                require(table.getItems().isEmpty(),"Edited inactive student leaves active filter");
                ((ComboBox<String>)stage.getScene().lookup("#studentStatusFilter")).setValue("Inactive");
                require(table.getItems().size()==1 && table.getItems().getFirst().name().equals("Edited UI Student"),"Edited data displayed");
                table.applyCss();table.layout();return null;
            });
            Platform.runLater(()->rowButton(fixture,"#editStudentButton").fire());
            await(()->dialog("Edit student")!=null);
            fx(()->{
                Window edit=dialog("Edit student");
                ((TextField)edit.getScene().lookup("#nameField")).setText("Must not save");
                ((Button)edit.getScene().lookup("#cancelStudentEditButton")).fire();return null;
            });
            await(()->dialog("Edit student")==null);
            Platform.runLater(()->rowButton(fixture,"#archiveStudentButton").fire());
            await(()->dialog("Archive student")!=null);
            fx(()->{
                DialogPane pane=(DialogPane)dialog("Archive student").getScene().getRoot();
                ((Button)pane.lookupButton(ButtonType.CANCEL)).fire();return null;
            });
            await(()->dialog("Archive student")==null);
            fx(()->{
                var table=(TableView<StudentRecord>)stage.getScene().lookup("#studentTable");
                require(!table.getItems().getFirst().archived(),"Archive cancel preserves record");
                return null;
            });
            Platform.runLater(()->rowButton(fixture,"#archiveStudentButton").fire());
            await(()->dialog("Archive student")!=null);
            fx(()->{
                DialogPane pane=(DialogPane)dialog("Archive student").getScene().getRoot();
                ButtonType confirm=pane.getButtonTypes().stream().filter(b->b.getButtonData()==ButtonBar.ButtonData.OK_DONE).findFirst().orElseThrow();
                ((Button)pane.lookupButton(confirm)).fire();return null;
            });
            await(()->((Label)stage.getScene().lookup("#studentMessage")).getText().startsWith("Student archived."));
            fx(()->{
                ((ComboBox<String>)stage.getScene().lookup("#studentStatusFilter")).setValue("Archived");
                var table=(TableView<StudentRecord>)stage.getScene().lookup("#studentTable");
                require(table.getItems().size()==1 && table.getItems().getFirst().archived(),"Archived filter");
                require(table.getItems().getFirst().name().equals("Edited UI Student"),"Edit cancel did not save");
                table.applyCss();table.layout();
                require(rowButton(fixture,"#editStudentButton").isDisabled(),"Archived edit disabled");
                require(rowButton(fixture,"#archiveStudentButton").isDisabled(),"Repeated archive disabled");
                ScrollPane root=(ScrollPane)stage.getScene().lookup("#studentScroll");
                for(int width:new int[]{580,1100}){
                    root.resize(width,800);
                    for(int i=0;i<4;i++){root.applyCss();root.layout();}
                    require(root.getContent().getLayoutBounds().getWidth()<=root.getViewportBounds().getWidth()+1,"No outer horizontal overflow");
                    var grid=(GridPane)root.lookup("#studentKpiGrid");
                    require(grid.getColumnConstraints().size()==(width<650?1:3),"KPI reflow");
                }
                ((TextField)stage.getScene().lookup("#studentSearch")).clear();
                ((ComboBox<String>)stage.getScene().lookup("#studentStatusFilter")).setValue("All statuses");
                root.resize(1140,850);for(int i=0;i<4;i++){root.applyCss();root.layout();}
                javax.imageio.ImageIO.write(javafx.embed.swing.SwingFXUtils.fromFXImage(root.snapshot(null,null),null),
                        "png",new java.io.File("build/student-management-preview.png"));
                return null;
            });
            if(errors.get()!=null)throw new AssertionError(errors.get());
            System.out.println("PASS: real UI search/filter, stable KPIs, edit validation/save/cancel, archive confirm/cancel, disabled archived actions and responsive layout.");
        }finally{
            fx(()->{if(stage!=null)stage.close();UserSession.clear();return null;});
            Platform.exit();
            try(var c=databaseConnection.getConnection();var s=c.prepareStatement("DELETE FROM users WHERE user_id=? AND username=?")){
                s.setLong(1,fixture);s.setString(2,tag);s.executeUpdate();
            }
        }
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
