package practica3_GVA;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.*;
import javax.swing.text.*;
import javax.swing.text.rtf.RTFEditorKit;
import javax.swing.undo.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;

// ============================================
// CAPA NUI - Interfaces y Enumeraciones
// ============================================

enum NuiCommand {
    NUEVO_DOCUMENTO,
    ABRIR_DOCUMENTO,
    GUARDAR_DOCUMENTO,
    APLICAR_NEGRITA,
    APLICAR_CURSIVA,
    COLOR_ROJO,
    COLOR_AZUL,
    DICTAR_TEXTO
}

interface NuiListener {
    void onCommand(NuiCommand cmd, String payload);
}

class NuiController {
    private java.util.List<NuiListener> listeners = new java.util.ArrayList<>();
    
    public void addListener(NuiListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }
    
    public void fireCommand(NuiCommand cmd, String payload) {
        System.out.println("[NUI] Comando recibido: " + cmd + 
                          (payload != null ? " (" + payload + ")" : ""));
        
        for (NuiListener listener : listeners) {
            listener.onCommand(cmd, payload);
        }
    }
    
    public void fireCommand(NuiCommand cmd) {
        fireCommand(cmd, null);
    }
}

class SimuladorVoz extends JPanel {
    private JTextField campoComando;
    private JButton btnEjecutar;
    private NuiController controller;
    private java.util.Map<String, NuiCommand> mapaComandos;
    
    public SimuladorVoz(NuiController controller) {
        this.controller = controller;
        initComponents();
        initMapaComandos();
    }
    
