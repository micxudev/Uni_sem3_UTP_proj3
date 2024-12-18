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
    private final Model model;
    private LinkedHashMap<Field, String> bindFields;
    private final HashSet<String> bindFieldNames = new HashSet<>();

    private final Map<String, double[]> dataFromFile = new HashMap<>();
    private String[] lata;

    // vars that were created when scripting
    private final Map<String, Object> scriptVars = new LinkedHashMap<>();


    public Controller(String modelClassName) {
        try {
            this.model = (Model) Class.forName(modelClassName).getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Error creating model from class", e);
        }
    }

    public Controller readDataFrom(String fileName) {
        try (Stream<String> lines = Files.lines(Path.of(fileName))) {
            lines.forEach(line -> {
                if (line.startsWith("LATA ")) {
                    lata = line.substring(5).trim().split("\\s+");
                } else {
                    String[] parts = line.trim().split("\\s+");
                    double[] values = Arrays.stream(parts, 1, parts.length)
                            .mapToDouble(Double::parseDouble)
                            .toArray();
                    dataFromFile.put(parts[0], values);
                }
            });
        } catch (IOException e) {
            throw new RuntimeException("Error reading data file: " + fileName, e);
        }
        return this;
    }

    public Controller runModel() {
        bindFields = getBindFields();
        bindFields.forEach((field, name) -> {
            if (name.equals("LL"))
                setValue(field, lata.length);
            else
                setValue(field, prepareArray(dataFromFile.get(name), lata.length));
        });
        model.run();
        return this;
    }

    public Controller runScriptFromFile(String fileName) throws ScriptException {
        StringBuilder script = new StringBuilder();

        try (Stream<String> lines = Files.lines(Path.of(fileName))) {
            lines.forEach(line -> script.append(line).append('\n'));
        } catch (IOException e) {
            throw new RuntimeException("Error reading script file: " + fileName, e);
        }

        runScript(script.toString());
        return this;
    }

    public Controller runScript(String script) throws ScriptException {
        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine groovy = manager.getEngineByName("groovy");

        // make fields (with @Bind from the model) available in the script
        bindFields.forEach((field, name) -> groovy.put(name, getValue(field)));

        // make fields from previous scripts available in this script
        scriptVars.forEach(groovy::put);

        // run this script
        groovy.eval(script);

        // update table
        DefaultTableModel tableModel = ModellingFrameworkSample.tableModel;
        tableModel.setNumRows(0); // remove all rows (old data)

        // update old rows
        bindFields.forEach((field, name) -> {
            if (name.equals("LL"))
                return;
            addTableRow(name, getValue(field));
        });

        // save new vars and add new rows
        Bindings bindings = groovy.getBindings(ScriptContext.ENGINE_SCOPE);
        bindings.forEach((key, value) -> {
            if (key.length() == 1 &&
                Character.isLetter(key.charAt(0)) &&
                Character.isLowerCase(key.charAt(0))) {
                return;
            }

            // add new entries
            if (!bindFieldNames.contains(key)) {
                scriptVars.put(key, value);
                addTableRow(key, value);
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

        bindFields.forEach((field, name) -> {
            if (name.equals("LL"))
                return;

            Object value = getValue(field);
            addTableRow(name, value);
            res.append(name).append("\t").append(fieldValueToStr(value)).append("\n");
        });
        return res.toString();
    }


    // Helpers
    private LinkedHashMap<Field, String> getBindFields() {
        return Arrays.stream(model.getClass().getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(Bind.class))
                .peek(field -> {
                    field.setAccessible(true);
                    bindFieldNames.add(field.getName());
                })
                .collect(Collectors.toMap(
                        field -> field,
                        Field::getName,
                        (existing, _) -> existing,
                        LinkedHashMap::new
                ));
    }

    private double[] prepareArray(double[] vals, int len) {
        if (vals == null)
            return new double[len];

        double[] arr = new double[len];
        System.arraycopy(vals, 0, arr, 0, vals.length);

        if (vals.length < len)
            Arrays.fill(arr, vals.length, len, vals[vals.length - 1]);

        return arr;
    }

    private Object[] formatFieldValues(Object value) {
        if (value == null)
            return new Object[0];

        if (value.getClass().isArray() && value instanceof double[])
            return Arrays.stream((double[]) value)
                    .mapToObj(Controller::formatNumber)
                    .toArray();

        return new String[]{value.toString()};
    }

    private static String formatNumber(double number) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setDecimalSeparator(',');
        symbols.setGroupingSeparator(' ');

        String pattern = number < 100 ? "#,##0.##" : "#,##0.#";

        DecimalFormat decFormat = new DecimalFormat(pattern, symbols);
        String formattedNumber = decFormat.format(number);

        if (formattedNumber.endsWith(symbols.getDecimalSeparator() + "0"))
            formattedNumber = formattedNumber.substring(0, formattedNumber.length() - 2);

        return formattedNumber;
    }

    private void addTableRow(String name, Object value) {
        Object[] tableRowData = new Object[lata.length+1];
        tableRowData[0] = name;
        Object[] data = formatFieldValues(value);
        System.arraycopy(data, 0, tableRowData, 1, data.length);
        tableModel.addRow(tableRowData);
    }

    private Object getValue(Field field) {
        try {
            return field.get(model);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to get field's value: " + field.getName(), e);
        }
    }

    private void setValue(Field field, Object value) {
        try {
            field.set(model, value);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to set field's value: " + field.getName(), e);
        }
    }


    // For console log
    private String fieldValueToStr(Object value) {
        if (value == null)
            return "NULL";

        if (value.getClass().isArray() && value instanceof double[])
            return Arrays.stream((double[]) value)
                    .mapToObj(Double::toString)
                    .collect(Collectors.joining("\t"));

        return value.toString();
    }
}