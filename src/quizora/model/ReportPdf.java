package quizora.model;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Single-page summary PDF using only fixed ASCII labels and numeric report values. */
public final class ReportPdf {
 private ReportPdf() {}
 public static void write(Path target, ReportData data, String type) throws IOException {
  List<String> lines=new ArrayList<>();lines.add("Quizora - "+type);lines.add("Generated: "+data.generatedAt().toString().replace('T',' ')+" (database local time)");
  lines.add("Scope: All time, including archived records");lines.add("");
  for(var metric:data.metrics(type))lines.add(metric.label()+": "+metric.value());
  lines.add("");
  if(type.equals("Quiz statistics")){
   lines.add("Scores: submitted attempts with finalized results only.");
   lines.add("Average: mean percentage per scored attempt, including retakes.");
   lines.add("Pass rate: scored attempts at or above "+data.passThreshold()+"%.");
   lines.add("N/A means there are no scored submissions.");
  }else lines.add("Active/inactive exclude archived accounts; archived is a separate count.");
  StringBuilder stream=new StringBuilder("BT /F1 12 Tf 50 790 Td 22 TL\n");
  for(String line:lines)stream.append('(').append(line.replace("\\","\\\\").replace("(","\\(").replace(")","\\)")).append(") Tj T*\n");
  stream.append("ET");byte[] content=stream.toString().getBytes(StandardCharsets.US_ASCII);
  String[] objects={"<< /Type /Catalog /Pages 2 0 R >>","<< /Type /Pages /Kids [3 0 R] /Count 1 >>","<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>","<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>","<< /Length "+content.length+" >>\nstream\n"+stream+"\nendstream"};
  ByteArrayOutputStream out=new ByteArrayOutputStream();out.write("%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII));List<Integer> offsets=new ArrayList<>();
  for(int i=0;i<objects.length;i++){offsets.add(out.size());out.write(((i+1)+" 0 obj\n"+objects[i]+"\nendobj\n").getBytes(StandardCharsets.US_ASCII));}
  int xref=out.size();out.write("xref\n0 6\n0000000000 65535 f \n".getBytes(StandardCharsets.US_ASCII));
  for(int offset:offsets)out.write(String.format(Locale.ROOT,"%010d 00000 n \n",offset).getBytes(StandardCharsets.US_ASCII));
  out.write(("trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n"+xref+"\n%%EOF\n").getBytes(StandardCharsets.US_ASCII));
  Files.write(target,out.toByteArray());
 }
}
