package com.bytezone.dm3270.display;

import static com.bytezone.dm3270.application.Console.Function.TERMINAL;
import static com.bytezone.dm3270.commands.AIDCommand.NO_AID_SPECIFIED;

import java.awt.Toolkit;
import java.io.UnsupportedEncodingException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.prefs.Preferences;

import com.bytezone.dm3270.application.Console.Function;
import com.bytezone.dm3270.application.ConsolePane;
import com.bytezone.dm3270.application.KeyboardStatusChangedEvent;
import com.bytezone.dm3270.application.KeyboardStatusListener;
import com.bytezone.dm3270.application.Site;
import com.bytezone.dm3270.assistant.AssistantStage;
import com.bytezone.dm3270.attributes.Attribute;
import com.bytezone.dm3270.attributes.ColorAttribute;
import com.bytezone.dm3270.commands.AIDCommand;
import com.bytezone.dm3270.commands.Command;
import com.bytezone.dm3270.commands.WriteControlCharacter;
import com.bytezone.dm3270.filetransfer.Transfer;
import com.bytezone.dm3270.filetransfer.Transfer.TransferType;
import com.bytezone.dm3270.filetransfer.TransferListener;
import com.bytezone.dm3270.filetransfer.TransferManager;
import com.bytezone.dm3270.filetransfer.TransferManager.TransferStatus;
import com.bytezone.dm3270.plugins.PluginsStage;
import com.bytezone.dm3270.structuredfields.SetReplyModeSF;

import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.stage.Stage;

public class Screen extends Canvas implements DisplayScreen, TransferListener
{
  private static final Toolkit defaultToolkit = Toolkit.getDefaultToolkit ();
  private static final boolean SHOW_CURSOR = true;
  private static final boolean HIDE_CURSOR = false;
  private static final byte[] saveScreenReplyTypes =
      { Attribute.XA_HIGHLIGHTING, Attribute.XA_FGCOLOR, Attribute.XA_CHARSET,
        Attribute.XA_BGCOLOR, Attribute.XA_TRANSPARENCY };

  private final Function function;

  private final ScreenPosition[] screenPositions;
  private final FieldManager fieldManager;
  private final FontManager fontManager;
  private final ContextManager contextManager;
  private final HistoryManager historyManager;
  private final TransferManager transferManager;
  private final ScreenPacker screenPacker;

  private final PluginsStage pluginsStage;
  private final AssistantStage assistantStage;
  private ConsolePane consolePane;

  private final GraphicsContext gc;
  private final ScreenDimensions screenDimensions;

  private final Pen pen;
  private final Cursor cursor;

  private byte currentAID;
  private byte replyMode;
  private byte[] replyTypes = new byte[0];

  private int insertedCursorPosition = -1;
  private boolean keyboardLocked;
  private boolean insertMode;
  private boolean readModifiedAll = false;

  public enum BuildInstruction
  {
    BUILD_FIELDS, DONT_BUILD_FIELDS
  }

  public Screen (ScreenDimensions screenDimensions, Preferences prefs, Function function,
      PluginsStage pluginsStage, Site site)
  {
    this.screenDimensions = screenDimensions;
    this.function = function;
    cursor = new Cursor (this, screenDimensions);

    gc = getGraphicsContext2D ();

    contextManager = new ContextManager ();
    fontManager = new FontManagerType1 (this, prefs);
    fieldManager = new FieldManager (this, contextManager, site);
    historyManager = new HistoryManager (screenDimensions, contextManager, fieldManager);
    assistantStage = new AssistantStage (this, site);
    transferManager = new TransferManager (this, site, assistantStage);

    screenPositions = new ScreenPosition[screenDimensions.size];
    pen = new PenType1 (screenPositions, gc, contextManager, screenDimensions);

    screenPacker = new ScreenPacker (pen, fieldManager);
    screenPacker.addTSOCommandListener (assistantStage);
    screenPacker.addTSOCommandListener (transferManager);
    addKeyboardStatusChangeListener (assistantStage);

    this.pluginsStage = pluginsStage;
    pluginsStage.setScreen (this);

    fieldManager.addScreenChangeListener (assistantStage);
    fieldManager.addScreenChangeListener (screenPacker);

    transferManager.addTransferListener (this);
    transferManager.addTransferListener (assistantStage);
  }

