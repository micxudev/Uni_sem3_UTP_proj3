package org.s30173;

import org.s30173.controller.Controller;

import javax.script.ScriptException;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.s30173.helpers.Manager.*;

public class ModellingFrameworkSample {
    private static final DefaultListModel<String> modelsListModel = new DefaultListModel<>();
    private static final DefaultListModel<String> dataListModel = new DefaultListModel<>();
    public static final DefaultTableModel tableModel = new DefaultTableModel() {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };

    private static final JList<String> modelList = new JList<>(modelsListModel);
    private static final JList<String> dataList = new JList<>(dataListModel);
    public static final JTable viewTable = new JTable(tableModel);

    private static Controller controller;
    private static JButton scriptFileBtn;
    private static JButton adHocScriptButton;

    private static final JFrame frame = new JFrame("Modelling framework sample");

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ModellingFrameworkSample::initializeGUI);
        loadModelsAndDataIntoLists();
    }


    // ----- GUI -----
    private static void initializeGUI() {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(FRAME_SIZE);
        frame.setMinimumSize(FRAME_MIN_SIZE);
        frame.setLocationRelativeTo(null);
        frame.getContentPane().setBackground(BG_COLOR);

        frame.add(createLeftPanel(), BorderLayout.WEST);
        frame.add(createCentralPanel(), BorderLayout.CENTER);

        frame.setVisible(true);
    }

    private static JPanel createLeftPanel() {
        JPanel lp = new JPanel(new BorderLayout());
        lp.setOpaque(false);
        lp.setBorder(BorderFactory.createEmptyBorder(5, 10, 0, 10));

        JLabel l = new JLabel("Select model and data");
        l.setFont(BOLD_FONT);
        l.setForeground(FG_COLOR);

        configureList(modelList);
        configureList(dataList);

        JButton runModelBtn = new JButton("Run model");
        styleButton(runModelBtn);
        runModelBtn.addActionListener(_ -> runModelClickAction());

        JPanel rmp = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 5));
        rmp.setOpaque(false);
        rmp.add(runModelBtn);

        JPanel gapPanel = new JPanel();
        gapPanel.setOpaque(false);

        lp.add(l, BorderLayout.NORTH);
        lp.add(gapPanel, BorderLayout.CENTER);
        lp.add(modelList, BorderLayout.WEST);
        lp.add(dataList, BorderLayout.EAST);
        lp.add(rmp, BorderLayout.SOUTH);

        return lp;
    }

    private static JPanel createCentralPanel() {
        JPanel cp = new JPanel(new BorderLayout());
        cp.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 10));
        cp.setOpaque(false);

        viewTable.setBackground(BG_COLOR2);
        viewTable.setForeground(FG_COLOR2);
        viewTable.setFont(PLAIN_M_FONT);
        viewTable.setRowHeight(22);
        viewTable.setGridColor(Color.BLACK);
        viewTable.setIntercellSpacing(new Dimension(10,0));

        JTableHeader header = viewTable.getTableHeader();
        header.setBackground(TABLE_HEADER_COLOR);
        header.setForeground(FG_COLOR2);
        header.setFont(BOLD_FONT);

        JScrollPane scroll = new JScrollPane(viewTable);
        scroll.getViewport().setBackground(BG_COLOR);
        scroll.setBorder(DEF_BORDER);

        JPanel bp = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        bp.setOpaque(false);

        scriptFileBtn = new JButton("Run script from file");
        styleButton(scriptFileBtn);
        scriptFileBtn.addActionListener(_ -> scriptFileButtonClickAction());
        scriptFileBtn.setVisible(false);
        bp.add(scriptFileBtn);

        adHocScriptButton = new JButton("Create and run ad hoc script");
        styleButton(adHocScriptButton);
        adHocScriptButton.addActionListener(_ -> scriptEditorButtonClickAction());
        adHocScriptButton.setVisible(false);
        bp.add(adHocScriptButton);

        cp.add(scroll, BorderLayout.CENTER);
        cp.add(bp, BorderLayout.SOUTH);

        return cp;
    }

    private static void configureList(JList<?> l) {
        l.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        l.setBackground(BG_COLOR2);
        l.setBorder(DEF_BORDER);
        l.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                l.setFont(PLAIN_M_FONT);
                l.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
                l.setOpaque(true);

                if (isSelected) {
                    l.setBackground(SELECTED_COLOR);
                    l.setForeground(FG_COLOR2);
                } else {
                    l.setBackground(BG_COLOR2);
                    l.setForeground(FG_COLOR);
                }

                return l;
            }
        });
    }

    private static void styleButton(JButton b) {
        b.setFocusable(false);
        b.setFont(PLAIN_M_FONT);
        b.setForeground(FG_COLOR);
        b.setBackground(BG_COLOR2);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setOpaque(true);
        b.setBorderPainted(true);
        b.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR, 1),
            BorderFactory.createEmptyBorder(5,10,5,10))
        );
    }

    private static void scriptFileButtonClickAction() {
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fc.setDialogTitle("Choose script file");
        fc.setCurrentDirectory(new File(SCRIPTS_DIR));

        int r = fc.showOpenDialog(frame);
        if (r == JFileChooser.APPROVE_OPTION)
            runScriptFromFileClickAction(fc);
    }

    private static void scriptEditorButtonClickAction() {
        JDialog sd = new JDialog(frame, "Script", true);
        sd.getContentPane().setBackground(BG_COLOR);
        sd.setSize(400, 300);
        sd.setLayout(new BorderLayout());

        JTextArea sa = new JTextArea();
        sa.setBackground(BG_COLOR2);
        sa.setBorder(BorderFactory.createEmptyBorder(5,5,5,5));
        sa.setForeground(FG_COLOR);
        sa.setFont(PLAIN_L_FONT);
        sa.setLineWrap(true);
        sa.setWrapStyleWord(true);
        sa.setCaretColor(FG_COLOR2);

        JScrollPane scroll = new JScrollPane(sa);
        scroll.setBorder(DEF_BORDER);
        sd.add(scroll, BorderLayout.CENTER);

        JPanel dbp = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        dbp.setOpaque(false);

        JButton runBtn = new JButton("Run");
        styleButton(runBtn);
        runBtn.addActionListener(_ -> {
            if(runAdhocScriptClickAction(sa))
                sd.dispose();
        });
        dbp.add(runBtn);

        JButton closeBtn = new JButton("Close");
        styleButton(closeBtn);
        closeBtn.addActionListener(_ -> sd.dispose());
        dbp.add(closeBtn);

        sd.add(dbp, BorderLayout.SOUTH);
        sd.setLocationRelativeTo(frame);
        sd.setVisible(true);
    }

    public static void addColumns(String[] columnNames) {
        tableModel.setNumRows(0);
        tableModel.setColumnCount(0);

        tableModel.addColumn("");
        for (String s : columnNames)
            tableModel.addColumn(s);
    }


    // ----- Logic -----
    private static void loadModelsAndDataIntoLists() {
        try {
            DirectoryStream<Path> modelsStream = Files.newDirectoryStream(Path.of(MODELS_DIR));
            modelsStream.forEach(file -> {
                String fileName = file.getFileName().toString();
                String fileNameNoExtension = fileName.substring(0, fileName.lastIndexOf("."));
                modelsListModel.addElement(fileNameNoExtension);
            });
            modelsStream.close();

            DirectoryStream<Path> dataStream = Files.newDirectoryStream(Path.of(DATA_DIR));
            dataStream.forEach(file -> dataListModel.addElement(file.getFileName().toString()));
            dataStream.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void runModelClickAction() {
        String modelValue = modelList.getSelectedValue();
        String dataValue = dataList.getSelectedValue();

        if (modelValue == null || dataValue == null) {
            JOptionPane.showMessageDialog(frame,
            "Choose model and data first", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        controller = new Controller(MODELS_PACKAGE + modelValue);
        controller
            .readDataFrom(DATA_DIR + dataValue)
            .runModel();

        scriptFileBtn.setVisible(true);
        adHocScriptButton.setVisible(true);
    }

    private static void runScriptFromFileClickAction(JFileChooser fc) {
        try {
            controller.runScriptFromFile(fc.getSelectedFile().getAbsolutePath());
        } catch (ScriptException e) {
            JOptionPane.showMessageDialog(frame,
            "Invalid groovy script in the file", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static boolean runAdhocScriptClickAction(JTextArea sa) {
        try {
            controller.runScript(sa.getText());
            return true;
        } catch (ScriptException e) {
            JOptionPane.showMessageDialog(frame,
            "Invalid groovy script", "Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }
}