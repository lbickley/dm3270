package com.bytezone.dm3270.display;

import java.util.ArrayList;
import java.util.List;

public class ScreenDetails
{
  private static final String[] tsoMenus =
      { "Menu", "List", "Mode", "Functions", "Utilities", "Help" };

  private final Screen screen;

  private FieldManager fieldManager;
  private List<Field> fields;

  private String datasetsMatching;
  private String datasetsOnVolume;

  private Field tsoCommandField;
  private boolean isTSOCommandScreen;
  private boolean isDatasetList;
  private String currentDataset;
  private String userid = "";
  private String prefix = "";

  public ScreenDetails (Screen screen)
  {
    this.screen = screen;
  }

  public void check (FieldManager fieldManager)
  {
    this.fieldManager = fieldManager;
    fields = fieldManager.getFields ();
    checkTSOCommandField ();
  }

  public Field getTSOCommandField ()
  {
    return tsoCommandField;
  }

  public boolean isTSOCommandScreen ()
  {
    return isTSOCommandScreen;
  }

  public String getCurrentDataset ()
  {
    return currentDataset;
  }

  public String getUserid ()
  {
    return userid;
  }

  public String getPrefix ()
  {
    return prefix;
  }

  private void checkTSOCommandField ()
  {
    int maxLocation = screen.columns * 5 + 20;
    int minLocation = screen.columns;
    boolean promptFound = false;
    tsoCommandField = null;

    for (Field field : fieldManager.getFields ())
    {
      if (field.getFirstLocation () > maxLocation)
        break;

      if (field.getFirstLocation () < minLocation)
        continue;

      int length = field.getDisplayLength ();

      if (promptFound)
      {
        if (field.isProtected () || field.isHidden ())
          break;

        if (length < 48 || (length > 70 && length != 234))
          break;

        tsoCommandField = field;
        break;
      }

      int column = field.getFirstLocation () % screen.columns;
      if (column > 2)
        continue;

      if (field.isUnprotected () || field.isHidden () || length < 4 || length > 15)
        continue;

      String text = field.getText ();

      if (text.endsWith ("===>"))
        promptFound = true;// next loop iteration will return the field
    }

    isTSOCommandScreen = checkTSOCommandScreen ();

    if (prefix.isEmpty ())
      checkPrefixScreen ();

    currentDataset = "";
    isDatasetList = checkDatasetList ();

    if (!isDatasetList)
    {
      checkEditOrViewDataset ();
      if (currentDataset.isEmpty ())
        checkBrowseDataset ();
    }
  }

  private void checkPrefixScreen ()
  {
    if (fields.size () < 73)
      return;

    String ispfScreen = "ISPF Primary Option Menu";

    Field field = fields.get (10);
    if (!ispfScreen.equals (field.getText ()))
      return;

    field = fields.get (23);
    if (!" User ID . :".equals (field.getText ()))
      return;
    if (field.getFirstLocation () != 457)
      return;

    field = fields.get (24);
    if (field.getFirstLocation () != 470)
      return;

    userid = field.getText ().trim ();

    field = fields.get (72);
    if (!" TSO prefix:".equals (field.getText ()))
      return;
    if (field.getFirstLocation () != 1017)
      return;

    field = fields.get (73);
    if (field.getFirstLocation () != 1030)
      return;

    prefix = field.getText ().trim ();
  }

  private boolean checkTSOCommandScreen ()
  {
    if (fields.size () < 14)
      return false;

    Field field = fields.get (10);
    if (!"ISPF Command Shell".equals (field.getText ()))
      return false;

    int workstationFieldNo = 13;
    field = fields.get (workstationFieldNo);
    if (!"Enter TSO or Workstation commands below:".equals (field.getText ()))
    {
      ++workstationFieldNo;
      field = fields.get (workstationFieldNo);
      if (!"Enter TSO or Workstation commands below:".equals (field.getText ()))
        return false;
    }

    List<String> menus = getMenus ();
    if (menus.size () != tsoMenus.length)
      return false;

    int i = 0;
    for (String menu : menus)
      if (!tsoMenus[i++].equals (menu))
        return false;

    field = fields.get (workstationFieldNo + 5);
    if (field.getDisplayLength () != 234)
      return false;

    return true;
  }

  private boolean checkDatasetList ()
  {
    datasetsOnVolume = "";
    datasetsMatching = "";

    if (fields.size () < 19)
      return false;

    Field field = fields.get (9);
    int location = field.getFirstLocation ();
    if (location != 161)
      return false;

    String text = field.getText ();
    if (!text.startsWith ("DSLIST - Data Sets "))
      return false;

    field = fields.get (11);
    location = field.getFirstLocation ();
    if (location != 241)
      return false;
    if (!field.getText ().equals ("Command ===>"))
      return false;

    field = fields.get (18);
    int pos = text.indexOf ("Row ");
    String category = text.substring (19, (pos > 0 ? pos : 64)).trim ();

    if (category.startsWith ("Matching"))
      datasetsMatching = category.substring (9).trim ();
    else if (category.startsWith ("on volume "))
      datasetsOnVolume = category.substring (10).trim ();
    else
      System.out.println ("Unknown category: " + category);

    return true;
  }

  private void checkEditOrViewDataset ()
  {
    if (fields.size () < 13)
      return;

    Field field = fields.get (11);
    int location = field.getFirstLocation ();
    if (location != 161)
      return;

    String text = field.getText ().trim ();
    if (!text.equals ("EDIT") && !text.equals ("VIEW"))
      return;

    field = fields.get (12);
    location = field.getFirstLocation ();
    if (location != 172)
      return;

    text = field.getText ().trim ();
    int pos = text.indexOf (' ');
    if (pos > 0)
    {
      String dataset = text.substring (0, pos);
      currentDataset = dataset;
    }
  }

  private void checkBrowseDataset ()
  {
    if (fields.size () < 8)
      return;

    Field field = fields.get (7);
    int location = field.getFirstLocation ();
    if (location != 161)
      return;

    String text = field.getText ();
    if (!text.equals ("BROWSE   "))
      return;

    field = fields.get (8);
    location = field.getFirstLocation ();
    if (location != 171)
      return;

    text = field.getText ().trim ();
    int pos = text.indexOf (' ');
    if (pos > 0)
    {
      String dataset = text.substring (0, pos);
      currentDataset = dataset;
    }
  }

  private List<String> getMenus ()
  {
    List<String> menus = new ArrayList<> ();

    for (Field field : fields)
    {
      if (field.getFirstLocation () >= screen.columns)
        break;

      if (field.isProtected () && field.isVisible () && field.getDisplayLength () > 1)
      {
        String text = field.getText ().trim ();
        if (!text.isEmpty ())
          menus.add (text);
      }
    }

    return menus;
  }

  @Override
  public String toString ()
  {
    StringBuilder text = new StringBuilder ();

    text.append ("Screen details:\n");
    text.append (String.format ("TSO screen ..... %s%n", isTSOCommandScreen));
    text.append (String.format ("Dataset list ... %s%n", isDatasetList));
    text.append (String.format ("Userid/prefix .. %s / %s%n", userid, prefix));
    text.append (String.format ("Datasets for ... %s%n", datasetsMatching));
    text.append (String.format ("Volume ......... %s%n", datasetsOnVolume));

    return text.toString ();
  }
}