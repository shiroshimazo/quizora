package quizora.controllerAuthentication;

import javafx.application.*;
import javafx.fxml.FXMLLoader;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.stage.*;
import quizora.auth.PasswordRecoveryService.*;

/** Exercises the unknown-email dialog without database or email delivery. */
public class RecoverySignUpUiTest extends Application {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private void choose(Stage owner, String text){
        Platform.runLater(()->{
            try{
                DialogPane pane=Window.getWindows().stream().filter(w->w!=owner&&w.isShowing())
                    .map(w->w.getScene().getRoot()).filter(n->n instanceof DialogPane)
                    .map(n->(DialogPane)n).findFirst().orElseThrow();
                ButtonType type=pane.getButtonTypes().stream().filter(b->b.getText().equals(text)).findFirst().orElseThrow();
                ((Button)pane.lookupButton(type)).fire();
            }catch(Throwable error){error.printStackTrace();System.exit(1);}
        });
    }
    @Override public void start(Stage stage){
        try{
            FXMLLoader loader=new FXMLLoader(getClass().getResource("/Resources/fxml/authentication/forgotPassword.fxml"));
            Parent recovery=loader.load();stage.setScene(new Scene(recovery));stage.show();
            ((TextField)recovery.lookup("#emailField")).setText(" new@example.com ");
            var failure=forgotPasswordController.class.getDeclaredMethod("showFailure",Throwable.class);failure.setAccessible(true);
            var unknown=new RecoveryException(Reason.UNKNOWN_EMAIL,"No account found with this email. Would you like to sign up?");
            choose(stage,"Cancel");failure.invoke(loader.getController(),unknown);
            check(stage.getScene().getRoot()==recovery,"Cancel stays on recovery");
            choose(stage,"Sign up");failure.invoke(loader.getController(),unknown);
            Parent registration=stage.getScene().getRoot();
            check(registration.lookup("#createAccountButton")!=null,"Sign up opens registration");
            check(((TextField)registration.lookup("#emailField")).getText().equals("new@example.com"),"Entered email carried over");
            check(stage.getTitle().equals("Quizora - Create Account"),"Registration title");
            ((Hyperlink)registration.lookup("#signInLink")).fire();
            check(stage.getScene().getRoot().lookup("#loginButton")!=null,"Registration can return to Login");
            System.out.println("Recovery sign-up checks passed: Cancel, Sign up, email prefill, Login navigation.");Platform.exit();
        }catch(Throwable error){error.printStackTrace();System.exit(1);}
    }
    public static void main(String[]args){launch(args);}
}