  public ScreenWatcher getScreenWatcher ()
  {
    return fieldManager.getScreenWatcher ();
  }

  public TransferManager getTransferManager ()
  {
    return transferManager;
  }

  public void setReplayServer (Site serverSite)
  {
    transferManager.setReplayServer (serverSite);
  }

  public void setStatusText (String text)
  {
    consolePane.setStatusText (text);
  }

  // called from the ConsolePane constructor
  public void setConsolePane (ConsolePane consolePane)
  {
    this.consolePane = consolePane;
    assistantStage.setConsolePane (consolePane);
    addKeyboardStatusChangeListener (consolePane);
  }

  public FieldManager getFieldManager ()
  {
    return fieldManager;
  }

  @Override
  public ScreenDimensions getScreenDimensions ()
  {
    return screenDimensions;
  }

  public boolean isTSOCommandScreen ()
  {
    return fieldManager.getScreenWatcher ().isTSOCommandScreen ();
  }

  public Field getTSOCommandField ()
  {
    return fieldManager.getScreenWatcher ().getTSOCommandField ();
  }

  public String getPrefix ()
  {
    return fieldManager.getScreenWatcher ().getPrefix ();
  }

  public FontManager getFontManager ()
  {
    return fontManager;
  }

  // called by WriteCommand.process()
  public PluginsStage getPluginsStage ()
  {
    return pluginsStage;
  }

  public AssistantStage getAssistantStage ()
  {
    return assistantStage;
  }

  public void close ()
  {
    assistantStage.closeWindow ();
  }

  public Function getFunction ()
  {
    return function;
  }

  public String getPreviousTSOCommand ()
  {
    return screenPacker.getPreviousTSOCommand ();
  }

  // called from AIDCommand.process()
  public void addTSOCommand (String command)
  {
    screenPacker.addTSOCommand (command);
  }

  public void listTSOCommands ()
  {
    screenPacker.listTSOCommands ();
  }

  // display a message on the screen - only used when logging off
  public void displayText (String text)
  {
    gc.setFill (ColorAttribute.colors[8]);                // black
    gc.fillRect (0, 0, getWidth (), getHeight ());
    gc.setFill (ColorAttribute.colors[5]);                // turquoise

    int x = 120;
    int y = 100;
    int height = 20;

    for (String line : text.split ("\n"))
    {
      gc.fillText (line, x, y);
      y += height;
    }
  }

  // called from AIDCommand.process()
  // called from ConsolePane constructor
  // called from PluginsStage.processPluginAuto()
  // called from PluginsStage.processPluginRequest()
  // called from PluginsStage.processReply()
  public Cursor getScreenCursor ()
  {
    return cursor;
  }

  // called from WriteControlCharacter.process()
  public void resetInsertMode ()
  {
    if (insertMode)
      toggleInsertMode ();
  }

  // called from ConsoleKeyPress.handle()
  // called from ConsolePane.sendAID()
  public void toggleInsertMode ()
  {
    insertMode = !insertMode;
    fireKeyboardStatusChange ("");
  }

  // called from Cursor.typeChar()
  // called from ConsolePane.sendAID()
  public boolean isInsertMode ()
  {
    return insertMode;
  }

  // called from EraseAllUnprotectedCommand.process()
  public void eraseAllUnprotected ()
  {
    Optional<Field> firstUnprotectedField = fieldManager.eraseAllUnprotected ();

    restoreKeyboard ();         // resets the AID to NO_AID_SPECIFIED
    resetModified ();
    draw ();

    if (firstUnprotectedField.isPresent ())
      cursor.moveTo (firstUnprotectedField.get ().getFirstLocation ());
  }

  public void buildFields (WriteControlCharacter wcc)
  {
    fieldManager.buildFields (screenPositions);        // what about resetModified?
  }

  // called from WriteCommand.process()
  public void checkRecording ()
  {
    byte savedReplyMode = replyMode;
    byte[] savedReplyTypes = replyTypes;

    setReplyMode (SetReplyModeSF.RM_CHARACTER, saveScreenReplyTypes);
    historyManager.saveScreen (readBuffer ());

    setReplyMode (savedReplyMode, savedReplyTypes);
  }

