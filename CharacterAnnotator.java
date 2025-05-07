import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class CharacterAnnotator {
    private static final String DATA_FILE = "data.json"; // where labels + paths are stored
    private static final String IMG_DIR = "images"; // directory for saved images
    private List<DataEntry> entries;
    private DrawingPanel drawingPanel;
    private JTextField labelField;
    private Gson gson = new GsonBuilder().setPrettyPrinting().create(); // for pretty JSON output

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new CharacterAnnotator().createAndShowGui());
    }

    // ChatGPT: This method sets up the GUI and initializes the drawing panel
    private void createAndShowGui() {
        loadEntries(); // load existing data if available
        ensureImageDir(); // create images folder if needed

        JFrame frame = new JFrame("Character Annotator");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        drawingPanel = new DrawingPanel(128, 128); // drawing canvas
        drawingPanel.setBorder(BorderFactory.createLineBorder(Color.BLACK));
        frame.add(drawingPanel, BorderLayout.CENTER);

        JPanel control = new JPanel(); // bottom control panel
        labelField = new JTextField("a", 2);

        JButton saveBtn = new JButton("Save");
        saveBtn.addActionListener(e -> onSave());

        JButton countBtn = new JButton("Show Counts");
        countBtn.addActionListener(e -> showLabelCounts());

        JButton clearBtn = new JButton("Clear");
        clearBtn.addActionListener(e -> drawingPanel.clear());

        control.add(new JLabel("Label:"));
        control.add(labelField);
        control.add(saveBtn);
        control.add(countBtn);
        control.add(clearBtn);

        frame.add(control, BorderLayout.SOUTH);

        frame.pack();
        frame.setResizable(false);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private void loadEntries() {
        File f = new File(DATA_FILE);
        if (!f.exists()) {
            entries = new ArrayList<>();
            return;
        }
        try (Reader r = new FileReader(f)) {
            Type listType = new TypeToken<List<DataEntry>>(){}.getType();
            entries = gson.fromJson(r, listType);
        } catch (IOException ex) {
            ex.printStackTrace();
            entries = new ArrayList<>();
        }
    }

    private void ensureImageDir() {
        File dir = new File(IMG_DIR);
        if (!dir.exists()) dir.mkdirs();
    }

    // ChatGPT: This method handles the save button action
    private void onSave() {
        String label = labelField.getText().trim();
        if (label.isEmpty()) label = "?"; // fallback if no label entered

        // Find next available filename
        int idx = entries.size() + 1;
        String fname;
        do {
            fname = String.format("image%04d.png", idx++);
        } while (new File(IMG_DIR, fname).exists());

        File outFile = new File(IMG_DIR, fname);
        try {
            ImageIO.write(drawingPanel.getImage(), "PNG", outFile); // save canvas as PNG
        } catch (IOException ex) {
            System.out.println("Failed to save image: " + ex.getMessage());
            return;
        }

        // Record the new image and label
        entries.add(new DataEntry(IMG_DIR + File.separator + fname, label));

        // Update the JSON file
        try (Writer w = new FileWriter(DATA_FILE)) {
            gson.toJson(entries, w);
        } catch (IOException ex) {
            System.out.println("Failed to update data.json: " + ex.getMessage());
            return;
        }

        drawingPanel.clear(); // reset canvas after save
    }

    private void showLabelCounts() {
        if (entries == null || entries.isEmpty()) {
            System.out.println("No entries found.");
            return;
        }

        HashMap<String, Integer> labelCounts = new HashMap<>();
        for (DataEntry entry : entries) {
            labelCounts.put(entry.label, labelCounts.getOrDefault(entry.label, 0) + 1);
        }

        System.out.println("Label Counts:");
        for (String label : labelCounts.keySet()) {
            System.out.println(label + ": " + labelCounts.get(label));
        }
    }

    // simple class to store image path and label
    private static class DataEntry {
        String path, label;
        DataEntry(String p, String l) { path = p; label = l; }
    }

    // ChatGPT: This class handles the drawing panel where the user can draw characters
    private static class DrawingPanel extends JPanel {
        private BufferedImage img;
        private Graphics2D g2;
        private int prevX, prevY;

        DrawingPanel(int w, int h) {
            img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            g2 = img.createGraphics();
            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, w, h);
            g2.setStroke(new BasicStroke(8, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(Color.BLACK);

            setPreferredSize(new Dimension(w, h));
            MouseAdapter ma = new MouseAdapter() {
                public void mousePressed(MouseEvent e) {
                    prevX = e.getX();
                    prevY = e.getY();
                }
                public void mouseDragged(MouseEvent e) {
                    int x = e.getX(), y = e.getY();
                    g2.drawLine(prevX, prevY, x, y); // draw line while dragging
                    prevX = x; prevY = y;
                    repaint();
                }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
        }

        public void clear() {
            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, img.getWidth(), img.getHeight()); // clear canvas
            g2.setColor(Color.BLACK);
            repaint();
        }

        public BufferedImage getImage() {
            return img;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            g.drawImage(img, 0, 0, null);
        }
    }
}
