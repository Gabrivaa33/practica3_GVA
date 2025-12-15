package practica3_GVA;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.event.*;
import javax.swing.text.*;
import javax.swing.text.rtf.RTFEditorKit;
import javax.swing.undo.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

// Imports para Vosk (reconocimiento de voz)
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.LibVosk;
import org.vosk.LogLevel;

// Imports para captura de audio del micrófono
import javax.sound.sampled.*;

//============================================
//ENUM Y INTERFACES NUI
//============================================

enum NuevoNuiCommand {
 NUEVO_DOCUMENTO,
 ABRIR_DOCUMENTO,
 GUARDAR_DOCUMENTO,
 APLICAR_NEGRITA,
 APLICAR_CURSIVA,
 COLOR_ROJO,
 COLOR_AZUL,
 DICTAR_TEXTO
}

interface NuevoNuiListener {
 void onCommand(NuevoNuiCommand cmd, String payload);
}

class NuevoNuiController {
 private java.util.List<NuevoNuiListener> listeners = new java.util.ArrayList<>();
 
 public void addListener(NuevoNuiListener listener) {
     if (!listeners.contains(listener)) {
         listeners.add(listener);
     }
 }
 
 public void removeListener(NuevoNuiListener listener) {
     listeners.remove(listener);
 }
 
 public void fireCommand(NuevoNuiCommand cmd, String payload) {
     System.out.println("[NUI-VOZ] Comando: " + cmd + 
                       (payload != null ? " (" + payload + ")" : ""));
     
     for (NuevoNuiListener listener : listeners) {
         listener.onCommand(cmd, payload);
     }
 }
 
 public void fireCommand(NuevoNuiCommand cmd) {
     fireCommand(cmd, null);
 }
}

// ============================================
// CONTROL DE VOZ REAL CON MICRÓFONO
// ============================================

class VoiceControlPanel extends JPanel {
    private JButton btnStartStop;
    private JLabel statusLabel;
    private JTextArea logArea;
    private NuevoNuiController nuiController;
    private boolean isListening = false;
    private Thread listeningThread;
    
    // Componentes de Vosk para reconocimiento de voz real
    private Model voskModel;
    private Recognizer recognizer;
    private TargetDataLine microphone;
    
    // Ruta al modelo de Vosk en español
    private static final String MODEL_PATH = "vosk-model-small-es-0.42";
    private static final float SAMPLE_RATE = 16000;
    
    // Mapeo de palabras a comandos
    private Map<String, NuevoNuiCommand> commandMap = new HashMap<>();
    
    public VoiceControlPanel(NuevoNuiController controller) {
        this.nuiController = controller;
        initCommandMap();
        initUI();
    }
    
    private void initCommandMap() {
        // Español
        commandMap.put("nuevo", NuevoNuiCommand.NUEVO_DOCUMENTO);
        commandMap.put("nueva", NuevoNuiCommand.NUEVO_DOCUMENTO);
        commandMap.put("abrir", NuevoNuiCommand.ABRIR_DOCUMENTO);
        commandMap.put("guardar", NuevoNuiCommand.GUARDAR_DOCUMENTO);
        commandMap.put("negrita", NuevoNuiCommand.APLICAR_NEGRITA);
        commandMap.put("cursiva", NuevoNuiCommand.APLICAR_CURSIVA);
        commandMap.put("itálica", NuevoNuiCommand.APLICAR_CURSIVA);
        commandMap.put("rojo", NuevoNuiCommand.COLOR_ROJO);
        commandMap.put("azul", NuevoNuiCommand.COLOR_AZUL);
        commandMap.put("escribir", NuevoNuiCommand.DICTAR_TEXTO);
        commandMap.put("dictar", NuevoNuiCommand.DICTAR_TEXTO);
        
        // Inglés
        commandMap.put("new", NuevoNuiCommand.NUEVO_DOCUMENTO);
        commandMap.put("open", NuevoNuiCommand.ABRIR_DOCUMENTO);
        commandMap.put("save", NuevoNuiCommand.GUARDAR_DOCUMENTO);
        commandMap.put("bold", NuevoNuiCommand.APLICAR_NEGRITA);
        commandMap.put("italic", NuevoNuiCommand.APLICAR_CURSIVA);
        commandMap.put("red", NuevoNuiCommand.COLOR_ROJO);
        commandMap.put("blue", NuevoNuiCommand.COLOR_AZUL);
        commandMap.put("write", NuevoNuiCommand.DICTAR_TEXTO);
    }
    