  // called from this.eraseAllUnprotected()
  // called from this.resize()
  // called from Write.process()
  public void draw ()
  {
    int pos = 0;

    for (ScreenPosition screenPosition : screenPositions)
      screenPosition.draw (HIDE_CURSOR);

    if (insertedCursorPosition >= 0)
    {
      cursor.moveTo (insertedCursorPosition);
      insertedCursorPosition = -1;
      cursor.setVisible (true);
    }

    pos = cursor.getLocation ();
    screenPositions[pos].draw (SHOW_CURSOR);
  }

  // called from Field.draw()
  // called from Cursor.moveTo() - when moving the cursor around the screen
  // called from Cursor.setVisible()
  // called from Cursor.backspace()
  // called from Cursor.delete()
  // called from Cursor.eraseEOL()
  // called from Cursor.moveTo()
  void drawPosition (int position, boolean hasCursor)
  {
    screenPositions[position].draw (hasCursor);
  }

  // called from FontManager() before we are fully initialised
  // called from FontManager.setFont()
  void fontChanged (FontDetails fontDetails)
  {
    contextManager.setFontDetails (fontDetails);
    setWidth (fontDetails.width * screenDimensions.columns
        + screenDimensions.xOffset * 2);
    setHeight (fontDetails.height * screenDimensions.rows + screenDimensions.yOffset * 2);

    gc.setFont (fontDetails.font);
    if (consolePane != null)
      consolePane.setStatusFont ();

    if (screenPositions != null)
    {
      ((Stage) getScene ().getWindow ()).sizeToScene ();
      eraseScreen ();
      draw ();
    }
  }

  void eraseScreen ()
  {
    gc.setFill (ColorAttribute.colors[8]);             // black
    gc.fillRect (0, 0, getWidth (), getHeight ());
  }

  // called from Cursor.home()
  Optional<Field> getHomeField ()
  {
    List<Field> fields = fieldManager.getUnprotectedFields ();
    if (fields != null && fields.size () > 0)
      return Optional.of (fields.get (0));
    return Optional.empty ();
  }

  public void setAID (byte aid)
  {
    currentAID = aid;
  }

  public byte getAID ()
  {
    return currentAID;
  }

  // called from SetReplyModeSF
  // called from this.checkRecording()
  public void setReplyMode (byte replyMode, byte[] replyTypes)
  {
    this.replyMode = replyMode;
    this.replyTypes = replyTypes;
  }

  public void setFieldText (Field field, String text)
  {
    try
    {
      field.setText (text.getBytes ("CP1047"));
      field.setModified (true);
      field.draw ();                      // draws the field without a cursor
    }
    catch (UnsupportedEncodingException e)
    {
      e.printStackTrace ();
    }
  }

  public String getScreenText ()
  {
    StringBuilder text = new StringBuilder ();

    text.append (pen.getScreenText ());
    text.append ("\n");
    text.append (fieldManager.getTotalsText ());

    return text.toString ();
  }

  // ---------------------------------------------------------------------------------//
  // File transfer interface methods
  // ---------------------------------------------------------------------------------//

  @Override
  public void transferStatusChanged (TransferStatus status, Transfer transfer)
  {
    if (transfer.isData ())
      switch (status)
      {
        case READY:
          break;

        case OPEN:
          if (transfer.getTransferType () == TransferType.UPLOAD)
            setText ("Uploading ...");
          else
            setText ("Downloading ...");
          break;

        case PROCESSING:
          if (transfer.getTransferType () == TransferType.DOWNLOAD)
            setText (String.format ("%,d : Bytes received: %,d", transfer.size (),
                                    transfer.getDataLength ()));
          else
            setText (String.format ("Bytes sent: %,d", transfer.getDataLength ()));
          break;

        case FINISHED:
          setText ("Closing ...");
          break;
      }
  }

  private void setText (String text)
  {
    Platform.runLater ( () -> setStatusText (text));
  }

  // ---------------------------------------------------------------------------------//
  // DisplayScreen interface methods
  // ---------------------------------------------------------------------------------//

  @Override
  public Pen getPen ()
  {
    return pen;
  }

  @Override
  public ScreenPosition getScreenPosition (int position)
  {
    return screenPositions[position];
  }

  @Override
  public ScreenPosition[] getScreenPositions ()
  {
    return screenPositions;
  }

