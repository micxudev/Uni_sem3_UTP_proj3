package org.s30173.helpers;

import javax.swing.border.Border;
import javax.swing.border.LineBorder;
import java.awt.*;

public class Manager {
    public static String MODELS_PACKAGE = "org.s30173.models.";
    public static String MODELS_DIR     = "src/main/java/org/s30173/models";
    public static String DATA_DIR       = "src/data/";
    public static String SCRIPTS_DIR    = "src/scripts/";

    public static final Color BG_COLOR       = new Color(30, 31, 34);
    public static final Color BG_COLOR2      = new Color(43, 45, 48);
    public static final Color BORDER_COLOR   = new Color(66, 81, 106);
    public static final Color SELECTED_COLOR = new Color(33, 66, 130);
    public static final Color FG_COLOR       = new Color(210, 210, 210);
    public static final Color FG_COLOR2      = new Color(250, 250, 250);
    public static final Color TABLE_HEADER_COLOR   = new Color(83, 129, 211);

    public static final Font PLAIN_L_FONT = new Font("Arial", Font.PLAIN, 16);
    public static final Font PLAIN_M_FONT = new Font("Arial", Font.PLAIN, 14);
    public static final Font BOLD_FONT    = new Font("Arial", Font.BOLD, 14);

    public static final Border DEF_BORDER = new LineBorder(BORDER_COLOR, 1);

    public static final Dimension FRAME_SIZE     = new Dimension(800, 600);
    public static final Dimension FRAME_MIN_SIZE = new Dimension(750, 400);
}