/*
Scanner.java
Brian Dawn 
Latest Revision: November 26th, 2011.

This class scans through a Source object and converts the characters
into tokens for SNARL.
*/

import java.util.HashMap;

class Scanner extends Common
{
    //The token ID of the current token.
    private int token;
    
    //String representation of current token.
    private String tokenString;
    
    //Integer representation of current token.
    private int tokenInt;
    
    //The source object to read characters from.
    private Source source;
    
    //Hashmap that stores all reserved words.
    private HashMap<String, Integer> reservedTokens;
    
    //Instantiate a new Scanner object. Initialize the
    //token to the first non-ignored token.
    public Scanner(Source source)
    {
        this.source = source;
        reservedTokens = new HashMap<String, Integer>();
        
        //Populate our HashMap with the reserved names and their IDs.
        //Use the Strings as the keys, so we can grab the token ID later.
        reservedTokens.put("and", boldAndToken);
        reservedTokens.put("begin", boldBeginToken);
        reservedTokens.put("code", boldCodeToken);
        reservedTokens.put("do", boldDoToken);
        reservedTokens.put("else", boldElseToken);
        reservedTokens.put("end", boldEndToken);
        reservedTokens.put("if", boldIfToken);
        reservedTokens.put("int", boldIntToken);
        reservedTokens.put("not", boldNotToken);
        reservedTokens.put("or", boldOrToken);
        reservedTokens.put("proc", boldProcToken);
        reservedTokens.put("string", boldStringToken);
        reservedTokens.put("then", boldThenToken);
        reservedTokens.put("value", boldValueToken);
        reservedTokens.put("while", boldWhileToken);

        nextToken(); //Advance to the first non-ignored token.
    }
    
    //Advance the token to the next token from source.
    public void nextToken()
    {
        token = ignoredToken;
        
        while(token == ignoredToken)
        {
            
            switch(source.getChar())
            {
                case '[':
                    token = openBracketToken;
                    source.nextChar();
                    break;
 
                case ']':
                    token = closeBracketToken;
                    source.nextChar();
                    break;
                    
                case '(':
                    token = openParenToken;
                    source.nextChar();
                    break; 
                     
                case ')':
                    token = closeParenToken;
                    source.nextChar();
                    break;   
                    
                case ',':
                    token = commaToken;
                    source.nextChar();
                    break;
                    
                case '-':
                    token = dashToken;
                    source.nextChar();
                    break;
                    
                case '=':
                    nextComparison();
                    break;
                    
                case '<':
                    nextComparison();
                    break;
                    
                case '>':
                    nextComparison();
                    break;
                    
                case '+':
                    token = plusToken;
                    source.nextChar();
                    break;
                    
                case ';':
                    token = semicolonToken;
                    source.nextChar();
                    break;
                    
                case '/':
                    token = slashToken;
                    source.nextChar();
                    break;
                    
                case '*':
                    token = starToken;
                    source.nextChar();
                    break;
                    
                case '"':
                    nextStringConstant();
                    break;
                                                                     
                case '#':
                    nextComment();
                    break;
                
                case ':':
                    nextColonOrAssignment();
                    break;
                    
                case ' ':
                    token = ignoredToken;
                    source.nextChar();
                    break;
                
                case '\t':
                    token = ignoredToken;
                    source.nextChar();
                    break;
                    
                case eofChar:
                    token = endFileToken;
                    break;
                    
                default:
                    if ( isLetter(source.getChar()) )
                    {
                        nextName();
                    }
                    else if ( isDigit(source.getChar()) )
                    {
                        nextIntConstant();
                    }
                    else //Otherwise we have an unrecognized token.
                    {
                        source.error("Unrecognized symbol.");
                    }
                    break;
            }
        }
    }
    
    //Returns the current token number.
    public int getToken()
    {
        return token;
    }
    
    //Return the String representation of the current token.
    //This is only valid if the current token is of the following:
    //  intConstantToken
    //  nameToken
    //  stringConstantToken
    public String getString()
    {
        return tokenString;
    }
    
    //Return the int representation of the current token.
    //This is only valid if the current token is an intConstantToken.
    public int getInt()
    {
        return tokenInt;
    }
    
