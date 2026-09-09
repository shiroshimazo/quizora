package quizora.model;
import java.time.LocalDateTime;
import java.util.Locale;
public record SubjectRecord(long id,String name,String category,String description,LocalDateTime createdAt,boolean archived,int quizzes,int teachers) {
 public String status(){return archived?"Archived":"Active";}
 public boolean matches(String query,String status,String categoryFilter){
  String term=query==null?"":query.strip().toLowerCase(Locale.ROOT);
  return (status==null||status.equals("All statuses")||status.equals(status()))
    &&(categoryFilter==null||category.equalsIgnoreCase(categoryFilter))
    &&(id+" "+name+" "+category+" "+description).toLowerCase(Locale.ROOT).contains(term);
 }
}