    private void initUI() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createTitledBorder("🎤 Control de Voz Real (Vosk)"));
        
        // Panel de control
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        
        btnStartStop = new JButton("▶ Iniciar Escucha por Voz");
        btnStartStop.setBackground(new Color(50, 150, 50));
        btnStartStop.setForeground(Color.WHITE);
        btnStartStop.setFont(new Font("Arial", Font.BOLD, 12));
        btnStartStop.addActionListener(e -> toggleListening());
        
        statusLabel = new JLabel(" Estado: INACTIVO (haz clic para iniciar)");
        statusLabel.setForeground(Color.RED);
        statusLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        
        controlPanel.add(btnStartStop);
        controlPanel.add(Box.createHorizontalStrut(20));
        controlPanel.add(statusLabel);
        
        // Área de log
        logArea = new JTextArea(4, 50);
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        logArea.setBackground(new Color(240, 240, 240));
        logArea.setForeground(Color.DARK_GRAY);
        JScrollPane scrollPane = new JScrollPane(logArea);
        
        // Panel de ayuda
        JPanel helpPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel lblHelp = new JLabel("<html><b>Comandos de voz:</b> 'nuevo', 'abrir', 'guardar', 'negrita', 'cursiva', 'rojo', 'azul', 'escribir [texto]'</html>");
        helpPanel.add(lblHelp);
        
        // Botón de prueba
        JButton btnTest = new JButton("Probar Comando");
        btnTest.addActionListener(e -> probarComandoManual());
        helpPanel.add(Box.createHorizontalStrut(20));
        helpPanel.add(btnTest);
        
        add(controlPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(helpPanel, BorderLayout.SOUTH);
    }
    
    void toggleListening() {
        if (!isListening) {
            startVoiceListening();
        } else {
            stopVoiceListening();
        }
    }
    
    private void startVoiceListening() {
        // Deshabilitar botón mientras se inicializa
        btnStartStop.setEnabled(false);
        statusLabel.setText(" Estado: INICIALIZANDO VOSK...");
        statusLabel.setForeground(Color.ORANGE);
        
        // Inicializar en un hilo separado para no bloquear la UI
        new Thread(() -> {
            try {
                // Configurar Vosk para reducir logs
                LibVosk.setLogLevel(LogLevel.WARNINGS);
                
                logMessage("=== INICIANDO RECONOCIMIENTO DE VOZ VOSK ===");
                logMessage("Cargando modelo de español...");
                
                // Cargar el modelo de Vosk
                File modelDir = new File(MODEL_PATH);
                if (!modelDir.exists()) {
                    // Intentar ruta absoluta
                    modelDir = new File(System.getProperty("user.dir"), MODEL_PATH);
                }
                
                if (!modelDir.exists()) {
                    throw new IOException("No se encontró el modelo de Vosk en: " + modelDir.getAbsolutePath());
                }
                
                voskModel = new Model(modelDir.getAbsolutePath());
                logMessage("✅ Modelo cargado correctamente");
                
                // Crear el reconocedor
                recognizer = new Recognizer(voskModel, SAMPLE_RATE);
                logMessage("✅ Reconocedor inicializado (16kHz)");
                
                // Configurar el formato de audio para el micrófono
                AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
                DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
                
                if (!AudioSystem.isLineSupported(info)) {
                    throw new LineUnavailableException("El formato de audio no es soportado por el sistema");
                }
                
                microphone = (TargetDataLine) AudioSystem.getLine(info);
                microphone.open(format);
                microphone.start();
                logMessage("✅ Micrófono abierto y capturando");
                
                // Actualizar UI
                SwingUtilities.invokeLater(() -> {
                    isListening = true;
                    btnStartStop.setEnabled(true);
                    btnStartStop.setText("⏸ Detener Escucha");
                    btnStartStop.setBackground(new Color(200, 50, 50));
                    statusLabel.setText(" Estado: ESCUCHANDO (habla ahora)");
                    statusLabel.setForeground(new Color(0, 150, 0));
                });
                
                logMessage("🎤 Di comandos como 'nuevo', 'guardar', 'negrita'");
                logMessage("📝 Para dictar texto: 'escribir hola mundo'");
                
                // Iniciar hilo de reconocimiento
                listeningThread = new Thread(this::runVoiceRecognition);
                listeningThread.setDaemon(true);
                listeningThread.start();
                
            } catch (Exception e) {
                logMessage("❌ Error al inicializar Vosk: " + e.getMessage());
                e.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    btnStartStop.setEnabled(true);
                    statusLabel.setText(" Estado: ERROR - " + e.getMessage());
                    statusLabel.setForeground(Color.RED);
                });
                cleanupVosk();
            }
        }).start();
    }
    
    private void stopVoiceListening() {
        isListening = false;
        btnStartStop.setText("▶ Iniciar Escucha por Voz");
        btnStartStop.setBackground(new Color(50, 150, 50));
        statusLabel.setText(" Estado: INACTIVO");
        statusLabel.setForeground(Color.RED);
        
        logMessage("=== RECONOCIMIENTO DETENIDO ===");
        
        // Detener el hilo de escucha
        if (listeningThread != null && listeningThread.isAlive()) {
            listeningThread.interrupt();
        }
        
        // Limpiar recursos de Vosk
        cleanupVosk();
    }
    
    private void cleanupVosk() {
        try {
            if (microphone != null) {
                microphone.stop();
                microphone.close();
                microphone = null;
            }
            if (recognizer != null) {
                recognizer.close();
                recognizer = null;
            }
            if (voskModel != null) {
                voskModel.close();
                voskModel = null;
            }
        } catch (Exception e) {
            System.err.println("Error al limpiar recursos de Vosk: " + e.getMessage());
        }
    }
    
    private void runVoiceRecognition() {
        byte[] buffer = new byte[4096];
        
        try {
            while (isListening && !Thread.currentThread().isInterrupted()) {
                int bytesRead = microphone.read(buffer, 0, buffer.length);
                
                if (bytesRead > 0) {
                    if (recognizer.acceptWaveForm(buffer, bytesRead)) {
                        // Resultado final de una frase
                        String result = recognizer.getResult();
                        String text = extractTextFromJson(result);
                        
                        if (text != null && !text.isEmpty()) {
                            logMessage("🔊 Reconocido: \"" + text + "\"");
                            processVoiceCommand(text);
                        }
                    } else {
                        // Resultado parcial (opcional: mostrar mientras habla)
                        String partial = recognizer.getPartialResult();
                        String partialText = extractPartialFromJson(partial);
                        if (partialText != null && !partialText.isEmpty()) {
                            SwingUtilities.invokeLater(() -> {
                                statusLabel.setText(" Escuchando: " + partialText);
                            });
                        }
                    }
                }
            }
            
            // Procesar cualquier resultado final pendiente
            if (recognizer != null) {
                String finalResult = recognizer.getFinalResult();
                String text = extractTextFromJson(finalResult);
                if (text != null && !text.isEmpty()) {
                    logMessage("🔊 Reconocido (final): \"" + text + "\"");
                    processVoiceCommand(text);
                }
            }
            
        } catch (Exception e) {
            if (isListening) {
                logMessage("❌ Error en reconocimiento: " + e.getMessage());
            }
        }
    }
    
    // Extrae el texto del JSON que devuelve Vosk: {"text": "..."}
    private String extractTextFromJson(String json) {
        if (json == null) return null;
        int start = json.indexOf("\"text\" : \"");
        if (start == -1) {
            start = json.indexOf("\"text\": \"");
        }
        if (start == -1) return null;
        
        start = json.indexOf("\"", start + 8) + 1;
        int end = json.lastIndexOf("\"");
        
        if (start > 0 && end > start) {
            return json.substring(start, end).trim();
        }
        return null;
    }
    
    // Extrae el texto parcial del JSON: {"partial": "..."}
    private String extractPartialFromJson(String json) {
        if (json == null) return null;
        int start = json.indexOf("\"partial\" : \"");
        if (start == -1) {
            start = json.indexOf("\"partial\": \"");
        }
        if (start == -1) return null;
        
        start = json.indexOf("\"", start + 11) + 1;
        int end = json.lastIndexOf("\"");
        
        if (start > 0 && end > start) {
            return json.substring(start, end).trim();
        }
        return null;
    }
    
    private void processVoiceCommand(String voiceText) {
        String text = voiceText.toLowerCase().trim();
        
        // Detectar dictado
        if (text.startsWith("escribir ") || text.startsWith("dictar ") || text.startsWith("write ")) {
            String dictationText = voiceText.substring(voiceText.indexOf(" ") + 1);
            nuiController.fireCommand(NuevoNuiCommand.DICTAR_TEXTO, dictationText);
            logMessage("📝 Dictando texto: \"" + dictationText + "\"");
            return;
        }
        
        // Buscar comando exacto
        NuevoNuiCommand command = commandMap.get(text);
        
        if (command != null) {
            nuiController.fireCommand(command);
            logMessage("✅ Comando ejecutado: " + command);
        } else {
            // Buscar coincidencias parciales
            for (Map.Entry<String, NuevoNuiCommand> entry : commandMap.entrySet()) {
                if (text.contains(entry.getKey())) {
                    nuiController.fireCommand(entry.getValue());
                    logMessage("✅ Comando ejecutado: " + entry.getValue() + " (por: \"" + entry.getKey() + "\")");
                    return;
                }
            }
            
            logMessage("❓ Comando no reconocido: \"" + text + "\"");
        }
    }
    
    private void probarComandoManual() {
        String[] comandos = {
            "nuevo", "abrir", "guardar", 
            "negrita", "cursiva", 
            "rojo", "azul",
            "escribir texto de prueba"
        };
        
        String seleccion = (String) JOptionPane.showInputDialog(
            this,
            "Selecciona un comando para probar:",
            "Probar Comando de Voz",
            JOptionPane.QUESTION_MESSAGE,
            null,
            comandos,
            comandos[0]
        );
        
        if (seleccion != null) {
            processVoiceCommand(seleccion);
        }
    }
    
    private void logMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
            logArea.append("[" + timestamp + "] " + message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }
}

