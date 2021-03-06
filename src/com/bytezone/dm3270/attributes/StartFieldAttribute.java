package com.bytezone.dm3270.attributes;

import com.bytezone.dm3270.display.ContextManager;
import com.bytezone.dm3270.display.ScreenContext;
import com.bytezone.dm3270.orders.BufferAddress;

import javafx.scene.paint.Color;

public class StartFieldAttribute extends Attribute
{
  private static final Color WHITE = ColorAttribute.colors[0];
  private static final Color BLUE = ColorAttribute.colors[1];
  private static final Color RED = ColorAttribute.colors[2];
  private static final Color GREEN = ColorAttribute.colors[4];
  private static final Color BLACK = ColorAttribute.colors[8];

  private final boolean isProtected;      // bit 2
  private final boolean isNumeric;        // bit 3
  private final boolean isModified;       // bit 7

  private boolean isExtended;         // created by StartFieldExtendedOrder
  private boolean userModified;       // used to avoid altering the original bit 7

  // these three fields are stored in two bits (4&5)
  private final boolean isHidden;
  private final boolean isHighIntensity;
  private final boolean selectorPenDetectable;

  public StartFieldAttribute (byte b)
  {
    super (AttributeType.START_FIELD, Attribute.XA_START_FIELD, b);

    isProtected = (b & 0x20) > 0;
    isNumeric = (b & 0x10) > 0;
    isModified = (b & 0x01) > 0;

    int display = (b & 0x0C) >> 2;
    selectorPenDetectable = display == 1 || display == 2;
    isHidden = display == 3;
    isHighIntensity = display == 2;
  }

  public void setExtended ()
  {
    isExtended = true;
  }

  public static byte compile (boolean prot, boolean num, boolean bit4, boolean bit5,
      boolean mod)
  {
    int value = 0;

    if (prot)
      value |= 0x20;
    if (num)
      value |= 0x10;
    if (bit4)
      value |= 0x08;
    if (bit5)
      value |= 0x04;
    if (mod)
      value |= 0x01;

    return BufferAddress.address[value];
  }

  public boolean isExtended ()
  {
    return isExtended;
  }

  public boolean isProtected ()           // P/p - Protected/unprotected
  {
    return isProtected;
  }

  public boolean isAlphanumeric ()        // A/a - Alphanumeric/numeric
  {
    return !isNumeric;
  }

  public boolean isHidden ()              // V/v - Visible/not visible
  {
    return isHidden;
  }

  public boolean isVisible ()             // V/v - Visible/not visible
  {
    return !isHidden;
  }

  public boolean isIntensified ()         // I/i - Intensified/not intensified
  {
    return isHighIntensity;
  }

  public boolean isDetectable ()          // D/d - Detectable/not detectable
  {
    return selectorPenDetectable;
  }

  public boolean isModified ()            // M/m - Modified/not modified
  {
    return isModified || userModified;
  }

  public boolean isAutomaticSkip ()
  {
    return isProtected && isNumeric;
  }

  public void setModified (boolean modified)
  {
    userModified = modified;
  }

  /*
   * http://www-01.ibm.com/support/knowledgecenter/SSGMGV_3.1.0/com.ibm.cics.
   * ts31.doc/dfhp3/dfhp3at.htm%23dfhp3at
   * 
   * Some terminals support base color without, or in addition to, the extended 
   * colors included in the extended attributes. There is a mode switch on the 
   * front of such a terminal, allowing the operator to select base or default 
   * color. Default color shows characters in green unless field attributes specify
   * bright intensity, in which case they are white. In base color mode, the 
   * protection and intensity bits are used in combination to select among four 
   * colors: normally white, red, blue, and green; the protection bits retain their 
   * protection functions as well as determining color. (If you use extended color, 
   * rather than base color, for 3270 terminals, note that you cannot specify 
   * "white" as a color. You need to specify "neutral", which is displayed as white 
   * on a terminal.)
   */

  @Override
  public ScreenContext process (ContextManager contextManager, ScreenContext unused1,
      ScreenContext unused2)
  {
    assert unused1 == null && unused2 == null;

    Color color = isHighIntensity ? //
        isProtected ? WHITE : RED : //
        isProtected ? BLUE : GREEN;

    return contextManager.getScreenContext (color, BLACK, (byte) 0, isHighIntensity);
  }

  private String getColorName ()
  {
    return isHighIntensity ?        //
        isProtected ? "WH" : "RE" : //
        isProtected ? "BL" : "GR";
  }

  public String getAcronym ()
  {
    StringBuilder text = new StringBuilder ();

    text.append (isProtected ? "P" : "p");              // Protected
    text.append (isNumeric ? "a" : "A");                // Alphanumeric
    text.append (isHidden ? "v" : "V");                 // Visible
    text.append (isHighIntensity ? "I" : "i");          // Intensified
    text.append (selectorPenDetectable ? "D" : "d");    // Detectable
    text.append (isModified () ? "M" : "m");            // Modified

    return text.toString ();
  }

  @Override
  public String toString ()
  {
    return String.format ("Attribute %s : %02X %s", getColorName (), attributeValue,
                          getAcronym ());
  }
}