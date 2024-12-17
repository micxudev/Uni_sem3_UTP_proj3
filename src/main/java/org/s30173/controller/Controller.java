package org.s30173.controller;

import org.s30173.ModellingFrameworkSample;
import org.s30173.helpers.Bind;
import org.s30173.helpers.Model;

import javax.script.*;
import javax.swing.table.DefaultTableModel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Controller {
    private final String modelClassName;
    private Model model;

    private String[] lata;
    private HashMap<String, Object[]> newScriptFields;

    public Controller(String modelClassName) {
        this.modelClassName = modelClassName;
        this.newScriptFields = new HashMap<>();
    }

    public Controller readDataFrom(String fileName) {
        newScriptFields.clear(); // clear fields that were added when scripting
        LinkedHashMap<String, ArrayList<Double>> data = new LinkedHashMap<>();

        try (Stream<String> lines = Files.lines(Path.of(fileName))) {
            lines.forEach(line -> {
                if (line.startsWith("LATA ")) {
                    lata = line.substring(5).trim().split("\\s+");
                } else {
                    String[] arr = line.split("\\s+");

                    ArrayList<Double> values = new ArrayList<>();
                    data.put(arr[0], values);

                    for (int i = 1; i < arr.length; i++)
                        values.add(Double.parseDouble(arr[i]));
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try {
            int LL = lata.length;
            model = (Model) Class.forName(modelClassName).getDeclaredConstructor().newInstance();

            // initialize fields with annotation @Bind
            Arrays.stream(model.getClass().getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(Bind.class))
                .forEach(field -> {
                    field.setAccessible(true);
                    try {
                        if (field.getName().equals("LL")) {
                            field.set(model, LL);
                        } else {
                            double[] arr = new double[LL];
                            ArrayList<Double> values = data.get(field.getName());

                            if (values != null) {
                                // copy all values that are present in values
                                for (int i = 0; i < values.size(); i++) {
                                    arr[i] = values.get(i);
                                }

                                // copy the last present value to the rest if needed
                                if (values.size() < LL) {
                                    double last = values.getLast();
                                    for (int i = values.size(); i < LL; i++) {
                                        arr[i] = last;
                                    }
                                }

                                field.set(model, arr);
                            }
                        }
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    public Controller runModel() {
        model.run();
        return this;
    }

    public Controller runScriptFromFile(String fileName) throws ScriptException {
        StringBuilder str = new StringBuilder();

        try (Stream<String> lines = Files.lines(Path.of(fileName))) {
            lines.forEach(line -> str.append(line).append("\n"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        runScript(str.toString());
        return this;
    }

    public Controller runScript(String script) throws ScriptException {
        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine groovy = manager.getEngineByName("groovy");

        // make data from the model available in the script
        Arrays.stream(model.getClass().getDeclaredFields())
            .filter(field -> field.isAnnotationPresent(Bind.class))
            .forEach(field -> {
                field.setAccessible(true);

                try {
                    Object value = field.get(model);
                    groovy.put(field.getName(), value);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }

            });

        // TODO: make fields added using script available as well
        newScriptFields.forEach(groovy::put);

        groovy.eval(script);

        // repaint the table (insert new values)
        DefaultTableModel tableModel = ModellingFrameworkSample.tableModel;
        tableModel.setNumRows(0);
        Arrays.stream(model.getClass().getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(Bind.class))
                .filter(field -> !field.getName().equals("LL"))
                .forEach(field -> {
                    field.setAccessible(true);
                    try {
                        String name = field.getName();
                        Object value = field.get(model);

                        // data for table
                        Object[] tableRowData = new Object[lata.length+1];
                        tableRowData[0] = name;
                        Object[] data = formatFieldValueAsObjArr(value);
                        System.arraycopy(data, 0, tableRowData, 1, data.length);

                        tableModel.addRow(tableRowData);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                });


        // TODO: add fields created in script to the table and save them for future scripts
        Bindings bindings = groovy.getBindings(ScriptContext.ENGINE_SCOPE);
        bindings.entrySet().forEach(entry -> {
            // add new entries to the map newScriptFields
        });

        return this;
    }

    public String getResultsAsTsv() {
        DefaultTableModel tableModel = ModellingFrameworkSample.tableModel;

        // clear the table
        tableModel.setNumRows(0);
        tableModel.setColumnCount(0);

        // add columns
        tableModel.addColumn("");
        for (String s : lata)
            tableModel.addColumn(s);

        // build the result string (for console)
        StringBuilder res = new StringBuilder();
        res.append("LATA\t");
        res.append(String.join("\t", lata));
        res.append("\n");

        Arrays.stream(model.getClass().getDeclaredFields())
            .filter(field -> field.isAnnotationPresent(Bind.class))
            .filter(field -> !field.getName().equals("LL"))
            .forEach(field -> {
                field.setAccessible(true);
                try {
                    String name = field.getName();
                    Object value = field.get(model);

                    // data for table
                    Object[] tableRowData = new Object[lata.length+1];
                    tableRowData[0] = name;
                    Object[] data = formatFieldValueAsObjArr(value);
                    System.arraycopy(data, 0, tableRowData, 1, data.length);

                    tableModel.addRow(tableRowData);

                    // string for console
                    res
                    .append(name)
                    .append("\t")
                    .append(formatFieldValueAsStr(value))
                    .append("\n");
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            });
        return res.toString();
    }


    // Helpers
    private String formatFieldValueAsStr(Object value) {
        if (value == null)
            return "null";

        if (value.getClass().isArray()) {
            if (value instanceof double[]) {
                return Arrays.stream((double[]) value)
                        .mapToObj(Double::toString)
                        .collect(Collectors.joining("\t"));
            }
        }

        return value.toString();
    }

    private Object[] formatFieldValueAsObjArr(Object value) {
        if (value == null)
            return new Object[0];

        if (value.getClass().isArray()) {
            if (value instanceof double[]) {
                return Arrays.stream((double[]) value)
                        .mapToObj(Controller::formatNumber)
                        .toArray();
            }
        }

        return new String[]{value.toString()};
    }

    private static String formatNumber(double number) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setDecimalSeparator(',');
        symbols.setGroupingSeparator(' ');

        String pattern = number < 1000 ? "#,##0.##" : "#,##0.#";

        DecimalFormat df = new DecimalFormat(pattern, symbols);
        String formattedNum = df.format(number);

        if (formattedNum.endsWith(",0"))
            formattedNum = formattedNum.substring(0, formattedNum.length() - 2);

        return formattedNum;
    }

    private static Set<?> get
}