/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

/**
 *
 * @author Jeremy
 */

package quizora.model;

import java.time.LocalDate;
import java.util.List;

public record QuizStatisticsData(LocalDate today,List<Summary> quizzes,List<Daily> submissionsByDay) {
    public record Summary(long id,String title,String subject,String status,boolean archived,long attempts,long submitted,long scored,Double average) {
        @Override public String toString(){return "#"+id+" - "+title+(archived?" (Archived)":"");}
    }
    public record Daily(long quizId,LocalDate date,long count) { }
}
