package quizora.controllerStudent;

import quizora.DAO.StudentProfileDAO;

/** Student profile uses the shared validated edit and picture-preview workflow. */
public class profileController extends quizora.controllerAdmin.accountManagementController {
    public profileController() { this(new StudentProfileDAO()); }
    public profileController(StudentProfileDAO dao) { super(dao); }
    @Override protected String profileRole() { return "student"; }
    @Override protected String accessMessage() { return "Active student access is required. Sign in again."; }
}