// ============================================
// EDITOR PRINCIPAL CON VOZ REAL
// ============================================

public class practica3_3_1 extends JFrame implements NuevoNuiListener {

    private JTextPane textPane;
    private JLabel lblChars, lblWords, lblLines;
    private UndoManager undoManager = new UndoManager();
    private ProgressLabel progressLabel;
    private JFileChooser fileChooser;
    private File currentFile;
    
    // Componentes NUI de voz
    private NuevoNuiController nuiController;
    private VoiceControlPanel voiceControlPanel;
    private int contadorComandosVoz = 0;
    private JLabel lblVozStatus;

    public practica3_3_1() {
        setTitle("Editor con Reconocimiento de Voz Real - Práctica UT2 RA2");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(950, 700);
        setLocationRelativeTo(null);

        Container cp = getContentPane();
        cp.setLayout(new BorderLayout(5,5));
        ((JPanel) cp).setBorder(new EmptyBorder(6,6,6,6));

        // Inicializar capa NUI
        nuiController = new NuevoNuiController();
        nuiController.addListener(this);
        voiceControlPanel = new VoiceControlPanel(nuiController);
        
        // Panel superior con control de voz
        JPanel panelSuperior = new JPanel(new BorderLayout());
        panelSuperior.add(voiceControlPanel, BorderLayout.NORTH);
        
        // Toolbar original
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

        // Área de texto
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

        // Barra de estado extendida para voz
        JPanel statusBar = new JPanel(new BorderLayout());
        JPanel countersPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12,2));
        lblChars = new JLabel("Chars: 0");
        lblWords = new JLabel("Words: 0");
        lblLines = new JLabel("Lines: 1");
        
        lblVozStatus = new JLabel(" | Comandos Voz: 0");
        lblVozStatus.setForeground(new Color(0, 100, 200));
        lblVozStatus.setFont(lblVozStatus.getFont().deriveFont(Font.BOLD));
        
        countersPanel.add(lblChars);
        countersPanel.add(new JSeparator(SwingConstants.VERTICAL));
        countersPanel.add(lblWords);
        countersPanel.add(new JSeparator(SwingConstants.VERTICAL));
        countersPanel.add(lblLines);
        countersPanel.add(new JSeparator(SwingConstants.VERTICAL));
        countersPanel.add(lblVozStatus);

        progressLabel = new ProgressLabel();
        statusBar.add(countersPanel, BorderLayout.WEST);
        statusBar.add(progressLabel, BorderLayout.EAST);
        cp.add(statusBar, BorderLayout.SOUTH);

        // Menú superior con opciones de voz
        JMenuBar menuBar = new JMenuBar();
        
        // Menú Archivo
        JMenu menuArchivo = new JMenu("Archivo");
        menuArchivo.add(makeMenuItem("Nuevo", e -> newFile()));
        menuArchivo.add(makeMenuItem("Abrir...", e -> openFile()));
        menuArchivo.add(makeMenuItem("Guardar", e -> saveFile()));
        menuArchivo.add(makeMenuItem("Guardar como...", e -> saveFileAs()));
        menuArchivo.addSeparator();
        menuArchivo.add(makeMenuItem("Salir", e -> dispose()));
        menuBar.add(menuArchivo);

        // Menú Editar
        JMenu menuEditar = new JMenu("Editar");
        menuEditar.add(makeMenuItem("Deshacer", e -> doUndo()));
        menuEditar.add(makeMenuItem("Rehacer", e -> doRedo()));
        menuEditar.addSeparator();
        menuEditar.add(makeMenuItem("Cortar", e -> textPane.cut()));
        menuEditar.add(makeMenuItem("Copiar", e -> textPane.copy()));
        menuEditar.add(makeMenuItem("Pegar", e -> textPane.paste()));
        menuEditar.add(makeMenuItem("Buscar/Reemplazar", e -> openFindReplaceDialog()));
        menuBar.add(menuEditar);

        // Menú Voz
        JMenu menuVoz = new JMenu("🎤 Voz");
        menuVoz.add(makeMenuItem("▶ Iniciar Escucha", e -> {
            // Simulamos iniciar escucha
            voiceControlPanel.toggleListening();
        }));
        menuVoz.add(makeMenuItem("⏸ Detener Escucha", e -> {
            // Simulamos detener escucha
            voiceControlPanel.toggleListening();
        }));
        menuVoz.addSeparator();
        menuVoz.add(makeMenuItem("Mostrar Comandos", e -> mostrarComandosVoz()));
        menuVoz.add(makeMenuItem("Probar Micrófono", e -> probarMicrofono()));
        menuVoz.add(makeMenuItem("Reset Contador", e -> {
            contadorComandosVoz = 0;
            lblVozStatus.setText(" | Comandos Voz: 0");
        }));
        menuBar.add(menuVoz);

        setJMenuBar(menuBar);
        setVisible(true);
    }

    // ========== IMPLEMENTACIÓN DE NuevoNuiListener ==========
    
    @Override
    public void onCommand(NuevoNuiCommand cmd, String payload) {
        SwingUtilities.invokeLater(() -> {
            contadorComandosVoz++;
            lblVozStatus.setText(" | Comandos Voz: " + contadorComandosVoz);
            
            switch (cmd) {
                case NUEVO_DOCUMENTO:
                    newFile();
                    mostrarMensajeVoz("✅ Nuevo documento creado por voz");
                    break;
                case ABRIR_DOCUMENTO:
                    openFile();
                    mostrarMensajeVoz("✅ Abriendo documento por voz");
                    break;
                case GUARDAR_DOCUMENTO:
                    saveFile();
                    mostrarMensajeVoz("✅ Guardando documento por voz");
                    break;
                case APLICAR_NEGRITA:
                    aplicarFormatoVoz(StyleConstants.CharacterConstants.Bold);
                    mostrarMensajeVoz("✅ Negrita aplicada por voz");
                    break;
                case APLICAR_CURSIVA:
                    aplicarFormatoVoz(StyleConstants.CharacterConstants.Italic);
                    mostrarMensajeVoz("✅ Cursiva aplicada por voz");
                    break;
                case COLOR_ROJO:
                    aplicarColorVoz(Color.RED);
                    mostrarMensajeVoz("✅ Color rojo aplicado por voz");
                    break;
                case COLOR_AZUL:
                    aplicarColorVoz(Color.BLUE);
                    mostrarMensajeVoz("✅ Color azul aplicado por voz");
                    break;
                case DICTAR_TEXTO:
                    dictarTextoVoz(payload != null ? payload : "");
                    break;
            }
        });
    }
    
    private void aplicarFormatoVoz(Object style) {
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
            
            if (style == StyleConstants.CharacterConstants.Bold) {
                StyleConstants.setBold(attr, !StyleConstants.isBold(as));
            } else if (style == StyleConstants.CharacterConstants.Italic) {
                StyleConstants.setItalic(attr, !StyleConstants.isItalic(as));
            }
            
            sd.setCharacterAttributes(inicio, fin - inicio, attr, false);
        }
    }
    
    private void aplicarColorVoz(Color color) {
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
    
    private void dictarTextoVoz(String texto) {
        if (texto.isEmpty()) return;
        
        try {
            int pos = textPane.getCaretPosition();
            textPane.getDocument().insertString(pos, texto + " ", null);
            mostrarMensajeVoz("📝 Dictado: \"" + (texto.length() > 20 ? texto.substring(0, 20) + "..." : texto) + "\"");
        } catch (BadLocationException ex) {
            ex.printStackTrace();
        }
    }
    
    private void mostrarMensajeVoz(String mensaje) {
        progressLabel.setOperationText(mensaje);
        progressLabel.setState(ProgressLabel.State.DONE);
        System.out.println("[VOZ] " + mensaje);
    }
    
    private void mostrarComandosVoz() {
        String mensaje = "🎤 COMANDOS DE VOZ DISPONIBLES\n\n" +
                        "ESPAÑOL:\n" +
                        "• 'nuevo' / 'nueva' - Crear nuevo documento\n" +
                        "• 'abrir' - Abrir documento\n" +
                        "• 'guardar' - Guardar documento\n" +
                        "• 'negrita' - Aplicar negrita\n" +
                        "• 'cursiva' / 'itálica' - Aplicar cursiva\n" +
                        "• 'rojo' - Color rojo\n" +
                        "• 'azul' - Color azul\n" +
                        "• 'escribir [texto]' / 'dictar [texto]' - Dictar texto\n\n" +
                        "INGLÉS:\n" +
                        "• 'new', 'open', 'save', 'bold', 'italic', 'red', 'blue', 'write [text]'\n\n" +
                        "Comandos ejecutados: " + contadorComandosVoz;
        
        JOptionPane.showMessageDialog(this, mensaje, "Comandos de Voz", JOptionPane.INFORMATION_MESSAGE);
    }
    
    private void probarMicrofono() {
        try {
            // Verificar si hay micrófono disponible
            javax.sound.sampled.Mixer.Info[] mixerInfos = javax.sound.sampled.AudioSystem.getMixerInfo();
            boolean tieneMicrofono = false;
            
            for (javax.sound.sampled.Mixer.Info info : mixerInfos) {
                if (info.getName().toLowerCase().contains("mic") || 
                    info.getDescription().toLowerCase().contains("mic")) {
                    tieneMicrofono = true;
                    break;
                }
            }
            
            if (tieneMicrofono) {
                JOptionPane.showMessageDialog(this, 
                    "✅ Micrófono detectado en el sistema\n\n" +
                    "Para usar reconocimiento de voz real:\n" +
                    "1. Haz clic en '▶ Iniciar Escucha por Voz'\n" +
                    "2. Habla cerca del micrófono\n" +
                    "3. Di comandos claramente\n" +
                    "4. Mira la consola para interactuar", 
                    "Prueba de Micrófono", 
                    JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, 
                    "⚠️ No se detectó micrófono\n\n" +
                    "Usando modo consola:\n" +
                    "1. Haz clic en '▶ Iniciar Escucha por Voz'\n" +
                    "2. Escribe comandos en la consola/terminal\n" +
                    "3. Ejemplo: 'nuevo', 'guardar', 'negrita'\n" +
                    "4. Para salir: escribe 'salir'", 
                    "Micrófono no detectado", 
                    JOptionPane.WARNING_MESSAGE);
            }
                
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, 
                "❌ Error al verificar micrófono:\n" + e.getMessage(), 
                "Error", 
                JOptionPane.ERROR_MESSAGE);
        }
    }

    // ========== MÉTODOS ORIGINALES DEL EDITOR ==========
    
    private JMenuItem makeMenuItem(String text, ActionListener action) {
        JMenuItem item = new JMenuItem(text);
        item.addActionListener(action);
        return item;
    }
    
    private void newFile() {
        textPane.setText("");
        currentFile = null;
        setTitle("Editor con Voz - [Nuevo archivo]");
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
                setTitle("Editor con Voz - [" + file.getName() + "]");
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
                        setTitle("Editor con Voz - ["+currentFile.getName()+"]");
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

    private JButton makeButton(String text, ActionListener action){ 
        JButton b=new JButton(text); 
        b.addActionListener(action); 
        return b; 
    }
    
    private Object openFindReplaceDialog() { 
        JDialog dialog = new JDialog(this, "Buscar/Reemplazar", true);
        dialog.setSize(400, 200);
        dialog.setLocationRelativeTo(this);
        
        JPanel panel = new JPanel(new GridLayout(3, 2, 5, 5));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));
        
        panel.add(new JLabel("Buscar:"));
        JTextField txtBuscar = new JTextField();
        panel.add(txtBuscar);
        
        panel.add(new JLabel("Reemplazar:"));
        JTextField txtReemplazar = new JTextField();
        panel.add(txtReemplazar);
        
        JButton btnBuscar = new JButton("Buscar");
        JButton btnReemplazar = new JButton("Reemplazar");
        
        panel.add(btnBuscar);
        panel.add(btnReemplazar);
        
        dialog.add(panel);
        dialog.setVisible(true);
        return null;
    }

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
            practica3_3_1 editor = new practica3_3_1();
            System.out.println("\n" + "=".repeat(50));
            System.out.println("EDITOR CON RECONOCIMIENTO DE VOZ VOSK");
            System.out.println("=".repeat(50));
            System.out.println("\nINSTRUCCIONES:");
            System.out.println("1. Haz clic en '▶ Iniciar Escucha por Voz' (botón verde)");
            System.out.println("2. Espera a que cargue el modelo de Vosk");
            System.out.println("3. Habla al micrófono con comandos como:");
            System.out.println("   - 'nuevo', 'guardar', 'negrita', 'cursiva'");
            System.out.println("   - 'rojo', 'azul'");
            System.out.println("   - 'escribir hola mundo' (para dictar texto)");
            System.out.println("4. Mira el panel de control para ver los logs");
            System.out.println("\n¡El editor con Vosk está listo!");
            System.out.println("=".repeat(50) + "\n");
        });
    }
}