    private void initComponents() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createTitledBorder("Simulador de Voz NUI"));
        
        campoComando = new JTextField();
        campoComando.setToolTipText("Escribe comandos: nuevo, abrir, guardar, negrita, cursiva, rojo, azul, dictar");
        
        btnEjecutar = new JButton("Ejecutar Comando");
        btnEjecutar.addActionListener(e -> ejecutarComando());
        
        campoComando.addActionListener(e -> ejecutarComando());
        
        add(campoComando, BorderLayout.CENTER);
        add(btnEjecutar, BorderLayout.EAST);
        
        JPanel panelAyuda = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panelAyuda.add(new JLabel("Comandos: nuevo, abrir, guardar, negrita, cursiva, rojo, azul, dictar"));
        add(panelAyuda, BorderLayout.SOUTH);
    }
    
    private void initMapaComandos() {
        mapaComandos = new java.util.HashMap<>();
        
        mapaComandos.put("nuevo", NuiCommand.NUEVO_DOCUMENTO);
        mapaComandos.put("nuevo documento", NuiCommand.NUEVO_DOCUMENTO);
        mapaComandos.put("nuevo doc", NuiCommand.NUEVO_DOCUMENTO);
        
        mapaComandos.put("abrir", NuiCommand.ABRIR_DOCUMENTO);
        mapaComandos.put("abrir documento", NuiCommand.ABRIR_DOCUMENTO);
        mapaComandos.put("abrir archivo", NuiCommand.ABRIR_DOCUMENTO);
        
        mapaComandos.put("guardar", NuiCommand.GUARDAR_DOCUMENTO);
        mapaComandos.put("guardar documento", NuiCommand.GUARDAR_DOCUMENTO);
        mapaComandos.put("guardar archivo", NuiCommand.GUARDAR_DOCUMENTO);
        
        mapaComandos.put("negrita", NuiCommand.APLICAR_NEGRITA);
        mapaComandos.put("bold", NuiCommand.APLICAR_NEGRITA);
        
        mapaComandos.put("cursiva", NuiCommand.APLICAR_CURSIVA);
        mapaComandos.put("italica", NuiCommand.APLICAR_CURSIVA);
        mapaComandos.put("italic", NuiCommand.APLICAR_CURSIVA);
        
        mapaComandos.put("rojo", NuiCommand.COLOR_ROJO);
        mapaComandos.put("color rojo", NuiCommand.COLOR_ROJO);
        
        mapaComandos.put("azul", NuiCommand.COLOR_AZUL);
        mapaComandos.put("color azul", NuiCommand.COLOR_AZUL);
        mapaComandos.put("blue", NuiCommand.COLOR_AZUL);
        
        mapaComandos.put("dictar", NuiCommand.DICTAR_TEXTO);
        mapaComandos.put("dictar texto", NuiCommand.DICTAR_TEXTO);
        mapaComandos.put("escribir", NuiCommand.DICTAR_TEXTO);
    }
    
    private void ejecutarComando() {
        String texto = campoComando.getText().trim().toLowerCase();
        
        if (texto.isEmpty()) {
            JOptionPane.showMessageDialog(this, 
                "Escribe un comando primero", 
                "Error", 
                JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        NuiCommand comando = mapaComandos.get(texto);
        
        if (comando == null) {
            for (java.util.Map.Entry<String, NuiCommand> entry : mapaComandos.entrySet()) {
                if (texto.contains(entry.getKey())) {
                    comando = entry.getValue();
                    break;
                }
            }
        }
        
        if (comando != null) {
            if (comando == NuiCommand.DICTAR_TEXTO) {
                String payload = campoComando.getText();
                controller.fireCommand(comando, payload);
            } else {
                controller.fireCommand(comando);
            }
            campoComando.setText("");
        } else {
            JOptionPane.showMessageDialog(this, 
                "Comando no reconocido: " + texto + "\nUsa: nuevo, abrir, guardar, negrita, cursiva, rojo, azul, dictar", 
                "Comando desconocido", 
                JOptionPane.ERROR_MESSAGE);
        }
    }
}

// ============================================
// EDITOR PRINCIPAL CON NUI INTEGRADO
// ============================================

public class practica3_3 extends JFrame implements NuiListener {

    private JTextPane textPane;
    private JLabel lblChars, lblWords, lblLines;
    private UndoManager undoManager = new UndoManager();
    private ProgressLabel progressLabel;
    private JFileChooser fileChooser;
    private File currentFile;
    
    // ===== NUEVO: Componentes NUI =====
    private NuiController nuiController;
    private SimuladorVoz simuladorVoz;
    private JLabel lblNuiStatus; // Para mostrar estado de comandos NUI
    private int contadorComandosNUI = 0;

    public practica3_3() {
        setTitle("Editor/Conversor de Texto con NUI - Práctica UT2 RA2");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 650); // Aumentado para caber el simulador NUI
        setLocationRelativeTo(null);

        Container cp = getContentPane();
        cp.setLayout(new BorderLayout(5,5));
        ((JPanel) cp).setBorder(new EmptyBorder(6,6,6,6));

        // ===== NUEVO: Inicializar capa NUI =====
        initNuiLayer();
        
        // ===== NUEVO: Panel superior con NUI y toolbar =====
        JPanel panelSuperior = new JPanel(new BorderLayout());
        
        // 1. Simulador de voz NUI
        panelSuperior.add(simuladorVoz, BorderLayout.NORTH);
        
        // 2. Toolbar original
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);

        toolBar.add(makeButton("Mayúsculas", e -> transformSelectedOrAll(TextAction.UPPER)));
        toolBar.add(makeButton("Minúsculas", e -> transformSelectedOrAll(TextAction.LOWER)));
        toolBar.add(makeButton("Invertir", e -> transformSelectedOrAll(TextAction.REVERSE)));
        toolBar.add(makeButton("Quitar dobles espacios", e -> transformSelectedOrAll(TextAction.REMOVE_DOUBLE_SPACES)));

        toolBar.addSeparator();
        toolBar.add(makeButton("B", e -> toggleStyle(StyleConstants.CharacterConstants.Bold)));
        toolBar.add(makeButton("I", e -> toggleStyle(StyleConstants.CharacterConstants.Italic)));
        toolBar.add(makeButton("U", e -> toggleStyle(StyleConstants.CharacterConstants.Underline)));
        toolBar.add(makeButton("Color", e -> changeColor()));

        toolBar.addSeparator();
        toolBar.add(makeButton("Buscar/Reemplazar", e -> openFindReplaceDialog()));

        toolBar.addSeparator();
        JButton undoBtn = makeButton("Deshacer", e -> doUndo());
        JButton redoBtn = makeButton("Rehacer", e -> doRedo());
        toolBar.add(undoBtn);
        toolBar.add(redoBtn);
        
        panelSuperior.add(toolBar, BorderLayout.CENTER);
        cp.add(panelSuperior, BorderLayout.NORTH);

        // --- Área de texto ---
        textPane = new JTextPane();
        JScrollPane scroll = new JScrollPane(textPane);
        cp.add(scroll, BorderLayout.CENTER);

        textPane.getDocument().addUndoableEditListener(e -> {
            undoManager.addEdit(e.getEdit());
            updateUndoRedoButtons(undoBtn, redoBtn);
            updateStatus();
        });

        textPane.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { updateStatus(); }
            public void removeUpdate(DocumentEvent e) { updateStatus(); }
            public void changedUpdate(DocumentEvent e) { updateStatus(); }
        });

        textPane.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) { if(e.isPopupTrigger()) showContextMenu(e); }
            public void mouseReleased(MouseEvent e) { if(e.isPopupTrigger()) showContextMenu(e); }
        });

        // --- Barra de estado MODIFICADA para incluir NUI ---
        JPanel statusBar = new JPanel(new BorderLayout());
        JPanel countersPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12,2));
        lblChars = new JLabel("Chars: 0");
        lblWords = new JLabel("Words: 0");
        lblLines = new JLabel("Lines: 1");
        
        // ===== NUEVO: Contador de comandos NUI =====
        lblNuiStatus = new JLabel(" | NUI: 0 cmd");
        lblNuiStatus.setForeground(new Color(0, 100, 0)); // Verde oscuro
        
        countersPanel.add(lblChars);
        countersPanel.add(new JSeparator(SwingConstants.VERTICAL));
        countersPanel.add(lblWords);
        countersPanel.add(new JSeparator(SwingConstants.VERTICAL));
        countersPanel.add(lblLines);
        countersPanel.add(new JSeparator(SwingConstants.VERTICAL));
        countersPanel.add(lblNuiStatus); // Añadido

        progressLabel = new ProgressLabel();
        statusBar.add(countersPanel, BorderLayout.WEST);
        statusBar.add(progressLabel, BorderLayout.EAST);
        cp.add(statusBar, BorderLayout.SOUTH);

        // --- Menú superior MODIFICADO para incluir NUI ---
        JMenuBar menuBar = new JMenuBar();
        JMenu menuArchivo = new JMenu("Archivo");

        JMenuItem miNuevo = new JMenuItem("Nuevo");
        miNuevo.addActionListener(e -> newFile());
        menuArchivo.add(miNuevo);

        JMenuItem miAbrir = new JMenuItem("Abrir...");
        miAbrir.addActionListener(e -> openFile());
        menuArchivo.add(miAbrir);

        JMenuItem miGuardar = new JMenuItem("Guardar");
        miGuardar.addActionListener(e -> saveFile());
        menuArchivo.add(miGuardar);

        JMenuItem miGuardarComo = new JMenuItem("Guardar como...");
        miGuardarComo.addActionListener(e -> saveFileAs());
        menuArchivo.add(miGuardarComo);

        menuArchivo.addSeparator();
        JMenuItem miSalir = new JMenuItem("Salir");
        miSalir.addActionListener(e -> dispose());
        menuArchivo.add(miSalir);

        menuBar.add(menuArchivo);

        JMenu menuEditar = new JMenu("Editar");
        JMenuItem miDeshacer = new JMenuItem("Deshacer");
        miDeshacer.addActionListener(e -> doUndo());
        menuEditar.add(miDeshacer);

        JMenuItem miRehacer = new JMenuItem("Rehacer");
        miRehacer.addActionListener(e -> doRedo());
        menuEditar.add(miRehacer);

        JMenuItem miCortar = new JMenuItem("Cortar");
        miCortar.addActionListener(e -> textPane.cut());
        menuEditar.add(miCortar);

        JMenuItem miCopiar = new JMenuItem("Copiar");
        miCopiar.addActionListener(e -> textPane.copy());
        menuEditar.add(miCopiar);

        JMenuItem miPegar = new JMenuItem("Pegar");
        miPegar.addActionListener(e -> textPane.paste());
        menuEditar.add(miPegar);

        JMenuItem miBuscar = new JMenuItem("Buscar/Reemplazar");
        miBuscar.addActionListener(e -> openFindReplaceDialog());
        menuEditar.add(miBuscar);

        // ===== NUEVO: Menú NUI =====
        JMenu menuNUI = new JMenu("NUI");
        JMenuItem miComandosNUI = new JMenuItem("Mostrar Comandos NUI");
        miComandosNUI.addActionListener(e -> mostrarComandosNUI());
        menuNUI.add(miComandosNUI);
        
        JMenuItem miResetNUI = new JMenuItem("Reset Contador NUI");
        miResetNUI.addActionListener(e -> {
            contadorComandosNUI = 0;
            lblNuiStatus.setText(" | NUI: 0 cmd");
        });
        menuNUI.add(miResetNUI);
        
        menuBar.add(menuNUI);

        setJMenuBar(menuBar);
        setVisible(true);
    }

    // ===== NUEVO: Método para inicializar capa NUI =====
    private void initNuiLayer() {
        nuiController = new NuiController();
        nuiController.addListener(this);
        simuladorVoz = new SimuladorVoz(nuiController);
    }

    // ===== IMPLEMENTACIÓN DE NuiListener =====
    @Override
    public void onCommand(NuiCommand cmd, String payload) {
        SwingUtilities.invokeLater(() -> {
            contadorComandosNUI++;
            lblNuiStatus.setText(" | NUI: " + contadorComandosNUI + " cmd");
            
            switch (cmd) {
                case NUEVO_DOCUMENTO:
                    newFile();
                    mostrarMensajeNUI("Nuevo documento creado por comando de voz");
                    break;
                case ABRIR_DOCUMENTO:
                    openFile();
                    mostrarMensajeNUI("Abriendo documento por comando de voz");
                    break;
                case GUARDAR_DOCUMENTO:
                    saveFile();
                    mostrarMensajeNUI("Guardando documento por comando de voz");
                    break;
                case APLICAR_NEGRITA:
                    aplicarNegritaNUI();
                    mostrarMensajeNUI("Negrita aplicada por comando de voz");
                    break;
                case APLICAR_CURSIVA:
                    aplicarCursivaNUI();
                    mostrarMensajeNUI("Cursiva aplicada por comando de voz");
                    break;
                case COLOR_ROJO:
                    aplicarColorNUI(Color.RED);
                    mostrarMensajeNUI("Color rojo aplicado por comando de voz");
                    break;
                case COLOR_AZUL:
                    aplicarColorNUI(Color.BLUE);
                    mostrarMensajeNUI("Color azul aplicado por comando de voz");
                    break;
                case DICTAR_TEXTO:
                    dictarTextoNUI(payload != null ? payload : "[texto dictado]");
                    break;
            }
        });
    }
    
    // ===== NUEVO: Métodos auxiliares para NUI =====
    private void mostrarMensajeNUI(String mensaje) {
        progressLabel.setOperationText(mensaje);
        progressLabel.setState(ProgressLabel.State.DONE);
        System.out.println("[NUI] " + mensaje);
    }
    
    private void aplicarNegritaNUI() {
        int inicio = textPane.getSelectionStart();
        int fin = textPane.getSelectionEnd();
        
        if (inicio == fin) {
            // Si no hay selección, aplicar al texto completo
            inicio = 0;
            fin = textPane.getDocument().getLength();
        }
        
        if (inicio < fin) {
            StyledDocument sd = textPane.getStyledDocument();
            MutableAttributeSet attr = new SimpleAttributeSet();
            Element elem = sd.getCharacterElement(inicio);
            AttributeSet as = elem.getAttributes();
            StyleConstants.setBold(attr, !StyleConstants.isBold(as));
            sd.setCharacterAttributes(inicio, fin - inicio, attr, false);
        }
    }
    
    private void aplicarCursivaNUI() {
        int inicio = textPane.getSelectionStart();
        int fin = textPane.getSelectionEnd();
        
        if (inicio == fin) {
            inicio = 0;
            fin = textPane.getDocument().getLength();
        }
        
        if (inicio < fin) {
            StyledDocument sd = textPane.getStyledDocument();
            MutableAttributeSet attr = new SimpleAttributeSet();
            Element elem = sd.getCharacterElement(inicio);
            AttributeSet as = elem.getAttributes();
            StyleConstants.setItalic(attr, !StyleConstants.isItalic(as));
            sd.setCharacterAttributes(inicio, fin - inicio, attr, false);
        }
    }
    
    private void aplicarColorNUI(Color color) {
        int inicio = textPane.getSelectionStart();
        int fin = textPane.getSelectionEnd();
        
        if (inicio == fin) {
            inicio = 0;
            fin = textPane.getDocument().getLength();
        }
        
        if (inicio < fin) {
            StyledDocument sd = textPane.getStyledDocument();
            MutableAttributeSet attr = new SimpleAttributeSet();
            StyleConstants.setForeground(attr, color);
            sd.setCharacterAttributes(inicio, fin - inicio, attr, false);
        }
    }
    
    private void dictarTextoNUI(String texto) {
        try {
            int pos = textPane.getCaretPosition();
            textPane.getDocument().insertString(pos, texto + " ", null);
            mostrarMensajeNUI("Texto dictado: " + (texto.length() > 20 ? texto.substring(0, 20) + "..." : texto));
        } catch (BadLocationException ex) {
            ex.printStackTrace();
        }
    }
    
    private void mostrarComandosNUI() {
        String mensaje = "Comandos NUI implementados:\n\n" +
                        "• NUEVO_DOCUMENTO (nuevo, nuevo doc)\n" +
                        "• ABRIR_DOCUMENTO (abrir, abrir archivo)\n" +
                        "• GUARDAR_DOCUMENTO (guardar, guardar archivo)\n" +
                        "• APLICAR_NEGRITA (negrita, bold)\n" +
                        "• APLICAR_CURSIVA (cursiva, italic)\n" +
                        "• COLOR_ROJO (rojo, color rojo)\n" +
                        "• COLOR_AZUL (azul, color azul)\n" +
                        "• DICTAR_TEXTO (dictar, dictar texto)\n\n" +
                        "Comandos ejecutados: " + contadorComandosNUI;
        
        JOptionPane.showMessageDialog(this, mensaje, "Comandos NUI Disponibles", JOptionPane.INFORMATION_MESSAGE);
    }

    // ---------- Métodos de archivo con progreso (SIN CAMBIOS) ----------
    private void newFile() {
        textPane.setText("");
        currentFile = null;
        setTitle("Editor/Conversor de Texto con NUI - Práctica UT2 RA2 [Nuevo archivo]");
        progressLabel.reset();
    }

    private void saveFile() {
        if (currentFile == null) saveFileAs();
        else saveToFile(currentFile);
    }

    private void saveFileAs() {
        if (fileChooser == null) fileChooser = new JFileChooser();
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            currentFile = fileChooser.getSelectedFile();
            if(!currentFile.getAbsolutePath().toLowerCase().endsWith(".rtf")) 
                currentFile = new File(currentFile.getAbsolutePath() + ".rtf");
            saveToFile(currentFile);
        }
    }

    private void saveToFile(File file) {
        progressLabel.setState(ProgressLabel.State.WORKING);
        SwingWorker<Void, Integer> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                RTFEditorKit rtfKit = new RTFEditorKit();
                Document doc = textPane.getDocument();

                try (FileOutputStream out = new FileOutputStream(file)) {
                    rtfKit.write(out, doc, 0, doc.getLength());
                }

                for (int i = 1; i <= 100; i++) {
                    Thread.sleep(5);
                    publish(i);
                }

                return null;
            }

            @Override
            protected void process(java.util.List<Integer> chunks) {
                int val = chunks.get(chunks.size() - 1);
                progressLabel.setProgress(val);
                progressLabel.setOperationText("Guardando " + file.getName() + " (" + val + "%)");
            }

            @Override
            protected void done() {
                progressLabel.setState(ProgressLabel.State.DONE);
                setTitle("Editor/Conversor de Texto con NUI - Práctica UT2 RA2 [" + file.getName() + "]");
            }
        };
        worker.execute();
    }

    private void openFile() {
        if (fileChooser == null) fileChooser = new JFileChooser();
        if(fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION){
            currentFile = fileChooser.getSelectedFile();
            progressLabel.setState(ProgressLabel.State.WORKING);
            SwingWorker<Document,Integer> worker = new SwingWorker<>() {
                @Override
                protected Document doInBackground() throws Exception {
                    RTFEditorKit rtfKit = new RTFEditorKit();
                    Document doc = rtfKit.createDefaultDocument();

                    try (FileInputStream in = new FileInputStream(currentFile)) {
                        rtfKit.read(in, doc, 0);
                    }

                    for (int i = 1; i <= 100; i++) {
                        Thread.sleep(5);
                        publish(i);
                    }

                    return doc;
                }

                @Override
                protected void process(java.util.List<Integer> chunks) {
                    int val = chunks.get(chunks.size()-1);
                    progressLabel.setProgress(val);
                    progressLabel.setOperationText("Cargando "+currentFile.getName()+" ("+val+"%)");
                }

                @Override
                protected void done() {
                    try {
                        textPane.setDocument(get());
                        setTitle("Editor/Conversor de Texto con NUI - Práctica UT2 RA2 ["+currentFile.getName()+"]");
                        progressLabel.setState(ProgressLabel.State.DONE);
                    } catch(Exception ex){
                        ex.printStackTrace();
                        progressLabel.setState(ProgressLabel.State.ERROR);
                    }
                }
            };
            worker.execute();
        }
    }

    // ---------- Funciones de texto (SIN CAMBIOS) ----------
    private enum TextAction { UPPER, LOWER, REVERSE, REMOVE_DOUBLE_SPACES }

    private void transformSelectedOrAll(TextAction action) {
        try {
            Document doc = textPane.getDocument();
            int start = textPane.getSelectionStart();
            int end = textPane.getSelectionEnd();
            if(start==end){ start=0; end=doc.getLength(); }
            String original = doc.getText(start,end-start);
            String transformed = original;
            switch(action){
                case UPPER: transformed = original.toUpperCase(); break;
                case LOWER: transformed = original.toLowerCase(); break;
                case REVERSE: transformed = new StringBuilder(original).reverse().toString(); break;
                case REMOVE_DOUBLE_SPACES: transformed = original.replaceAll("\\s{2,}"," "); break;
            }
            doc.remove(start,end-start);
            doc.insertString(start,transformed,null);
        } catch (BadLocationException ex){ ex.printStackTrace(); }
    }

    private void toggleStyle(Object style){
        StyledDocument sd = textPane.getStyledDocument();
        int start = textPane.getSelectionStart();
        int end = textPane.getSelectionEnd();
        if(start == end) return;

        Element elem = sd.getCharacterElement(start);
        AttributeSet as = elem.getAttributes();
        MutableAttributeSet attr = new SimpleAttributeSet();

        if(style == StyleConstants.CharacterConstants.Bold) {
            StyleConstants.setBold(attr, !StyleConstants.isBold(as));
        } else if(style == StyleConstants.CharacterConstants.Italic) {
            StyleConstants.setItalic(attr, !StyleConstants.isItalic(as));
        } else if(style == StyleConstants.CharacterConstants.Underline) {
            StyleConstants.setUnderline(attr, !StyleConstants.isUnderline(as));
        }

        sd.setCharacterAttributes(start, end - start, attr, false);
    }

    private void changeColor(){
        Color chosen = JColorChooser.showDialog(this,"Selecciona color",Color.BLACK);
        if(chosen==null) return;
        StyledDocument sd = textPane.getStyledDocument();
        int start=textPane.getSelectionStart(), end=textPane.getSelectionEnd();
        if(start==end) return;
        MutableAttributeSet attr = new SimpleAttributeSet();
        StyleConstants.setForeground(attr,chosen);
        sd.setCharacterAttributes(start,end-start,attr,false);
    }

    private void doUndo(){ if(undoManager.canUndo()) undoManager.undo(); }
    private void doRedo(){ if(undoManager.canRedo()) undoManager.redo(); }
    private void updateUndoRedoButtons(JButton undoBtn,JButton redoBtn){
        undoBtn.setEnabled(undoManager.canUndo());
        redoBtn.setEnabled(undoManager.canRedo());
    }

    private void updateStatus(){
        String text = textPane.getText();
        lblChars.setText("Chars: "+text.length());
        String trimmed=text.trim();
        lblWords.setText("Words: "+(trimmed.isEmpty()?0:trimmed.split("\\s+").length));
        int lines=text.isEmpty()?1:text.split("\r\n|\r|\n").length;
        lblLines.setText("Lines: "+lines);
    }

    private void showContextMenu(MouseEvent e){
        JPopupMenu menu=new JPopupMenu();
        menu.add(new JMenuItem(new AbstractAction("Cortar"){ public void actionPerformed(ActionEvent a){ textPane.cut(); }}));
        menu.add(new JMenuItem(new AbstractAction("Copiar"){ public void actionPerformed(ActionEvent a){ textPane.copy(); }}));
        menu.add(new JMenuItem(new AbstractAction("Pegar"){ public void actionPerformed(ActionEvent a){ textPane.paste(); }}));
        menu.show(e.getComponent(), e.getX(), e.getY());
    }

    private JButton makeButton(String text, ActionListener action){ JButton b=new JButton(text); b.addActionListener(action); return b; }
    private Object openFindReplaceDialog() { return null; } // Placeholder

    // ---------- Componente ProgressLabel (SIN CAMBIOS) ----------
    class ProgressLabel extends JPanel{
        private JLabel label;
        private JProgressBar progressBar;
        enum State { IDLE, WORKING, DONE, ERROR }
        public ProgressLabel(){
            setLayout(new BorderLayout(5,0));
            label = new JLabel("Listo");
            progressBar = new JProgressBar(0,100);
            progressBar.setPreferredSize(new Dimension(150,16));
            progressBar.setStringPainted(true);
            progressBar.setVisible(false);
            add(label,BorderLayout.WEST);
            add(progressBar,BorderLayout.EAST);
        }
        public void setState(State s){
            switch(s){
                case IDLE: label.setText("Listo"); progressBar.setVisible(false); break;
                case WORKING: label.setText("⚙️ Procesando..."); progressBar.setVisible(true); progressBar.setValue(0); break;
                case DONE: label.setText("✅ Completado"); progressBar.setValue(100);
                    Timer tDone = new Timer(800, ev -> setState(State.IDLE));
                    tDone.setRepeats(false); tDone.start();
                    break;
                case ERROR: label.setText("❌ Error"); progressBar.setVisible(false);
                    Timer tError = new Timer(1500, ev -> setState(State.IDLE));
                    tError.setRepeats(false); tError.start();
                    break;
            }
        }
        public void setProgress(int val){ progressBar.setValue(val);}
        public void setOperationText(String txt){ label.setText("⚙️ "+txt);}
        public void reset(){ setState(State.IDLE); progressBar.setValue(0);}
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            practica3_3 editor = new practica3_3();
            System.out.println("=== Editor NUI Integrado ===");
            System.out.println("Comandos de voz disponibles:");
            System.out.println("- nuevo, abrir, guardar");
            System.out.println("- negrita, cursiva");
            System.out.println("- rojo, azul");
            System.out.println("- dictar [texto]");
            System.out.println("============================");
        });
    }
}