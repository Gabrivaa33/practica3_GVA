package practica3_GVA;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.*;
import javax.swing.text.*;
import javax.swing.undo.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import javax.swing.SwingWorker;

/**
 * TextEditorApp
 *
 * Editor/Conversor de texto con:
 * - Área editable (JTextPane) con estilo (negrita, cursiva, color)
 * - Barra de herramientas con operaciones encadenables (mayúsculas/minúsculas, invertir, eliminar dobles espacios)
 * - Búsqueda y reemplazo (diálogo)
 * - Contadores (caracteres, palabras, líneas)
 * - Selección con ratón, menú contextual (cortar/copiar/pegar)
 * - Atajos: Ctrl+C/X/V, Ctrl+Z (deshacer), Ctrl+Y (rehacer), Ctrl+F (buscar)
 * - UndoManager para deshacer/rehacer
 * - ProgressLabel personalizado para operaciones de archivo
 * - Funciones de archivo: Nuevo, Abrir, Guardar, Guardar como
 * - ProgressLabel muestra progreso real de carga de archivos
 *
 * Comentarios incluidos para entrega en la práctica.
 */
public class Practica3 extends JFrame {

    private JTextPane textPane;
    private JLabel lblChars, lblWords, lblLines;
    private UndoManager undoManager = new UndoManager();
    private ProgressLabel progressLabel;
    private JFileChooser fileChooser;
    private File currentFile;

    public Practica3() {
        setTitle("Editor/Conversor de Texto - Práctica ");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 600);
        setLocationRelativeTo(null);

        // Layout principal
        Container cp = getContentPane();
        cp.setLayout(new BorderLayout(5, 5));
        ((JPanel) cp).setBorder(new EmptyBorder(6, 6, 6, 6));