    //Returns true if the char is a letter in the English alphabet, 
    //otherwise false.
    private boolean isLetter(char c)
    {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }
    
    //Returns true if the char is 0 through 9, otherwise false.
    private boolean isDigit(char c)
    {
        return (c >= '0' && c <= '9');
    }
    
    //Handles integer constants, which are one or more digits.
    //For example 012334.
    private void nextIntConstant()
    {
        StringBuilder stringBuilder = new StringBuilder();
        
        while( isDigit(source.getChar()) )
        {
            stringBuilder.append(source.getChar());
            source.nextChar();
        }
        
        token = intConstantToken;
        tokenString = stringBuilder.toString();
        
        //Check to make sure this is a valid integer.
        try
        {
            tokenInt = Integer.parseInt(tokenString);
        }
        catch(Exception e)
        {
            source.error("Invalid integer constant.");
        }
    }
    
    //Recognizes string constants. For example "hello world".
    private void nextStringConstant()
    {
        source.nextChar(); //Skip the first " char.
        
        StringBuilder stringBuilder = new StringBuilder();
        while( source.getChar() != '"' )
        {
            if( source.atLineEnd() )
            {
                source.error("Missing closing quote for string constant.");
            }
            stringBuilder.append( source.getChar() );
            source.nextChar();
        }
        
        token = stringConstantToken;
        tokenString = stringBuilder.toString();
        
        source.nextChar(); //Skip the last " char.
    }
    
    //Method for handling comparison tokens.
    private void nextComparison()
    {
        
        if (source.getChar() == '=')
        {
            token = equalToken;
            source.nextChar();
            return;
        }
        else if (source.getChar() == '<')
        {
            source.nextChar();
            
            if (source.getChar() == '>')
            {
                token = lessGreaterToken;
                source.nextChar();
            }
            else if(source.getChar() == '=')
            {
                token = lessEqualToken;
                source.nextChar();
            }
            else
            {
                token = lessToken;
            } 
        }
        else if (source.getChar() == '>')
        {
            source.nextChar();
            if( source.getChar() == '=')
            {
                token = greaterEqualToken;
                source.nextChar();
            }
            else
            {
                token = greaterToken;
            }
        }  
    }
    
    //Method for recognizing names. Names start with a
    //letter followed by any number of digits and letters.
    private void nextName()
    {
        StringBuilder strBuilder = new StringBuilder();
        
        while( isLetter(source.getChar()) || isDigit(source.getChar()) )
        {
            strBuilder.append(source.getChar());
            
            source.nextChar();
        } 
        
        String name = strBuilder.toString();
        
        
        Integer value = reservedTokens.get(name);
        if( value == null)
        {
            token = nameToken;
        }
        else //It is a reserved word.
        {
            token = value;
        }
        
        tokenString = name;
    }
    
    //Method for recognizing colonToken or colonEqualToken.
    private void nextColonOrAssignment()
    {
        source.nextChar(); //Skip the : char.
        
        if (source.getChar() == '=')
        {
            token = colonEqualToken;
            source.nextChar();
        }
        else
        {
            token = colonToken;
        }
    }
    
    //Method for skipping over comments.
    private void nextComment()
    {
        source.nextChar(); //Skip the # char.
        
        while(!source.atLineEnd())
        {
            source.nextChar();
        }
        
        source.nextChar(); //Skip the newline char.
        
        token = ignoredToken;
    }
    
    public static void main(String [] args)
    {
        Source src = new Source(args[0], null);
        Scanner scan = new Scanner(src);
        
        while( scan.getToken() != Common.endFileToken )
        {
            if( scan.getToken() == Common.intConstantToken )
            {
                System.out.println( tokenToString(scan.getToken()) + 
                    " " + scan.getInt());
            }
            else if( scan.getToken() == Common.stringConstantToken ||
                scan.getToken() == Common.nameToken)
            {
                System.out.println( tokenToString(scan.getToken()) +
                    " \"" + scan.getString() + "\"");
            }
            else
            {
                System.out.println( tokenToString(scan.getToken()));
            }
            
            scan.nextToken();
        }
    }
}