  @Override
  public int validate (int position)
  {
    return pen.validate (position);
  }

  @Override
  public void clearScreen ()
  {
    eraseScreen ();
    cursor.moveTo (0);
    pen.clearScreen ();
    fieldManager.reset ();
  }

  @Override
  public void insertCursor (int position)
  {
    insertedCursorPosition = position;                // move it here later
  }

  // ---------------------------------------------------------------------------------//
  // Convert screen contents to an AID command
  // ---------------------------------------------------------------------------------//

  // called from ConsoleKeyPress.handle() in response to a user command
  // called from this.readModifiedFields(0x..) below
  public AIDCommand readModifiedFields ()
  {
    return screenPacker.readModifiedFields (currentAID, getScreenCursor ().getLocation (),
                                            readModifiedAll);
  }

  // Called from:
  //      ReadCommand.process() in response to a ReadBuffer (F2) command
  //      ReadPartitionSF.process() in response to a ReadBuffer (F2) command
  //      ScreenHistory.requestScreen()
  public AIDCommand readBuffer ()
  {
    return screenPacker.readBuffer (currentAID, getScreenCursor ().getLocation (),
                                    replyMode, replyTypes);
  }

  // Called from ReadCommand.process() in response to a ReadModified (F6)
  // or a ReadModifiedAll (6E) command
  // Called from ReadPartitionSF.process() in response to a ReadModified (F6)
  // or a ReadModifiedAll (6E) command
  public AIDCommand readModifiedFields (byte type)
  {
    switch (type)
    {
      case Command.READ_MODIFIED_F6:
        return readModifiedFields ();

      case Command.READ_MODIFIED_ALL_6E:
        readModifiedAll = true;
        AIDCommand command = readModifiedFields ();
        readModifiedAll = false;
        return command;

      default:
        System.out.println ("Unknown type in Screen.readModifiedFields()");
    }

    return null;
  }

  // ---------------------------------------------------------------------------------//
  // Events to be processed from WriteControlCharacter.process()
  // ---------------------------------------------------------------------------------//

  public void resetPartition ()
  {
  }

  public void startPrinter ()
  {
  }

  public void soundAlarm ()
  {
    defaultToolkit.beep ();
  }

  public void restoreKeyboard ()
  {
    setAID (NO_AID_SPECIFIED);
    cursor.setVisible (true);
    keyboardLocked = false;
    fireKeyboardStatusChange ("");
  }

  public void lockKeyboard (String keyName)
  {
    keyboardLocked = true;
    fireKeyboardStatusChange (keyName);

    if (function == TERMINAL)
      cursor.setVisible (false);
  }

  public void resetModified ()
  {
    fieldManager.getUnprotectedFields ().forEach (f -> f.setModified (false));
  }

  public boolean isKeyboardLocked ()
  {
    return keyboardLocked;
  }

  // ---------------------------------------------------------------------------------//
  // Listener events
  // ---------------------------------------------------------------------------------//

  private final Set<KeyboardStatusListener> keyboardChangeListeners = new HashSet<> ();

  private void fireKeyboardStatusChange (String keyName)
  {
    KeyboardStatusChangedEvent evt =
        new KeyboardStatusChangedEvent (insertMode, keyboardLocked, keyName);
    keyboardChangeListeners.forEach (l -> l.keyboardStatusChanged (evt));
  }

  public void addKeyboardStatusChangeListener (KeyboardStatusListener listener)
  {
    if (!keyboardChangeListeners.contains (listener))
      keyboardChangeListeners.add (listener);
  }

  public void removeKeyboardStatusChangeListener (KeyboardStatusListener listener)
  {
    if (keyboardChangeListeners.contains (listener))
      keyboardChangeListeners.remove (listener);
  }

  // ---------------------------------------------------------------------------------//
  // Screen history
  // ---------------------------------------------------------------------------------//

  public Optional<HistoryManager> pause ()             // triggered by cmd-s
  {
    if (historyManager.size () == 0)
      return Optional.empty ();

    historyManager.pause (keyboardLocked);
    keyboardLocked = true;

    return Optional.of (historyManager);
  }

  public void resume ()                     // also triggered by cmd-s
  {
    keyboardLocked = historyManager.resume ();
  }
}