        // --- Barra de herramientas superior ---
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);

        // Botones de transformación
        toolBar.add(makeButton("Mayúsculas", "Convertir a MAYÚSCULAS", e -> transformSelectedOrAll(TextAction.UPPER)));
        toolBar.add(makeButton("Minúsculas", "Convertir a minúsculas", e -> transformSelectedOrAll(TextAction.LOWER)));
        toolBar.add(makeButton("Invertir", "Invertir texto", e -> transformSelectedOrAll(TextAction.REVERSE)));
        toolBar.add(makeButton("Quitar dobles espacios", "Eliminar espacios dobles", e -> transformSelectedOrAll(TextAction.REMOVE_DOUBLE_SPACES)));

        toolBar.addSeparator();

        // Botones de estilo (negrita, cursiva, color)
        toolBar.add(makeButton("B", "Negrita", e -> toggleStyle(StyleConstants.CharacterConstants.Bold)));
        toolBar.add(makeButton("I", "Cursiva", e -> toggleStyle(StyleConstants.CharacterConstants.Italic)));
        JButton colorBtn = makeButton("Color", "Cambiar color del texto seleccionado", e -> changeColor());
        toolBar.add(colorBtn);

        toolBar.addSeparator();

        // Buscar / Reemplazar
        toolBar.add(makeButton("Buscar/Reemplazar", "Buscar y reemplazar", e -> openFindReplaceDialog()));

        toolBar.addSeparator();

        // Undo/Redo
        JButton undoBtn = makeButton("Deshacer", "Ctrl+Z", e -> doUndo());
        JButton redoBtn = makeButton("Rehacer", "Ctrl+Y", e -> doRedo());
        toolBar.add(undoBtn);
        toolBar.add(redoBtn);

        cp.add(toolBar, BorderLayout.NORTH);

        // --- Área de texto central (JTextPane para estilos) ---
        textPane = new JTextPane();
        textPane.setContentType("text/plain");
        textPane.setFont(new Font("SansSerif", Font.PLAIN, 14));
        JScrollPane scroll = new JScrollPane(textPane);
        cp.add(scroll, BorderLayout.CENTER);

        // Añadimos Undoable edit listener para UndoManager
        textPane.getDocument().addUndoableEditListener(e -> {
            undoManager.addEdit(e.getEdit());
            updateUndoRedoButtons(undoBtn, redoBtn);
            updateStatus();
        });

        // Document listener para actualizar contadores
        textPane.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { updateStatus(); }
            public void removeUpdate(DocumentEvent e) { updateStatus(); }
            public void changedUpdate(DocumentEvent e) { updateStatus(); }
        });

        // Selección con ratón -> se detecta automáticamente con JTextPane,
        // añadimos menú contextual
        textPane.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) showContextMenu(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) showContextMenu(e);
            }
        });

        // --- Menú superior (para accesos + atajos) ---
        JMenuBar menuBar = new JMenuBar();
        JMenu mArchivo = new JMenu("Archivo");
        
        JMenuItem miNuevo = new JMenuItem("Nuevo");
        miNuevo.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_DOWN_MASK));
        miNuevo.addActionListener(e -> newFile());
        mArchivo.add(miNuevo);

        JMenuItem miAbrir = new JMenuItem("Abrir...");
        miAbrir.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
        miAbrir.addActionListener(e -> openFile());
        mArchivo.add(miAbrir);

        JMenuItem miGuardar = new JMenuItem("Guardar");
        miGuardar.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK));
        miGuardar.addActionListener(e -> saveFile());
        mArchivo.add(miGuardar);

        JMenuItem miGuardarComo = new JMenuItem("Guardar como...");
        miGuardarComo.addActionListener(e -> saveFileAs());
        mArchivo.add(miGuardarComo);

        mArchivo.addSeparator();

        JMenuItem miSalir = new JMenuItem("Salir");
        miSalir.addActionListener(e -> dispose());
        mArchivo.add(miSalir);
        
        menuBar.add(mArchivo);

        JMenu mEditar = new JMenu("Editar");
        JMenuItem miDeshacer = new JMenuItem("Deshacer");
        miDeshacer.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK));
        miDeshacer.addActionListener(e -> doUndo());
        mEditar.add(miDeshacer);

        JMenuItem miRehacer = new JMenuItem("Rehacer");
        miRehacer.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK));
        miRehacer.addActionListener(e -> doRedo());
        mEditar.add(miRehacer);

        JMenuItem miCortar = new JMenuItem("Cortar");
        miCortar.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_X, InputEvent.CTRL_DOWN_MASK));
        miCortar.addActionListener(e -> textPane.cut());
        mEditar.add(miCortar);

        JMenuItem miCopiar = new JMenuItem("Copiar");
        miCopiar.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK));
        miCopiar.addActionListener(e -> textPane.copy());
        mEditar.add(miCopiar);

        JMenuItem miPegar = new JMenuItem("Pegar");
        miPegar.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK));
        miPegar.addActionListener(e -> textPane.paste());
        mEditar.add(miPegar);

        JMenuItem miBuscar = new JMenuItem("Buscar/Reemplazar");
        miBuscar.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK));
        miBuscar.addActionListener(e -> openFindReplaceDialog());
        mEditar.add(miBuscar);

        menuBar.add(mEditar);
        setJMenuBar(menuBar);

        // --- Barra de estado inferior ---
        JPanel statusBar = new JPanel(new BorderLayout());
        JPanel countersPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 2));
        lblChars = new JLabel("Chars: 0");
        lblWords = new JLabel("Words: 0");
        lblLines = new JLabel("Lines: 1");
        countersPanel.add(lblChars);
        countersPanel.add(new JSeparator(SwingConstants.VERTICAL));
        countersPanel.add(lblWords);
        countersPanel.add(new JSeparator(SwingConstants.VERTICAL));
        countersPanel.add(lblLines);

        // Añadir ProgressLabel a la barra de estado
        progressLabel = new ProgressLabel();
        statusBar.add(countersPanel, BorderLayout.WEST);
        statusBar.add(progressLabel, BorderLayout.EAST);

        cp.add(statusBar, BorderLayout.SOUTH);

        // Inicializar atajos directos (InputMap/ActionMap) si se desea
        setupKeyBindings();

        // Inicializar estado botones Undo/Redo
        updateUndoRedoButtons(undoBtn, redoBtn);

        setVisible(true);
    }

    // ---------- COMPONENTE PROGRESSLABEL ----------
    
    /**
     * Componente personalizado ProgressLabel que combina JLabel y JProgressBar
     */
    class ProgressLabel extends JPanel {
        private JLabel label;
        private JProgressBar progressBar;
        private JLabel statusIcon;
        
        public enum State { IDLE, WORKING, DONE, ERROR }
        
        public ProgressLabel() {
            setLayout(new BorderLayout(5, 0));
            setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
            
            // Icono de estado
            statusIcon = new JLabel();
            statusIcon.setPreferredSize(new Dimension(16, 16));
            
            // Etiqueta de texto
            label = new JLabel("Listo");
            
            // Barra de progreso
            progressBar = new JProgressBar(0, 100);
            progressBar.setPreferredSize(new Dimension(150, 16));
            progressBar.setVisible(false);
            progressBar.setStringPainted(true); // Mostrar porcentaje
            
            JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
            leftPanel.add(statusIcon);
            leftPanel.add(label);
            
            add(leftPanel, BorderLayout.WEST);
            add(progressBar, BorderLayout.EAST);
            
            setState(State.IDLE);
        }
        
        public void setState(State state) {
            switch (state) {
                case IDLE:
                    label.setText("Listo");
                    progressBar.setVisible(false);
                    statusIcon.setText("●");
                    statusIcon.setForeground(Color.GRAY);
                    break;
                case WORKING:
                    progressBar.setVisible(true);
                    progressBar.setValue(0);
                    statusIcon.setText("⟳");
                    statusIcon.setForeground(Color.BLUE);
                    break;
                case DONE:
                    label.setText("Completado");
                    progressBar.setValue(100);
                    progressBar.setVisible(true);
                    statusIcon.setText("✓");
                    statusIcon.setForeground(Color.GREEN);
                    
                    // Ocultar progreso después de 3 segundos
                    Timer timer = new Timer(3000, e -> {
                        progressBar.setVisible(false);
                        label.setText("Listo");
                        statusIcon.setText("●");
                        statusIcon.setForeground(Color.GRAY);
                    });
                    timer.setRepeats(false);
                    timer.start();
                    break;
                case ERROR:
                    label.setText("Error");
                    progressBar.setVisible(false);
                    statusIcon.setText("✗");
                    statusIcon.setForeground(Color.RED);
                    break;
            }
        }
        
        public void setProgress(int value) {
            progressBar.setValue(value);
        }
        
        public void setOperationText(String text) {
            label.setText(text);
        }
        
        public void reset() {
            setState(State.IDLE);
            progressBar.setValue(0);
        }
    }

    // ---------- MÉTODOS DE ARCHIVO ----------

    private void newFile() {
        if (confirmSave()) {
            textPane.setText("");
            currentFile = null;
            setTitle("Editor/Conversor de Texto - Práctica 2 [Nuevo archivo]");
            progressLabel.reset();
        }
    }

    private void openFile() {
        if (confirmSave()) {
            if (fileChooser == null) {
                fileChooser = new JFileChooser();
                fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Archivos de texto", "txt"));
            }
            
            if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                currentFile = fileChooser.getSelectedFile();
                loadFileWithProgress(currentFile);
            }
        }
    }

    private void saveFile() {
        if (currentFile == null) {
            saveFileAs();
        } else {
            saveToFile(currentFile);
        }
    }

    private void saveFileAs() {
        if (fileChooser == null) {
            fileChooser = new JFileChooser();
            fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Archivos de texto", "txt"));
        }
        
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            currentFile = fileChooser.getSelectedFile();
            if (!currentFile.getName().toLowerCase().endsWith(".txt")) {
                currentFile = new File(currentFile.getAbsolutePath() + ".txt");
            }
            saveToFile(currentFile);
        }
    }

    private boolean confirmSave() {
        if (textPane.getDocument().getLength() > 0) {
            int option = JOptionPane.showConfirmDialog(this, 
                "¿Desea guardar los cambios?", "Guardar cambios", 
                JOptionPane.YES_NO_CANCEL_OPTION);
            
            if (option == JOptionPane.YES_OPTION) {
                saveFile();
                return true;
            } else if (option == JOptionPane.CANCEL_OPTION) {
                return false;
            }
        }
        return true;
    }

    // ---------- CARGA DE ARCHIVO CON PROGRESO EN PROGRESSLABEL ----------

    private void loadFileWithProgress(File file) {
        progressLabel.setState(ProgressLabel.State.WORKING);
        progressLabel.setOperationText("Cargando: " + file.getName());
        
        SwingWorker<String, ProgressUpdate> worker = new SwingWorker<String, ProgressUpdate>() {
            @Override
            protected String doInBackground() throws Exception {
                StringBuilder content = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                    String line;
                    int lineCount = 0;
                    long totalSize = file.length();
                    long readSize = 0;
                    
                    // Contar líneas totales para progreso más preciso
                    int totalLines = countLines(file);
                    int linesProcessed = 0;
                    
                    // Volver al inicio del archivo
                    reader.close();
                    BufferedReader reader2 = new BufferedReader(new FileReader(file));
                    
                    while ((line = reader2.readLine()) != null) {
                        content.append(line).append("\n");
                        lineCount++;
                        linesProcessed++;
                        readSize += line.length() + 1;
                        
                        // Calcular progreso basado en líneas procesadas
                        int lineProgress = totalLines > 0 ? (int) ((linesProcessed * 100) / totalLines) : 0;
                        
                        // Calcular progreso basado en bytes leídos
                        int byteProgress = totalSize > 0 ? (int) ((readSize * 100) / totalSize) : 0;
                        
                        // Usar el progreso más bajo para ser conservador
                        int progress = Math.min(lineProgress, byteProgress);
                        
                        String details = "Línea " + lineCount;
                        if (totalSize > 0) {
                            details += " - " + (readSize / 1024) + "KB/" + (totalSize / 1024) + "KB";
                        }
                        
                        publish(new ProgressUpdate(progress, details));
                        
                        // Pequeña pausa para hacer visible el progreso en archivos pequeños
                        if (totalSize < 50000) { // Menos de 50KB
                            Thread.sleep(5);
                        }
                    }
                    publish(new ProgressUpdate(100, "Completado"));
                }
                return content.toString();
            }
            
            @Override
            protected void process(java.util.List<ProgressUpdate> chunks) {
                if (!chunks.isEmpty()) {
                    ProgressUpdate update = chunks.get(chunks.size() - 1);
                    progressLabel.setProgress(update.progress);
                    progressLabel.setOperationText("Cargando: " + file.getName() + " (" + update.details + ")");
                }
            }
            
            @Override
            protected void done() {
                try {
                    String content = get();
                    textPane.setText(content);
                    setTitle("Editor/Conversor de Texto - Práctica 2 [" + file.getName() + "]");
                    progressLabel.setState(ProgressLabel.State.DONE);
                    progressLabel.setOperationText("Carga completada: " + file.getName());
                    
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(Practica3.this, 
                        "Error al cargar el archivo: " + ex.getMessage(), 
                        "Error", JOptionPane.ERROR_MESSAGE);
                    progressLabel.setState(ProgressLabel.State.ERROR);
                    progressLabel.setOperationText("Error cargando: " + file.getName());
                }
            }
        };
        worker.execute();
    }

    // Clase auxiliar para actualizaciones de progreso
    private class ProgressUpdate {
        int progress;
        String details;
        
        ProgressUpdate(int progress, String details) {
            this.progress = progress;
            this.details = details;
        }
    }

    // Método para contar líneas en un archivo (para progreso más preciso)
    private int countLines(File file) throws IOException {
        int lines = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            while (reader.readLine() != null) {
                lines++;
            }
        }
        return lines;
    }

    private void saveToFile(File file) {
        progressLabel.setState(ProgressLabel.State.WORKING);
        progressLabel.setOperationText("Guardando: " + file.getName());
        
        SwingWorker<Boolean, Integer> worker = new SwingWorker<Boolean, Integer>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                try (FileWriter writer = new FileWriter(file)) {
                    String content = textPane.getText();
                    int totalLength = content.length();
                    int chunkSize = Math.max(totalLength / 50, 100); // Actualizar ~50 veces
                    
                    for (int i = 0; i < totalLength; i += chunkSize) {
                        int end = Math.min(i + chunkSize, totalLength);
                        writer.write(content.substring(i, end));
                        
                        int progress = (int) ((end * 100) / totalLength);
                        publish(Math.min(progress, 100));
                        
                        // Pequeña pausa para hacer visible el progreso
                        if (totalLength < 100000) {
                            Thread.sleep(10);
                        }
                    }
                    publish(100);
                    return true;
                }
            }
            
            @Override
            protected void process(java.util.List<Integer> chunks) {
                if (!chunks.isEmpty()) {
                    progressLabel.setProgress(chunks.get(chunks.size() - 1));
                    progressLabel.setOperationText("Guardando: " + file.getName() + 
                        " (" + chunks.get(chunks.size() - 1) + "%)");
                }
            }
            
            @Override
            protected void done() {
                try {
                    if (get()) {
                        setTitle("Editor/Conversor de Texto - Práctica 2 [" + file.getName() + "]");
                        progressLabel.setState(ProgressLabel.State.DONE);
                        progressLabel.setOperationText("Guardado: " + file.getName());
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(Practica3.this, 
                        "Error al guardar el archivo: " + ex.getMessage(), 
                        "Error", JOptionPane.ERROR_MESSAGE);
                    progressLabel.setState(ProgressLabel.State.ERROR);
                    progressLabel.setOperationText("Error guardando: " + file.getName());
                }
            }
        };
        worker.execute();
    }

    // ---------- UTILIDADES Y ACCIONES ----------

    private JButton makeButton(String text, String tooltip, ActionListener action) {
        JButton b = new JButton(text);
        b.setToolTipText(tooltip);
        b.addActionListener(action);
        return b;
    }

    // Enum para transformaciones
    private enum TextAction { UPPER, LOWER, REVERSE, REMOVE_DOUBLE_SPACES }

    // Aplica una transformación a la selección (si existe) o al texto completo
    private void transformSelectedOrAll(TextAction action) {
        try {
            Document doc = textPane.getDocument();
            int start = textPane.getSelectionStart();
            int end = textPane.getSelectionEnd();
            boolean hasSelection = start != end;

            if (!hasSelection) {
                start = 0;
                end = doc.getLength();
            }

            String original = doc.getText(start, end - start);
            String transformed = original;
            switch (action) {
                case UPPER:
                    transformed = original.toUpperCase();
                    break;
                case LOWER:
                    transformed = original.toLowerCase();
                    break;
                case REVERSE:
                    transformed = new StringBuilder(original).reverse().toString();
                    break;
                case REMOVE_DOUBLE_SPACES:
                    transformed = original.replaceAll("\\s{2,}", " ");
                    break;
            }

            // Replace
            doc.remove(start, end - start);
            doc.insertString(start, transformed, null);

        } catch (BadLocationException ex) {
            ex.printStackTrace();
        }
    }

    // Toggle style (bold/italic) on selected text using StyledDocument
    private void toggleStyle(Object styleConstant) {
        StyledDocument sd = textPane.getStyledDocument();
        int start = textPane.getSelectionStart();
        int end = textPane.getSelectionEnd();
        if (start == end) return; // no selection

        Element elem = sd.getCharacterElement(start);
        AttributeSet as = elem.getAttributes();

        boolean current = false;
        if (styleConstant == StyleConstants.CharacterConstants.Bold) {
            current = StyleConstants.isBold(as);
        } else if (styleConstant == StyleConstants.CharacterConstants.Italic) {
            current = StyleConstants.isItalic(as);
        }

        MutableAttributeSet attr = new SimpleAttributeSet();
        if (styleConstant == StyleConstants.CharacterConstants.Bold) {
            StyleConstants.setBold(attr, !current);
        } else if (styleConstant == StyleConstants.CharacterConstants.Italic) {
            StyleConstants.setItalic(attr, !current);
        }

        sd.setCharacterAttributes(start, end - start, attr, false);
    }

    private void changeColor() {
        Color chosen = JColorChooser.showDialog(this, "Selecciona color", Color.BLACK);
        if (chosen == null) return;
        StyledDocument sd = textPane.getStyledDocument();
        int start = textPane.getSelectionStart();
        int end = textPane.getSelectionEnd();
        if (start == end) return;
        MutableAttributeSet attr = new SimpleAttributeSet();
        StyleConstants.setForeground(attr, chosen);
        sd.setCharacterAttributes(start, end - start, attr, false);
    }

    // --- Undo / Redo ---
    private void doUndo() {
        if (undoManager.canUndo()) {
            undoManager.undo();
            updateStatus();
        }
    }

    private void doRedo() {
        if (undoManager.canRedo()) {
            undoManager.redo();
            updateStatus();
        }
    }

    private void updateUndoRedoButtons(JButton undoBtn, JButton redoBtn) {
        undoBtn.setEnabled(undoManager.canUndo());
        redoBtn.setEnabled(undoManager.canRedo());
    }

    // --- Búsqueda y reemplazo simple ---
    private void openFindReplaceDialog() {
        JDialog dlg = new JDialog(this, "Buscar y reemplazar", true);
        dlg.setSize(420, 160);
        dlg.setLocationRelativeTo(this);
        dlg.setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.fill = GridBagConstraints.HORIZONTAL;

        JLabel lblFind = new JLabel("Buscar:");
        c.gridx = 0; c.gridy = 0; c.weightx = 0.2;
        dlg.add(lblFind, c);

        JTextField txtFind = new JTextField();
        c.gridx = 1; c.gridy = 0; c.weightx = 0.8;
        dlg.add(txtFind, c);

        JLabel lblReplace = new JLabel("Reemplazar:");
        c.gridx = 0; c.gridy = 1; c.weightx = 0.2;
        dlg.add(lblReplace, c);

        JTextField txtReplace = new JTextField();
        c.gridx = 1; c.gridy = 1; c.weightx = 0.8;
        dlg.add(txtReplace, c);

        JPanel pButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnFindNext = new JButton("Buscar siguiente");
        JButton btnReplace = new JButton("Reemplazar");
        JButton btnReplaceAll = new JButton("Reemplazar todo");
        pButtons.add(btnFindNext);
        pButtons.add(btnReplace);
        pButtons.add(btnReplaceAll);

        c.gridx = 0; c.gridy = 2; c.gridwidth = 2;
        dlg.add(pButtons, c);

        // Acción: buscar siguiente
        btnFindNext.addActionListener(e -> {
            String toFind = txtFind.getText();
            if (toFind.isEmpty()) return;

            String text = textPane.getText();
            int startPos;

            // Si hay selección, empezamos la búsqueda desde el final de la selección.
            int selStart = textPane.getSelectionStart();
            int selEnd = textPane.getSelectionEnd();
            if (selStart != selEnd) {
                startPos = selEnd; // buscar después de la selección actual
            } else {
                startPos = textPane.getCaretPosition();
            }

            int idx = text.indexOf(toFind, startPos);
            if (idx == -1 && startPos > 0) {
                // no encontrado después del caret -> buscar desde el principio
                idx = text.indexOf(toFind);
            }
            if (idx != -1) {
                textPane.requestFocus();
                textPane.select(idx, idx + toFind.length());
            } else {
                JOptionPane.showMessageDialog(dlg, "No encontrado");
            }
        });

        // Reemplazar: si la selección coincide con el término de búsqueda, reemplaza.
        // Si no coincide, busca la siguiente aparición y la reemplaza si se encuentra.
        btnReplace.addActionListener(e -> {
            String toFind = txtFind.getText();
            String toRepl = txtReplace.getText();
            if (toFind.isEmpty()) return;

            String selection = textPane.getSelectedText();
            if (selection != null && selection.equals(toFind)) {
                // Reemplaza la selección actual
                textPane.replaceSelection(toRepl);
                // Tras reemplazar, dejamos el cursor al final del reemplazo
                int newPos = textPane.getSelectionStart() + toRepl.length();
                textPane.select(newPos, newPos);
                textPane.requestFocus();
            } else {
                // Si la selección no coincide, buscamos la siguiente ocurrencia e intentamos reemplazarla
                btnFindNext.doClick(); // selecciona la siguiente ocurrencia (si existe)
                // Ahora comprueba si la selección recién encontrada coincide y reemplaza
                String newSelection = textPane.getSelectedText();
                if (newSelection != null && newSelection.equals(toFind)) {
                    textPane.replaceSelection(toRepl);
                    int newPos = textPane.getSelectionStart() + toRepl.length();
                    textPane.select(newPos, newPos);
                    textPane.requestFocus();
                } else {
                    // si tras buscar no hay coincidencia, informa al usuario (opcional)
                    // JOptionPane.showMessageDialog(dlg, "No hay coincidencia para reemplazar.");
                }
            }
        });

        // Reemplazar todo
        btnReplaceAll.addActionListener(e -> {
            String find = txtFind.getText();
            String repl = txtReplace.getText();
            if (find.isEmpty()) return;
            String text = textPane.getText();
            text = text.replace(find, repl);
            textPane.setText(text);
        });

        dlg.setVisible(true);
    }
    // --- Menú contextual para cortar/copiar/pegar ---
    private void showContextMenu(MouseEvent e) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem miCut = new JMenuItem("Cortar");
        miCut.addActionListener(a -> textPane.cut());
        JMenuItem miCopy = new JMenuItem("Copiar");
        miCopy.addActionListener(a -> textPane.copy());
        JMenuItem miPaste = new JMenuItem("Pegar");
        miPaste.addActionListener(a -> textPane.paste());

        menu.add(miCut);
        menu.add(miCopy);
        menu.add(miPaste);
        menu.show(e.getComponent(), e.getX(), e.getY());
    }

    // --- Contadores: caracteres, palabras, líneas ---
    private void updateStatus() {
        String text = textPane.getText();
        lblChars.setText("Chars: " + text.length());

        // words = tokens separated by whitespace
        String trimmed = text.trim();
        int words = 0;
        if (!trimmed.isEmpty()) {
            words = trimmed.split("\\s+").length;
        }
        lblWords.setText("Words: " + words);

        // lines
        int lines = text.isEmpty() ? 0 : text.split("\r\n|\r|\n").length;
        lblLines.setText("Lines: " + Math.max(lines, 1));
    }

    // --- Key bindings (extras): Ctrl+F already in menu, but example of binding ESC to clear selection ---
    private void setupKeyBindings() {
        InputMap im = textPane.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap am = textPane.getActionMap();

        // Escape clears selection
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "clearSelection");
        am.put("clearSelection", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                textPane.select(0, 0);
            }
        });
    }

    // ---------- MAIN ----------
    public static void main(String[] args) {
        // Ejecutar en EDT
        SwingUtilities.invokeLater(() -> new Practica3());
    }
}