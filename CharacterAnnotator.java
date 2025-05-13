import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import javax.imageio.ImageIO;        // Save images as files
import javax.swing.*;                // GUI components
import java.awt.*;                   // Graphics and layout
import java.awt.event.*;            // Mouse events
import java.awt.image.BufferedImage; // Drawing canvas
import java.io.*;                    // File I/O
import java.lang.reflect.Type;       // Gson type handling
import java.util.ArrayList;          // Dynamic list
import java.util.HashMap;            // Key-value mapping
import java.util.List;               // List interface
import java.util.Stack;              // Undo (LIFO)

// Main class- define and initialize variables, objects, and methods
public class CharacterAnnotator {
    private static final String DATA_FILE = "data.json"; // where labels + paths are stored
    private static final String IMG_DIR = "images"; // folder for saved images
    private List<DataEntry> entries; // list of saved images and labels
    private DrawingPanel drawingPanel; // drawing canvas
    private JTextField labelField; // text field for user to enter label
    private Gson gson = new GsonBuilder().setPrettyPrinting().create(); // for reading/ writing in JSON format
    public static Stack<DataEntry> entryStack = new Stack<>(); // stack object

    // main method
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new CharacterAnnotator().createAndShowGui());
    }

    // constructor to set up GUI
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

        // declare buttons and attach action listeners to call methods
        JButton saveBtn = new JButton("Save");
        saveBtn.addActionListener(event -> onSave());

        JButton countBtn = new JButton("Show Counts");
        countBtn.addActionListener(event -> showLabelCounts());

        JButton clearBtn = new JButton("Clear");
        clearBtn.addActionListener(event -> drawingPanel.clear());

        JButton undoBtn = new JButton("Undo");
        undoBtn.addActionListener(event -> undoLastSave());

        // add components to control panel
        control.add(new JLabel("Label:"));
        control.add(labelField);
        control.add(saveBtn);
        control.add(countBtn);
        control.add(clearBtn);
        control.add(undoBtn);

        frame.add(control, BorderLayout.SOUTH);
        frame.pack();
        frame.setResizable(false);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    // load existing entries from data.json
    private void loadEntries() {
        File f = new File(DATA_FILE);
        if (!f.exists()) {  // if file doesn't exist, create a new ArrayList for entries
            entries = new ArrayList<>();
            return;
        }
        try (Reader r = new FileReader(f)) {
            Type listType = new TypeToken<List<DataEntry>>() {}.getType();  // Define the data type for Gson to read: List<DataEntry>
            entries = gson.fromJson(r, listType);   // Deserialize JSON into the ArrayList
        } catch (IOException ex) {
            ex.printStackTrace();
            entries = new ArrayList<>();    // If there's an error reading the file, fall back to an empty list
        }

        // Display loaded entries (for debugging)
        System.out.println("Entries in ArrayList: ");
        for (int i = 0; i < entries.size(); i++) {
            DataEntry e = entries.get(i);
            System.out.println(e.label + ", " + e.path);
        }
    }

    // Make sure the images folder exists, create if it doesn't
    private void ensureImageDir() {
        File dir = new File(IMG_DIR);
        if (!dir.exists()) dir.mkdirs();
    }

    private void onSave() {
        String rawLabel = labelField.getText().trim().toLowerCase();    // get label from text field
        String label = rawLabel.isEmpty() ? "?" : rawLabel; // if empty, set to "?", otherwise use the rawLabel

        long count = entries.stream().filter(e -> e.label.equals(label)).count();   // count how many images with the same label already exist
        String fname = String.format("%s_%d.png", label, count);    // Create a unique filename like "a_0.png", "a_1.png", etc.

        
        File outFile = new File(IMG_DIR, fname);    // Create a File object: images/filename.png
        try {
            ImageIO.write(drawingPanel.getImage(), "PNG", outFile); // Save the image as PDF to the file
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(null, "Failed to save image: " + ex.getMessage());
            return;
        }

        DataEntry newEntry = new DataEntry(IMG_DIR + File.separator + fname, label); // Create a new DataEntry object with the image path and label
        entries.add(newEntry);  // add to ArrayList
        entryStack.push(newEntry); // push to stack

        // writes entries in ArrayList to data.json
        try (Writer w = new FileWriter(DATA_FILE)) {
            gson.toJson(entries, w);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(null, "Failed to update data.json: " + ex.getMessage());
            return;
        }

        // clears canvas after saving
        drawingPanel.clear();
    }

    private void showLabelCounts() {
        if (entries == null || entries.isEmpty()) {     // check if entries is null or empty
            System.out.println("No entries found.");
            return;
        }

        HashMap<String, Integer> labelCounts = new HashMap<>();
        for (DataEntry entry : entries) {   // Loop through all entries in ArrayList
            labelCounts.put(entry.label, labelCounts.getOrDefault(entry.label, 0) + 1); // Creates a mapping of label to count, maps labels to their counts
        }

        System.out.println("Label Counts:");
        for (String label : labelCounts.keySet()) { // Loop through all labels (keys) in the HashMap
            System.out.println(label + ": " + labelCounts.get(label));  // Print the label and the count it maps to
        }
    }

    // class to store image path and label
    private static class DataEntry {
        String path, label;

        DataEntry(String p, String l) {
            path = p;
            label = l;
        }
    }

    // panel where user draws with mouse
    private static class DrawingPanel extends JPanel {
        private BufferedImage img;
        private Graphics2D g2;
        private int prevX, prevY;

        DrawingPanel(int w, int h) {
            img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB); // Image that stores the user's drawing
            g2 = img.createGraphics();  // Graphics2D object for drawing
            g2.setColor(Color.WHITE);   // Set background color to white
            g2.fillRect(0, 0, w, h);   // Fill the image with white
            g2.setStroke(new BasicStroke(8, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); // Set stroke width and style
            g2.setColor(Color.BLACK); // Set drawing color to black

            setPreferredSize(new Dimension(w, h)); // Set the preferred size of the panel
            MouseAdapter ma = new MouseAdapter() { // MouseAdapter to handle mouse events
                public void mousePressed(MouseEvent e) { // when mouse is pressed
                    prevX = e.getX();   
                    prevY = e.getY();
                }

                public void mouseDragged(MouseEvent e) {
                    int x = e.getX(), y = e.getY();
                    g2.drawLine(prevX, prevY, x, y); // draw line while dragging
                    prevX = x;
                    prevY = y;
                    repaint();
                }
            };
            addMouseListener(ma);   // add mouse listener for mouse pressed
            addMouseMotionListener(ma); // add mouse motion listener for mouse dragged
        }

        // clear the canvas
        public void clear() {
            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, img.getWidth(), img.getHeight()); // clear canvas
            g2.setColor(Color.BLACK);
            repaint();
        }

        // returns the image from the panel
        public BufferedImage getImage() {
            return img;
        }

        // paint the image on the panel
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            g.drawImage(img, 0, 0, null);
        }
    }
    private void undoLastSave() {
        if (entryStack.isEmpty()) {
            System.out.println("Nothing to undo.");
            return;
        }
        DataEntry last = entryStack.pop();  // pop the last entry from the stack
        File fileToDelete = new File(last.path); // file to delete = last entry
        if (fileToDelete.exists()) {    // check if file exists (for safety)
            fileToDelete.delete();  // delete the file
        }
        entries.remove(last); // remove the entry from the ArrayList
        try (Writer w = new FileWriter(DATA_FILE)) {    // open data.json for writing
            gson.toJson(entries, w); // write the updated ArrayList to data.json
        } catch (IOException ex) 
        {
        JOptionPane.showMessageDialog(null, "Failed to update data.json: " + ex.getMessage());
        }
        System.out.println("Undid last save: " + last.path);
    }
}