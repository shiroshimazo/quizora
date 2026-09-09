package quizora.model;

import java.util.Locale;

/** A current teacher assignment with counts restricted to that teacher's quizzes. */
public record AssignedSubject(long id,String name,String category,String description,long quizzes,long published) {
    public boolean matches(String query) {
        String term=query==null?"":query.strip().toLowerCase(Locale.ROOT);
        return (id+" "+name+" "+category+" "+description).toLowerCase(Locale.ROOT).contains(term);
    }
}
