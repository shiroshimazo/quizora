package quizora.controllerTeacher;

import java.sql.SQLException;
import java.util.List;
import quizora.DAO.ResultsDAO;
import quizora.auth.AuthenticatedUser;
import quizora.model.ResultRecord;

/** Shares the results presentation; the DAO separately enforces teacher ownership. */
public class studentResultsController extends quizora.controllerAdmin.resultsController {
    @Override protected List<ResultRecord> loadResults(AuthenticatedUser identity)throws SQLException {
        return new ResultsDAO().loadForTeacher(identity);
    }
    @Override protected String accessMessage(){return "Active teacher access is required. Sign in again.";}
}
