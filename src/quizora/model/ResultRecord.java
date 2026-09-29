/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

/**
 *
 * @author Jeremy
 */

package quizora.model;
import java.time.LocalDateTime;
public record ResultRecord(long attemptId,long studentId,String student,String username,long quizId,String quiz,long subjectId,String subject,long score,long total,LocalDateTime submittedAt){
 public double percentage(){return 100.0*score/total;}
 public boolean passed(int threshold){return percentage()>=threshold;}
}
