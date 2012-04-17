//
//  SNARL/SOURCE. Read characters and assert errors.
//
//    James Moen
//    16 Aug 11
//

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

//  SOURCE. Read characters from a Snarl source file. Maybe assert errors.

class Source extends Common
{
  private char           ch;         //  Last character read from LINE.
  private String         line;       //  Last line read from READER.
  private int            lineCount;  //  Number of lines read from READER.
  private int            lineIndex;  //  Index of current character in LINE.
  private String         path;       //  Pathname of source file.
  private BufferedReader reader;     //  Read source characters from here.
  private Assembler      assembler;  //  Used for debugging.

//  Constructor. Return a new SOURCE, positioned at its first character.

  public Source(String path, Assembler assembler)
  {
    this.assembler = assembler;
    try
    {
      lineCount = 0;
      lineIndex = 0;
      this.path = path;
      reader = new BufferedReader(new FileReader(path));
      nextLine();
      nextChar();
    }
    catch (IOException ignore)
    {
      throw new RuntimeException("Cannot open " + path + ".");
    }
  }

//  CLOSE. Break the connection between READER and the source file PATH.

  public void close()
  {
    try
    {
      reader.close();
    }
    catch (IOException ignore)
    {
      throw new RuntimeException("Cannot close " + path + ".");
    }
  }

//  ERROR. Write an error message to standard output, then halt. We first write
//  the current source line, preceded by a 5-digit line number with leading "0"
//  characters. On the next line, we write a caret in the column where we found
//  the error. On the next line, we write MESSAGE.

  public void error(String message)
  {
    if (assembler != null)
      assembler.close();
    int power = 10000;
    int temp  = lineCount;
    for (int count = 1; count <= 5; count += 1)
    {
      System.out.print(temp / power);
      temp %= power;
      power /= 10;
    }
    System.out.println(" " + line);
    writeBlanks(lineIndex + 5);
    System.out.println("^");
    System.out.println(message);
    System.exit(1);
  }

//  GET CHAR. Return the next character from the source file.

  public char getChar()
  {
    return ch;
  }

//  NEXT CHAR. Read the next character from the source file.

  public void nextChar()
  {
    if (atLineEnd())
    {
      nextLine();
    }
    ch = line.charAt(lineIndex);
    lineIndex += 1;
  }

//  AT LINE END. Test if we've reached the end of the current line.

  public boolean atLineEnd()
  {
    return lineIndex >= line.length();
  }

//  NEXT LINE. Read the next LINE from READER and append a blank. The last line
//  is just an EOF CHAR.

  private void nextLine()
  {
    lineCount += 1;
    lineIndex = 0;
    try
    {
      line = reader.readLine();
      if (line == null)
      {
        line = "" + eofChar;
      }
      else
      {
        line += " ";
      }
    }
    catch (IOException ignore)
    {
      throw new RuntimeException("Cannot read " + path + ".");
    }
  }

//  MAIN. For testing. List the file named on the command line.

  public static void main(String[] files)
  {
    Source source = new Source(files[0], null);
    while (source.getChar() != eofChar)
    {
      while (! source.atLineEnd())
      {
        System.out.print(source.getChar());
        source.nextChar();
      }
      System.out.println();
      source.nextChar();
    }
    source.close();
  }
}
