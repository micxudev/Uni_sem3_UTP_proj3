package org.s30173.controller;

import org.s30173.ModellingFrameworkSample;
import org.s30173.helpers.Bind;
import org.s30173.helpers.Model;

import javax.script.*;
import javax.swing.table.DefaultTableModel;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.s30173.ModellingFrameworkSample.tableModel;

public class Controller {
    private final String modelClassName;
    private Model model;
    private LinkedHashMap<Field, String> boundFields;

    private final HashMap<String, double[]> dataFromFile; // (var_name : values)
    private String[] lata; // years (e.g. 2015, 2016 etc.)

    private final LinkedHashMap<String, Object> scriptVars; // variables that were created when scripting

    public Controller(String modelClassName) {
        this.modelClassName = modelClassName;
        this.dataFromFile = new HashMap<>();
        this.scriptVars = new LinkedHashMap<>();
    }

    public Controller readDataFrom(String fileName) {
        // read the data from the file and store it in the map (var_name : values)
        try (Stream<String> lines = Files.lines(Path.of(fileName))) {
            lines.forEach(line -> {
                if (line.startsWith("LATA ")) {
                    lata = line.substring(5).trim().split("\\s+");
                } else {
                    String[] arr = line.trim().split("\\s+");
                    String varName = arr[0];

                    double[] values = new double[arr.length - 1];
                    for (int i = 1; i < arr.length; i++)
                        values[i-1] = (Double.parseDouble(arr[i]));

                    dataFromFile.put(varName, values);
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    public Controller runModel() {
        // assumption: this method should not be called
        // before readDataFrom() method, because data map will be empty
        try {
            // create new instance of a model
            this.model = (Model) Class.forName(modelClassName).getDeclaredConstructor().newInstance();
            this.boundFields = getModelBoundFields();
            int LL = lata.length;

            // initialize fields (only with annotation @Bind)
            boundFields.forEach((field, name) -> {
                try {
                    if (name.equals("LL")) {
                        field.set(model, LL);
                        return;
                    }

                    double[] values = dataFromFile.get(name);
                    if (values == null)
                        return;

                    double[] arr = new double[LL];

                    // copy all from values (from file) to arr (actual data for each row)
                    System.arraycopy(values, 0, arr, 0, values.length);

                    // copy the last present value to the rest if needed
                    if (values.length < LL) {
                        double lastValue = values[values.length - 1];
                        for (int i = values.length; i < LL; i++)
                            arr[i] = lastValue;
                    }

                    field.set(model, arr);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

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

        // make fields (with @Bind from the model) available in the script
        boundFields.forEach((field, name) -> {
            try {
                groovy.put(name, field.get(model));
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        });

        // make fields from previous scripts available in this script
        scriptVars.forEach(groovy::put);

        // run this script
        groovy.eval(script);

        // update table
        DefaultTableModel tableModel = ModellingFrameworkSample.tableModel;
        tableModel.setNumRows(0); // remove all rows (old data)

        // update old rows
        boundFields.forEach((field, name) -> {
            if (name.equals("LL"))
                return;

            try {
                addNewRow(name, field.get(model));
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        });

        // save new vars and add new rows
        Bindings bindings = groovy.getBindings(ScriptContext.ENGINE_SCOPE);
        Map<String, Object> fieldsWithBind = getFieldsWithBindFromModel();
        bindings.forEach((key, value) -> {
            if (key.length() == 1 &&
                Character.isLetter(key.charAt(0)) &&
                Character.isLowerCase(key.charAt(0))) {
                return;
            }

            // add new entries
            if (!fieldsWithBind.containsKey(key)) {
                scriptVars.put(key, value);
                addNewRow(key, value);
            }
        });

        return this;
    }

    public String getResultsAsTsv() {
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

    private Map<String, Object> getFieldsWithBindFromModel() {
        Map<String, Object> map = new HashMap<>();
        Arrays.stream(model.getClass().getDeclaredFields())
            .filter(field -> field.isAnnotationPresent(Bind.class))
            .forEach(field -> {
                field.setAccessible(true);
                try {
                    String name = field.getName();
                    Object value = field.get(model);
                    map.put(name, value);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            });
        return map;
    }

    private LinkedHashMap<Field, String> getModelBoundFields() {
        LinkedHashMap<Field, String> map = new LinkedHashMap<>();
        Arrays.stream(model.getClass().getDeclaredFields())
            .filter(field -> field.isAnnotationPresent(Bind.class))
            .forEach(field -> {
                field.setAccessible(true);
                map.put(field, field.getName());
            });
        return map;
    }

    private void addNewRow(String name, Object value) {
        Object[] tableRowData = new Object[lata.length+1];
        tableRowData[0] = name;
        Object[] data = formatFieldValueAsObjArr(value);
        System.arraycopy(data, 0, tableRowData, 1, data.length);
        tableModel.addRow(tableRowData);
    }
}