package quizora.controllerAdmin;
import java.util.*;
import java.util.concurrent.*;
import java.sql.*;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.stage.*;
import quizora.auth.*;
import quizora.DAO.AdminProfileDAO;
import quizora.database.databaseConnection;
import quizora.dashboard.PanelRouter;
import quizora.model.*;
public class AccountProfileTest {
 static Stage stage;
 static <T>T fx(Callable<T> c)throws Exception{FutureTask<T> t=new FutureTask<>(c);Platform.runLater(t);return t.get(15,TimeUnit.SECONDS);}
 static void await(Callable<Boolean> c)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);while(!fx(c)){if(System.nanoTime()>end)throw new AssertionError("UI timeout");Thread.sleep(40);}}
 static Window dialog(String title){return Window.getWindows().stream().filter(w->w instanceof Stage s&&s!=stage&&s.getTitle().equals(title)).findFirst().orElse(null);}
 public static void main(String[] args)throws Exception{
  String tag="profile-"+UUID.randomUUID();long id;
  try(var c=databaseConnection.getConnection();var s=c.prepareStatement("INSERT INTO users(full_name,username,email,password_hash,role) VALUES('Profile Test',?,?,?,'admin')",Statement.RETURN_GENERATED_KEYS)){s.setString(1,tag);s.setString(2,tag+"@example.invalid");s.setString(3,PasswordHasher.hash("Test@123".toCharArray()));s.executeUpdate();try(var r=s.getGeneratedKeys()){r.next();id=r.getLong(1);}}
  var admin=new AuthenticatedUser(id,"Profile Test","admin");var dao=new AdminProfileDAO();
  Platform.startup(()->Platform.setImplicitExit(false));
  try{
   try{dao.load(null);throw new AssertionError("Anonymous allowed");}catch(SecurityException expected){}
   var original=dao.load(admin);var changes=new ProfileChanges("Changed",tag,tag+"@example.invalid","12345");
   try{dao.save(new AuthenticatedUser(id,"Fake","student"),original,changes);throw new AssertionError("Wrong role allowed");}catch(SecurityException expected){}
   try{dao.save(admin,new AdminProfile(id+1,"","","","","",original.createdAt()),changes);throw new AssertionError("Other account edit allowed");}catch(SecurityException expected){}
   try{dao.save(admin,original,new ProfileChanges("Changed","admin",tag+"@example.invalid",""));throw new AssertionError("Duplicate allowed");}catch(SQLException expected){if(expected.getErrorCode()!=1062)throw expected;}
   var updated=dao.save(admin,original,changes);
   try{dao.save(admin,original,changes);throw new AssertionError("Stale allowed");}catch(SQLException expected){if(!"40001".equals(expected.getSQLState()))throw expected;}
   byte[] image;try(var out=new java.io.ByteArrayOutputStream()){javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(20,20,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",out);image=out.toByteArray();}
   for(byte[] bad:new byte[][]{new byte[0],new byte[ProfilePicture.MAX_BYTES+1],"not an image".getBytes()}){try{ProfilePicture.validate(bad);throw new AssertionError("Bad image allowed");}catch(IllegalArgumentException expected){}}
   var picture=dao.picture(admin,updated,image);if(!Arrays.equals(Base64.getDecoder().decode(picture.picture()),image))throw new AssertionError("Picture not persisted");
   if(new AuthenticationService().authenticate(tag,"Test@123".toCharArray()).isEmpty())throw new AssertionError("Password changed");
   fx(()->{UserSession.signIn(admin);stage=new Stage();PanelRouter.open(stage);((ToggleButton)stage.getScene().lookup("#accountManagementButton")).fire();return null;});await(()->((Label)stage.getScene().lookup("#accountMessage")).getText().equals("Your profile is up to date."));
   Platform.runLater(()->((Button)stage.getScene().lookup("#editProfileButton")).fire());await(()->dialog("Edit profile")!=null);
   fx(()->{var scene=dialog("Edit profile").getScene();((TextField)scene.lookup("#profileNameField")).clear();((Button)scene.lookup("#saveProfileButton")).fire();if(((Label)scene.lookup("#profileFormMessage")).getText().isBlank())throw new AssertionError("Missing validation");((TextField)scene.lookup("#profileNameField")).setText("Updated UI Profile");((Button)scene.lookup("#saveProfileButton")).fire();return null;});await(()->dialog("Edit profile")==null);
   fx(()->{if(!((Label)stage.getScene().lookup("#profileName")).getText().equals("Updated UI Profile"))throw new AssertionError("Display not updated");if(!UserSession.current().fullName().equals("Updated UI Profile"))throw new AssertionError("Session name not updated");return null;});
   Platform.runLater(()->((Button)stage.getScene().lookup("#editProfileButton")).fire());await(()->dialog("Edit profile")!=null);
   fx(()->{var pane=(DialogPane)dialog("Edit profile").getScene().getRoot();((TextField)pane.lookup("#profileNameField")).setText("Not saved");((Button)pane.lookupButton(ButtonType.CANCEL)).fire();return null;});await(()->dialog("Edit profile")==null);
   if(!dao.load(UserSession.current()).name().equals("Updated UI Profile"))throw new AssertionError("Cancel saved");
   fx(()->{var root=(ScrollPane)stage.getScene().lookup("#accountScroll");for(int width:new int[]{580,1100}){root.resize(width,850);for(int i=0;i<4;i++){root.applyCss();root.layout();}if(root.getContent().getLayoutBounds().getWidth()>root.getViewportBounds().getWidth()+1)throw new AssertionError("Overflow");}return null;});
   var savedProfile=dao.load(UserSession.current());
   accountManagementController pictureController=fx(()->{
    var loader=new javafx.fxml.FXMLLoader(AccountProfileTest.class.getResource("/Resources/fxml/admin/accountManager.fxml"));
    javafx.scene.Parent root=loader.load();root.setVisible(true);root.setManaged(true);stage.setScene(new javafx.scene.Scene(root,900,850));
    accountManagementController controller=loader.getController();controller.display(savedProfile);return controller;
   });
   Platform.runLater(()->pictureController.previewPicture(image));await(()->dialog("Preview profile picture")!=null);
   fx(()->{var pane=(DialogPane)dialog("Preview profile picture").getScene().getRoot();((Button)pane.lookupButton(ButtonType.CANCEL)).fire();return null;});await(()->dialog("Preview profile picture")==null);
   Platform.runLater(()->pictureController.previewPicture(image));await(()->dialog("Preview profile picture")!=null);
   fx(()->{((Button)dialog("Preview profile picture").getScene().lookup("#savePictureButton")).fire();return null;});await(()->dialog("Preview profile picture")==null);
   fx(()->{if(!((Label)stage.getScene().lookup("#accountMessage")).getText().equals("Profile picture updated."))throw new AssertionError("Picture save message");return null;});
   System.out.println("PASS: own-admin access, duplicates, stale updates, image validation/persistence, preserved login, profile modal validation/save/cancel, session name and responsive layout.");
  }finally{fx(()->{if(stage!=null)stage.close();UserSession.clear();return null;});Platform.exit();try(var c=databaseConnection.getConnection();var s=c.prepareStatement("DELETE FROM users WHERE user_id=? AND username=?")){s.setLong(1,id);s.setString(2,tag);s.executeUpdate();}}
 }
}
