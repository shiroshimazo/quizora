package quizora.controllerTeacher;

import quizora.DAO.TeacherProfileDAO;

/** Teacher profile uses the shared edit and picture-preview workflow. */
public class profileController extends quizora.controllerAdmin.accountManagementController {
    public profileController(){super(new TeacherProfileDAO());}
    @Override protected String profileRole(){return "teacher";}
    @Override protected String accessMessage(){return "Active teacher access is required. Sign in again.";}
}
