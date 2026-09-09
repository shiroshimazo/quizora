package quizora.model;
public record SubjectChanges(String name,String category,String description){
 public SubjectChanges{
  name=name==null?"":name.strip();category=category==null?"":category.strip();description=description==null?"":description.strip();
  if(name.isEmpty()||name.length()>100)throw new IllegalArgumentException("Subject name is required (up to 100 characters).");
  if(category.length()>100)throw new IllegalArgumentException("Category must be at most 100 characters.");
  if(description.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>60000)throw new IllegalArgumentException("Description is too long.");
 }
}
