package com.ees.eval.scratch;

import org.apache.poi.xddf.usermodel.chart.XDDFDoughnutChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFChartData;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class CheckMethods {
    public static void main(String[] args) {
        try {
            Class<?> seriesClass = XDDFDoughnutChartData.Series.class;
            System.out.println("--- XDDFDoughnutChartData.Series Info ---");
            printClassInfo(seriesClass);

            System.out.println("\n--- XDDFChartData.Series (Superclass) Info ---");
            printClassInfo(XDDFChartData.Series.class);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void printClassInfo(Class<?> clazz) {
        System.out.println("Class: " + clazz.getName());
        
        System.out.println("[Fields]");
        for (Field f : clazz.getDeclaredFields()) {
            System.out.println("  " + f.getType().getSimpleName() + " " + f.getName());
        }

        System.out.println("[Methods]");
        for (Method m : clazz.getDeclaredMethods()) {
            System.out.println("  " + m.getReturnType().getSimpleName() + " " + m.getName());
        }
    }
}
