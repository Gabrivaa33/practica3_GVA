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

public class practica3_1 extends JFrame {

    private JTextPane textPane;
    private JLabel lblChars, lblWords, lblLines;
    private UndoManager undoManager = new UndoManager();
    private ProgressLabel progressLabel;
    private JFileChooser fileChooser;
    private File currentFile;

    public practica3_1() {
        setTitle("Editor/Conversor de Texto - Práctica 3");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 600);
        setLocationRelativeTo(null);

        Container cp = getContentPane();
        cp.setLayout(new BorderLayout(5,5));
        ((JPanel) cp).setBorder(new EmptyBorder(6,6,6,6));

        // --- Toolbar ---
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);

        toolBar.add(makeButton("Mayúsculas", e -> transformSelectedOrAll(TextAction.UPPER)));
        toolBar.add(makeButton("Minúsculas", e -> transformSelectedOrAll(TextAction.LOWER)));
        toolBar.add(makeButton("Invertir", e -> transformSelectedOrAll(TextAction.REVERSE)));
        toolBar.add(makeButton("Quitar dobles espacios", e -> transformSelectedOrAll(TextAction.REMOVE_DOUBLE_SPACES)));

        toolBar.addSeparator();
        toolBar.add(makeButton("B", e -> toggleStyle(StyleConstants.CharacterConstants.Bold)));
        toolBar.add(makeButton("I", e -> toggleStyle(StyleConstants.CharacterConstants.Italic)));
        toolBar.add(makeButton("Color", e -> changeColor()));

        toolBar.addSeparator();
        toolBar.add(makeButton("Buscar/Reemplazar", e -> openFindReplaceDialog()));

        toolBar.addSeparator();
        JButton undoBtn = makeButton("Deshacer", e -> doUndo());
        JButton redoBtn = makeButton("Rehacer", e -> doRedo());
        toolBar.add(undoBtn);
        toolBar.add(redoBtn);

        cp.add(toolBar, BorderLayout.NORTH);

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

        // --- Barra de estado ---
        JPanel statusBar = new JPanel(new BorderLayout());
        JPanel countersPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12,2));
        lblChars = new JLabel("Chars: 0");
        lblWords = new JLabel("Words: 0");
        lblLines = new JLabel("Lines: 1");
        countersPanel.add(lblChars);
        countersPanel.add(new JSeparator(SwingConstants.VERTICAL));
        countersPanel.add(lblWords);
        countersPanel.add(new JSeparator(SwingConstants.VERTICAL));
        countersPanel.add(lblLines);

        progressLabel = new ProgressLabel();
        statusBar.add(countersPanel, BorderLayout.WEST);
        statusBar.add(progressLabel, BorderLayout.EAST);
        cp.add(statusBar, BorderLayout.SOUTH);

        // --- Menú superior ---
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

        menuBar.add(menuEditar);

        setJMenuBar(menuBar);
        setVisible(true);
    }

    private Object openFindReplaceDialog() {
		// TODO Auto-generated method stub
		return null;
	}

	// ---------- Métodos de archivo con progreso ----------
    private void newFile() {
        textPane.setText("");
        currentFile = null;
        setTitle("Editor/Conversor de Texto - Práctica [Nuevo archivo]");
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

                // Guardar de golpe en el archivo
                try (FileOutputStream out = new FileOutputStream(file)) {
                    rtfKit.write(out, doc, 0, doc.getLength());
                }

                // Simular progreso para la barra (puedes ajustar el sleep)
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
                setTitle("Editor/Conversor de Texto - Práctica [" + file.getName() + "]");
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

                    // Leer el archivo completo
                    try (FileInputStream in = new FileInputStream(currentFile)) {
                        rtfKit.read(in, doc, 0);
                    }

                    // Simular progreso
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
                        setTitle("Editor/Conversor de Texto - Práctica ["+currentFile.getName()+"]");
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

    // ---------- Funciones de texto ----------
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
        int start=textPane.getSelectionStart(), end=textPane.getSelectionEnd();
        if(start==end) return;
        Element elem = sd.getCharacterElement(start);
        AttributeSet as = elem.getAttributes();
        boolean current = (style == StyleConstants.CharacterConstants.Bold)
                ? StyleConstants.isBold(as)
                : StyleConstants.isItalic(as);
        MutableAttributeSet attr = new SimpleAttributeSet();
        if(style==StyleConstants.CharacterConstants.Bold) StyleConstants.setBold(attr,!current);
        if(style==StyleConstants.CharacterConstants.Italic) StyleConstants.setItalic(attr,!current);
        sd.setCharacterAttributes(start,end-start,attr,false);
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

    // ---------- Componente ProgressLabel ----------
 // Dentro de ProgressLabel
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


    public static void main(String[] args){ SwingUtilities.invokeLater(practica3_1::new);}
}
