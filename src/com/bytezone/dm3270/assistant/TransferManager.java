package com.bytezone.dm3270.assistant;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.bytezone.dm3270.application.Site;
import com.bytezone.dm3270.display.Screen;
import com.bytezone.dm3270.display.TSOCommandListener;
import com.bytezone.dm3270.filetransfer.FileTransferOutboundSF;
import com.bytezone.dm3270.filetransfer.IndFileCommand;
import com.bytezone.dm3270.filetransfer.Transfer;
import com.bytezone.reporter.application.FileNode;
import com.bytezone.reporter.application.ReporterNode;

import javafx.application.Platform;

public class TransferManager implements TSOCommandListener
{
  private final List<Transfer> transfers = new ArrayList<> ();
  private Transfer currentTransfer;

  private final Screen screen;
  private final Site site;
  //  private final ReporterNode reporterNode;
  private final AssistantStage assistantStage;
  private IndFileCommand indFileCommand;

  public TransferManager (Screen screen, Site site, AssistantStage assistantStage)
  {
    this.screen = screen;
    this.site = site;
    this.assistantStage = assistantStage;
    assistantStage.setTransferManager (this);
  }

  private void addTransfer (Transfer transfer)
  {
    if (transfer.isSendData ())
    {
      transfers.add (transfer);
      Platform.runLater ( () -> addBuffer (transfer));
    }
  }

  private void addBuffer (Transfer transfer)
  {
    String name = transfer.getFileName ().toUpperCase ();
    if (!transfer.hasTLQ ())
    {
      String tlq = screen.getPrefix ();
      if (!tlq.isEmpty ())
        name = tlq + "." + name;
    }

    String siteFolderName = "";
    if (site != null)
    {
      siteFolderName = site.folder.getText ();
      if (!siteFolderName.isEmpty ())
      {
        Path path = Paths.get (System.getProperty ("user.home"), "dm3270", "files",
                               siteFolderName);
        if (!Files.exists (path))
          siteFolderName = "";
      }
      else
        System.out.println ("No folder specified in site record");
    }

    byte[] buffer = transfer.combineDataBuffers ();

    // this should be sent to a listener
    ReporterNode reporterNode = assistantStage.getReporterNode ();
    if (siteFolderName.isEmpty ())
      reporterNode.addBuffer (name, buffer);
    else
      reporterNode.addBuffer (name, buffer, siteFolderName);
  }

  // called from FileTransferOutboundSF.processOpen()
  public Optional<byte[]> getCurrentFileBuffer ()
  {
    // whoever instigated the transfer should have told us about it already
    ReporterNode reporterNode = assistantStage.getReporterNode ();
    FileNode fileNode = reporterNode.getSelectedNode ();
    if (fileNode == null)
    {
      System.out.println ("No fileNode selected in FilesTab.getCurrentFileBuffer()");
      return Optional.empty ();
    }
    else
      return Optional.of (fileNode.getReportData ().getBuffer ());
  }

  // called from FileTransferOutboundSF.processOpen()
  public void openTransfer (Transfer transfer)
  {
    currentTransfer = transfer;     // save it for subsequent calls
    transfer.setTransferCommand (indFileCommand);
  }

  // called from FileTransferOutboundSF.processSend0x46()
  // called from FileTransferOutboundSF.processReceive()
  public Optional<Transfer> getTransfer (FileTransferOutboundSF transferRecord)
  {
    if (currentTransfer == null)
    {
      System.out.println ("Null current transfer");
      return Optional.empty ();
    }

    currentTransfer.add (transferRecord);
    return Optional.of (currentTransfer);
  }

  // called from FileTransferOutboundSF.processClose()
  public Optional<Transfer> closeTransfer (FileTransferOutboundSF transferRecord)
  {
    if (currentTransfer == null)
    {
      System.out.println ("Null current transfer");
      return Optional.empty ();
    }

    Transfer transfer = currentTransfer;
    currentTransfer.add (transferRecord);

    addTransfer (currentTransfer);                // add to the file tree
    currentTransfer = null;

    return Optional.of (transfer);
  }

  // called from FileTransferOutboundSF.processReceive()
  public void closeTransfer ()
  {
    currentTransfer = null;
  }

  public IndFileCommand getIndFileCommand ()
  {
    return indFileCommand;
  }

  @Override
  public void tsoCommand (String command)
  {
    if (command.startsWith ("IND$FILE") || command.startsWith ("TSO IND$FILE"))
      indFileCommand = new IndFileCommand (command);
  }
}