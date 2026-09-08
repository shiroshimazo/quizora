package quizora.controllerAdmin;

import java.io.File;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.Region;
import javafx.scene.image.WritableImage;
import javax.imageio.ImageIO;
import quizora.Quizora;
import quizora.auth.UserSession;
import quizora.model.AdminDashboardData;
import quizora.model.AdminDashboardData.Count;
import quizora.model.AdminDashboardData.DailyCount;

/** Renderer fixtures are in memory only; screenshot is explicitly marked as test data. */
public class AdminDashboardViewTest {
    public static void main(String[] args) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.startup(() -> {
            Thread.currentThread().setUncaughtExceptionHandler((thread, error) -> failure.set(error));
            try {
                Quizora.satoshi(14);
                UserSession.clear();
                FXMLLoader loader = new FXMLLoader(Quizora.class.getResource("/Resources/fxml/admin/overview.fxml"));
                Region root = loader.load();
                Scene scene = new Scene(root, 1720, 2000);
                scene.getStylesheets().add(Quizora.class.getResource("/Resources/css/dashboard.css").toExternalForm());
                root.applyCss(); root.layout();
                require(((Button)root.lookup("#refreshButton")).isDisabled(), "Unauthenticated preview blocked");
                adminDashboardController controller = loader.getController();
                LocalDate today = LocalDate.of(2026, 9, 8);
                var emptyDays = java.util.stream.IntStream.range(0,14)
                        .mapToObj(i -> new DailyCount(today.minusDays(13-i), 0)).toList();
                controller.render(new AdminDashboardData(0,0,0,0,0,null,today,List.of(),emptyDays,
                        List.of(new Count("Draft",0),new Count("Published",0),new Count("Closed",0))));
                require(((Label)root.lookup("#averageValue")).getText().equals("—"), "Empty average");
                require(root.lookup("#pieEmptyLabel").isVisible(), "Empty pie message");
                var days = java.util.stream.IntStream.range(0,14)
                        .mapToObj(i -> new DailyCount(today.minusDays(13-i), i*3L)).toList();
                controller.render(new AdminDashboardData(245,18,36,8,273,76.4,today,
                        List.of(new Count("Mathematics",12),new Count("Science",10),
                                new Count("English",8),new Count("History",6)),days,
                        List.of(new Count("Draft",8),new Count("Published",24),new Count("Closed",4))));
                ((Label)root.lookup("#statusLabel")).setText("Visual test fixture · Illustrative values, not database records");
                root.applyCss(); root.layout();
                require(((BarChart<?,?>)root.lookup("#subjectChart")).getData().getFirst().getData().size()==4, "Bar data");
                require(((LineChart<?,?>)root.lookup("#submissionChart")).getData().getFirst().getData().size()==14, "Line data");
                require(((PieChart)root.lookup("#statusChart")).getData().size()==3, "Pie data");
                require(!root.lookup("#pieEmptyLabel").isVisible(), "Populated pie hides empty message");
                require(((Label)root.lookup("#studentsValue")).getText().equals("245"), "KPI rendered");
                for (int width : new int[]{580, 900, 1300}) {
                    root.resize(width, 800);
                    for (int pass=0;pass<4;pass++) { root.applyCss(); root.layout(); }
                    var cards = (javafx.scene.layout.GridPane)root.lookup("#kpiGrid");
                    var charts = (javafx.scene.layout.GridPane)root.lookup("#chartGrid");
                    require(cards.getColumnConstraints().size() == (width < 600 ? 1 : width < 1050 ? 2 : 3),
                            "Cards respond to viewport width");
                    require(charts.getColumnConstraints().size() == (width < 1100 ? 1 : 2),
                            "Charts stack in narrow windows");
                    ScrollPane scroll = (ScrollPane) root;
                    require(scroll.getContent().getLayoutBounds().getWidth() <= scroll.getViewportBounds().getWidth()+1,
                            "No horizontal content overflow");
                }
                root.resize(1720, 2000);
                for (int pass=0;pass<4;pass++) { root.applyCss(); root.layout(); }
                ImageIO.write(SwingFXUtils.fromFXImage(root.snapshot(null,new WritableImage(1720,2000)),null),
                        "png",new File("build/admin-dashboard-preview.png"));
                System.out.println("PASS: empty/populated charts, responsive layouts at 580/900/1300 widths, no horizontal overflow.");
            } catch (Throwable error) { failure.set(error); }
            finally { done.countDown(); }
        });
        done.await();
        Platform.exit();
        if (failure.get()!=null) throw new AssertionError(failure.get());
    }
    private static void require(boolean value,String message) {
        if (!value) throw new AssertionError(message);
    }
}
