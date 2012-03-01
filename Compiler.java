/*
Compiler.java
Brian Dawn 
Latest Revision: February 29th, 2012.
*/

// A recursive descent compiler for SNARL.
class Compiler extends Common
{
    // Bitstring of all comparison tokens.
    private static final long COMPARISON_SET = 
        makeSet(lessToken, 
                lessGreaterToken, 
                lessEqualToken, 
                greaterEqualToken, 
                greaterToken, 
                equalToken);
    
    // Bitstring of + and - tokens.                         
    private static final long SUM_SET = 
        makeSet(plusToken, 
                dashToken);
                            
    // Bitstring of * and / tokens.                    
    private static final long PRODUCT_SET = 
        makeSet(slashToken,
                starToken);
    
    // Bitstring of not and - tokens.
    private static final long TERM_SET = 
        makeSet(boldNotToken,
                dashToken);
    
    // Bitstring of int, string, and [ tokens.
    private static final long DECLARATION_SET = 
        makeSet(boldIntToken,
                boldStringToken,
                openBracketToken);
    
    // Bitstring of int and string tokens.                 
    private static final long VALUE_SET = 
        makeSet(boldIntToken,
                boldStringToken);

    // Used for handling errors.
    private Source source;
    
    // Used for reading tokens in.
    private Scanner scanner;
    
    // Used to keep track of definitions/scope.
    private SymbolTable symbolTable;
    
    // Assemble instructions to a MIPS assembly file.
    private Assembler assembler;
    
    // Map strings and names to global labels.
    private Global global;
    
    // Used to request and release registers.
    private Allocator allocator;
    
    // Define a basic string type for type checking.
    private BasicType stringType = new BasicType("string", Type.addressSize, null);
    
    // Define a basic integer type for type checking.
    private BasicType intType =  new BasicType("int", Type.wordSize, null);
    
    // Used to keep track of the value types of procedures.
    private BasicType storedValueType = null;
    
    // Used to keep track of the current procedure type.
    private ProcedureType storedProcType = null;
    
    // Describes a global array.
    private class GlobalArrayDescriptor extends GlobalDescriptor
    {
        
        private GlobalArrayDescriptor(Type type, Label label)
        {
            this.type = type;
            this.label = label;
        }
        
        // An array can't be on the left side of an assignment.
        protected Allocator.Register lvalue()
        {
            source.error("Can't assign to array.");
            return null;
        }
        
        // Handle the right side of an assignment.
        protected Allocator.Register rvalue()
        {
            Allocator.Register reg = allocator.request();
            assembler.emit("la", reg, getLabel());
            return reg;
        }
        
        public String toString()
        {
            return "[GlobalArrayDescriptor " + type + " " + label + "]";
        }
    }
    
    // Describes a procedure.
    private class GlobalProcedureDescriptor extends GlobalDescriptor
    {

        private GlobalProcedureDescriptor(Type type, Label label)
        {
            this.type = type;
            this.label = label;
        }

        // A procedure can't be on the left side of an assignment.
        protected Allocator.Register lvalue()
        {
            source.error("Can't assign to procedure.");  //  Your error message here.
            return null;
        }

        // A procedure can't be used as a variable name.
        protected Allocator.Register rvalue()
        {
            source.error("Can't store procedure into variable.");
            return null;
        }

        public String toString()
        {
            return "[GlobalProcedureDescriptor " + type + " " + label + "]";
        }
    }

    // Describe a global variable that's not an array.
    private class GlobalVariableDescriptor extends GlobalDescriptor
    {

        private GlobalVariableDescriptor(Type type, Label label)
        {
            this.type = type;
            this.label = label;
        }

        // Return a register that holds the global variable's address.
        protected Allocator.Register lvalue()
        {
            Allocator.Register reg = allocator.request();
            assembler.emit("la", reg, getLabel());
            return reg;
        }

        // Return a register that holds the global variable's value.
        protected Allocator.Register rvalue()
        {
            Allocator.Register reg = allocator.request();
            assembler.emit("la", reg, getLabel());
            assembler.emit("lw", reg, 0, reg);
            return reg;
        }

        public String toString()
        {
            return "[GlobalVariableDescriptor " + type + " " + label + "]";
        }
    }

    // Describe a local array variable.
    private class LocalArrayDescriptor extends LocalDescriptor
    {

        private LocalArrayDescriptor(Type type, int offset)
        {
            this.type = type;
            this.offset = offset;
        }

        // An array can't be alone on the left side of an assignment.
        protected Allocator.Register lvalue()
        {
            source.error("Can't assign to array.");
            return null;
        }

        // Return a register that holds the address of a local array variable.
        protected Allocator.Register rvalue()
        {
            Allocator.Register reg = allocator.request();
            assembler.emit("lw", reg, getOffset(), allocator.fp);
            assembler.emit("addi", reg, allocator.fp, getOffset());
            return reg;
        }

        public String toString()
        {
            return "[LocalArrayDescriptor " + type + " " + offset + "]";
        }
    }

    // Describe a local variable that's not an array.
    private class LocalVariableDescriptor extends LocalDescriptor
    {

        private LocalVariableDescriptor(Type type, int offset)
        {
            this.type = type;
            this.offset = offset;
        }

        // Return a register that holds the address of the local variable.
        protected Allocator.Register lvalue()
        {
            Allocator.Register reg = allocator.request();
            assembler.emit("addi", reg, allocator.fp, getOffset());
            return reg;
        }

        // Return a register that holds the value of the local variable.
        protected Allocator.Register rvalue()
        {
            Allocator.Register reg = allocator.request();
            assembler.emit("lw", reg, getOffset(), allocator.fp);
            return reg;
        }

        public String toString()
        {
            return "[LocalVariableDescriptor " + type + " " + offset + "]";
        }
    }
    
    
    // Parses the source file found at srcPath.
    public Compiler(String srcPath)
    {
        source = new Source(srcPath);
        scanner = new Scanner(source);
        
        symbolTable = new SymbolTable(source);
        symbolTable.push();
        
        // Pass 1
        passOne();
        
        source.close();
        
        // Reset variables for 2nd pass.
        source = new Source(srcPath);
        scanner = new Scanner(source);
        symbolTable.setSource(source);
        
        // Pass 2
        passTwo();
        
        source.close();
    }
    
    // Pass in a ProcedureType to add the parameter declarations to.
    // Handles procedure parameter declarations for pass one.
    private void passOneDeclaration(ProcedureType procedureType)
    {
        
        switch(scanner.getToken())
        {
            case boldIntToken: 
                scanner.nextToken(); // Skip 'int' token.
                nextExpected(nameToken);
                
                procedureType.addParameter(intType);
            break;
            
            case boldStringToken:
                scanner.nextToken(); // Skip 'string' token.
                nextExpected(nameToken);
                
                procedureType.addParameter(stringType);
            break;
            
            case openBracketToken: 
                
                scanner.nextToken(); // Skip '[' token.
                nextExpected(intConstantToken);
                
                int length = scanner.getInt();
                nextExpected(closeBracketToken);
                nextExpected(boldIntToken);
                nextExpected(nameToken);

                ArrayType arrayType = new ArrayType(length, intType);
                procedureType.addParameter(arrayType);
            break;
            
            default:
                source.error("Declaration expected.");
                break;
        }   
    }
    
    // Handles pass one of the parser.
    private void passOne()
    {
        while(scanner.getToken() != endFileToken)
        {
            if(scanner.getToken() == boldProcToken)
            {
                ProcedureType procedureType = new ProcedureType();
                
                scanner.nextToken(); // Skip 'proc' token.
                nextExpected(nameToken);
    
                String procName = scanner.getString();
                
                nextExpected(openParenToken);
                
                if(tokenIsInSet(scanner.getToken(), DECLARATION_SET))
                {
                    passOneDeclaration(procedureType);
                    
                    while(scanner.getToken() == commaToken)
                    {
                        scanner.nextToken(); // Skip ',' token.
                        
                        passOneDeclaration(procedureType);
                    }
                }
                
                nextExpected(closeParenToken);
                if(!tokenIsInSet(scanner.getToken(), VALUE_SET))
                    source.error("Expected int, or string.");
                    
                switch(scanner.getToken())
                {
                    case boldIntToken:
                        procedureType.addValue(intType);
                    break;
                    case boldStringToken:
                        procedureType.addValue(stringType);
                    break;
                }
                
                symbolTable.setDescriptor(procName, new Descriptor(procedureType));
                scanner.nextToken(); // Skip value token.
            }
            else
            {
                scanner.nextToken();
            }
        }
    }
    
    // Performs pass two of the compiler.
    // Parses program parts separated by ';' tokens.
    // Makes sure the end of the program happens correctly.
    private void passTwo()
    {
        enter("nextProgram");

        nextProgramPart();
        
        while(scanner.getToken() == semicolonToken)
        {
            scanner.nextToken(); // Skip the ; token.
            nextProgramPart();
        }
        
        if(scanner.getToken() != endFileToken)
        {
            source.error("End of program expected.");
        }

        exit("nextProgram");
    }
    
    // Parses a program part. Goes to a declaration or procedure.
    private void nextProgramPart()
    {
        enter("nextProgramPart");
        
        switch(scanner.getToken())
        {
            case boldIntToken:
            case boldStringToken:
            case openBracketToken:
                nextDeclaration();
                break;
            case boldProcToken: 
                nextProcedure();
                break;
            default:
                source.error("Declaration or procedure expected.");
                break;
        }
        exit("nextProgramPart");
    }
    
    // Parses an expression.
    // ex. <conjunction> or <conjunction>
    private RegisterDescriptor nextExpression()
    {
        RegisterDescriptor descriptor = null;
        enter("nextExpression");

        descriptor = nextConjunction();
        
        while(scanner.getToken() == boldOrToken)
        {
            check(descriptor, intType);
            scanner.nextToken(); // Skip 'or' token.
            descriptor = nextConjunction();
            check(descriptor, intType);
        }
        
        exit("nextExpression");
        return descriptor;
    }
    
    // Parses a conjunction.
    // ex. <comparison> and <comparison>
    private RegisterDescriptor nextConjunction()
    {
        RegisterDescriptor descriptor = null;
        enter("nextConjunction");

        descriptor = nextComparison();
        
        while(scanner.getToken() == boldAndToken)
        {
            check(descriptor, intType);
            scanner.nextToken(); // Skip 'and' token.
            descriptor = nextComparison();
            check(descriptor, intType);
        }
        
        exit("nextConjunction");
        return descriptor;
    }
    
    // Parses a comparison.
    // ex. <sum> < <sum>
    private RegisterDescriptor nextComparison()
    {
        RegisterDescriptor descriptor = null;
        enter("nextComparison");

        descriptor = nextSum();
        
        if (tokenIsInSet(scanner.getToken(), COMPARISON_SET))
        {
            check(descriptor, intType);
            scanner.nextToken(); // Skip comparison token.
            descriptor = nextSum();
            check(descriptor, intType);
        }
        
        exit("nextComparison");
        return descriptor;
    }
    
    // Parses a sum.
    // ex. <product> + <product> - <product>
    private RegisterDescriptor nextSum()
    {
        RegisterDescriptor descriptor = null;
        enter("nextSum");

        descriptor = nextProduct();
        while (tokenIsInSet(scanner.getToken(), SUM_SET))
        {
            check(descriptor, intType);
            scanner.nextToken(); // Skip '+' or '-' token.
            descriptor = nextProduct();
            check(descriptor, intType);
        }
        
        exit("nextSum");
        return descriptor;
    }
    
    // Parses a product.
    // ex. <term> * <term> / <term>
    private RegisterDescriptor nextProduct()
    {
        RegisterDescriptor descriptor = null;
        enter("nextProduct");

        descriptor = nextTerm();
        while (tokenIsInSet(scanner.getToken(), PRODUCT_SET))
        {
            check(descriptor, intType);
            scanner.nextToken(); // Skip '*' or '/' token.
            descriptor = nextTerm();
            check(descriptor, intType);
            
        }
        
        exit("nextProduct");
        return descriptor;
    }
    
    // Parses a term.
    // ex. -<unit>
    private RegisterDescriptor nextTerm()
    {
        RegisterDescriptor descriptor = null;
        enter("nextTerm");
        
        if (tokenIsInSet(scanner.getToken(), TERM_SET))
        {
            descriptor = nextTerm();
            check(descriptor, intType);
        }
        else
            descriptor = nextUnit();
        
        exit("nextTerm");
        return descriptor;
    }
    
    // Parses the argument list of a call.
    private Descriptor nextCall()
    {
        Descriptor descriptor = null;
        enter("nextCall");
        
        int arity = 0;
        scanner.nextToken(); // Skip '(' token.
        if (scanner.getToken() != closeParenToken)
        {
            arity++;
            if(storedProcType.getArity() < arity)
                source.error("Invalid number of arguments.");
                
            ProcedureType.Parameter param = storedProcType.getParameters();
            descriptor = nextExpression();
            check(descriptor, param.getType());
            
            
            while (scanner.getToken() == commaToken)
            {
                nextExpected(commaToken, ", or ) expected.");
                
                arity++;
                if(storedProcType.getArity() < arity)
                    source.error("Invalid number of arguments.");
                    
                descriptor = nextExpression();
                param = param.getNext();
                check(descriptor, param.getType());
            }
        }
        nextExpected(closeParenToken);
        
        if(storedProcType.getArity() != arity)
            source.error("Invalid number of arguments.");
            
        exit("nextCall");
        return new Descriptor(storedProcType.getValue());
    }
    
    // Parses a unit.
    private RegisterDescriptor nextUnit()
    {
        enter("nextUnit");
        RegisterDescriptor descriptor;
        Allocator.Register reg;
        
        switch (scanner.getToken())
        {
            case intConstantToken: 
                
                reg = allocator.request();
                assembler.emit("li", reg, scanner.getInt());
                
                scanner.nextToken(); // Skip int constant token.
                
                descriptor = new RegisterDescriptor(intType, reg);
                
                break;
                
            case stringConstantToken:
                
                
                reg = allocator.request();
                Label label = global.enterString(scanner.getString());
                assembler.emit("la", reg, label);
                
                scanner.nextToken(); // Skip string constant token.
                
                descriptor = new RegisterDescriptor(stringType, reg);
                
                break;
                
            case openParenToken:
                scanner.nextToken(); // Skip '(' token.
                
                descriptor = nextExpression();
                
                nextExpected(closeParenToken);
                break;
                
            case nameToken:
                nextExpected(nameToken);
                
                descriptor = symbolTable.getDescriptor(scanner.getString());
                
                switch (scanner.getToken())
                {
                    case openParenToken:

                        checkProcType();
                        
                        descriptor = nextCall();
                        break;
                        
                    case openBracketToken:
                    
                        descriptor = 
                            symbolTable.getDescriptor(scanner.getString());
                        
                        if(! (descriptor.getType() instanceof ArrayType))
                            source.error(scanner.getString() + 
                                " is not an array.");
                            
                        scanner.nextToken(); // Skip '[' token.
                        descriptor = nextExpression();
                        check(descriptor, intType);
                        
                        nextExpected(closeBracketToken);
                        break;
                }
                
                break;
            default:
                source.error("Unit expected.");
                break;
        }
        
        exit("nextUnit");
        return descriptor;
    }
    
    // Checks a procedure call and sets storedProcType.
    private void checkProcType()
    {
        Descriptor descriptor = 
            symbolTable.getDescriptor(scanner.getString());
    
        ProcedureType procType = null;
    
        if(!(descriptor.getType() instanceof ProcedureType))
            source.error(scanner.getString() + 
                " is not a procedure.");
        else
        {
            procType = (ProcedureType)descriptor.getType();
        }
    
        storedProcType = procType;  
    }
    
    // Parses a statement.
    private void nextStatement()
    {
        enter("nextStatement");
        
        Descriptor desc;
        switch (scanner.getToken())
        {
            case nameToken:
                nextExpected(nameToken);
                
                switch (scanner.getToken())
                {
                    case openParenToken:
                        checkProcType();
                    
                        nextCall();
                        break;
                        
                    case openBracketToken:
                    
                        desc = 
                            symbolTable.getDescriptor(scanner.getString());
                        if(! (desc.getType() instanceof ArrayType))
                            source.error(scanner.getString() + 
                                " is not an array.");
                            
                        scanner.nextToken(); // Skip '[' token.
                        desc = nextExpression();
                        check(desc, intType);
                        
                        nextExpected(closeBracketToken);
                        nextExpected(colonEqualToken);
                        desc = nextExpression();
                        check(desc, intType);
                        break;
                        
                    default:
                        desc = symbolTable.getDescriptor(scanner.getString());
                        
                        if(!desc.getType().isSubtype(intType) &&
                            !desc.getType().isSubtype(stringType))
                        {
                            source.error("Only variables of type int or" +
                                " string may be assigned to.");
                        }
                        nextExpected(colonEqualToken);
                        Descriptor expressionDesc = nextExpression();
                        check(desc, expressionDesc.getType());
                        
                        break;
                }
                break;
                
            case boldBeginToken:
                nextBegin();
                break;
            
            case boldCodeToken:
                nextCode();
                break;
                
            case boldIfToken:
                nextIf();
                break;
                
            case boldValueToken:
                nextValue();
                break;
                
            case boldWhileToken:
                nextWhile();
                break;
                
            default:
                source.error("Statement expected.");
                break;
        }
        
        exit("nextStatement");
    }
    
    // Parses a begin statement.
    private void nextBegin()
    {
        enter("nextBegin");
        
        scanner.nextToken(); // Skip 'begin' token.
        if(scanner.getToken() != boldEndToken)
        {
            nextStatement();
            while (scanner.getToken() == semicolonToken)
            {
                scanner.nextToken(); // Skip ';' token.
                nextStatement();
            }
        }
        nextExpected(boldEndToken);
        
        exit("nextBegin");
    }
    
    // Parses a code statement.
    private void nextCode()
    {
        enter("nextCode");
        
        scanner.nextToken(); // Skip code token.
        nextExpected(stringConstantToken);
        
        exit("nextCode");
    }
    
    // Parses an if statement.
    // ex. if <expression> then <statement>
    private void nextIf()
    {
        enter("nextIf");
        
        while(scanner.getToken() == boldIfToken)
        {
            scanner.nextToken(); // Skip 'if' token.
        
            Descriptor desc = nextExpression();
            check(desc, intType);
            
            nextExpected(boldThenToken);
            nextStatement();
               
            if(scanner.getToken() == boldElseToken)
            {
                scanner.nextToken(); // Skip 'else' token.
                if (scanner.getToken() != boldIfToken)
                {
                    nextStatement();
                    break;
                }
            }
            else
            {
                break;
            }
        }
        
        exit("nextIf");  
    }
    
    // Parses a value statement.
    private void nextValue()
    {
        enter("nextValue");
        
        scanner.nextToken(); // Skip 'value' token.
        Descriptor desc = nextExpression();
        check(desc, storedValueType);
        
        exit("nextValue");
    }
    
    // Parses a while statement.
    private void nextWhile()
    {
        enter("nextWhile");
        
        scanner.nextToken(); // Skip 'while' token.
        Descriptor desc = nextExpression();
        check(desc, intType);
        
        nextExpected(boldDoToken);
        nextStatement();
        
        exit("nextWhile");
    }
    
    // Parses a declaration.
    private void nextDeclaration()
    {
        enter("nextDeclaration");
        switch(scanner.getToken())
        {
            case boldIntToken: nextIntDeclaration(); break;
            case boldStringToken: nextStringDeclaration(); break;
            case openBracketToken: nextArrayDeclaration(); break;
            default:
                source.error("Declaration expected.");
                break;
        }    
        exit("nextDeclaration");
    }
    
    // Parses an int declaration.
    private void nextIntDeclaration()
    {
        enter("nextIntDeclaration");
        
        scanner.nextToken(); // Skip 'int' token.
        
        Descriptor descriptor = new Descriptor(intType);
        symbolTable.setDescriptor(scanner.getString(), descriptor);
        
        nextExpected(nameToken);
        
        exit("nextIntDeclaration");
    }
    
    // Parses a string declaration.
    private void nextStringDeclaration()
    {
        enter("nextStringDeclaration");
        
        scanner.nextToken(); // Skip 'string' token.
        
        Descriptor descriptor = new Descriptor(stringType);
        symbolTable.setDescriptor(scanner.getString(), descriptor);
        
        nextExpected(nameToken);
        
        exit("nextStringDeclaration");
    }
    
    // Parses an array declaration.
    private void nextArrayDeclaration()
    {
        enter("nextArrayDeclaration");
        
        scanner.nextToken(); // Skip '[' token.
        nextExpected(intConstantToken);
        nextExpected(closeBracketToken);
        nextExpected(boldIntToken);
        nextExpected(nameToken);
        
        ArrayType arrayType = new ArrayType(scanner.getInt(), intType);
        Descriptor descriptor = new Descriptor(arrayType);
        symbolTable.setDescriptor(scanner.getString(), descriptor);
        
        exit("nextArrayDeclaration");
    }
    
    // Parses an entire procedure.
    private void nextProcedure()
    {
        enter("nextProcedure");
        
        symbolTable.push();
        scanner.nextToken(); // Skip 'proc' token.
        nextExpected(nameToken);
        nextProcedureSignature();
        nextExpected(colonToken);
        nextProcedureBody();
        symbolTable.pop();
        
        exit("nextProcedure");
    }
    
    // Parses the body of a procedure.
    private void nextProcedureBody()
    {
        enter("nextProcedureBody");
        
        if(tokenIsInSet(scanner.getToken(), DECLARATION_SET))
        {
            nextDeclaration();
            while(scanner.getToken() == semicolonToken)
            {
                scanner.nextToken(); // Skip ';' token.
                nextDeclaration();
            }
        }
        
        nextBegin();
            
        exit("nextProcedureBody");
    }
    
    // Parses the signature part of a procedure.
    private void nextProcedureSignature()
    {
        enter("nextProcedureSignature");
        
        nextExpected(openParenToken);
        if(tokenIsInSet(scanner.getToken(), DECLARATION_SET))
        {
            nextDeclaration();
            while(scanner.getToken() == commaToken)
            {
                scanner.nextToken(); // Skip ',' token.
                nextDeclaration();
            }
        }
        
        nextExpected(closeParenToken);
        
        if(!tokenIsInSet(scanner.getToken(), VALUE_SET))
            source.error("Expected int, or string.");
        
        switch(scanner.getToken())
        {
            case boldIntToken:
                storedValueType = intType;
            break;
            case boldStringToken:
                storedValueType = stringType;
            break;
        }

        scanner.nextToken(); // Skip 'value' token.
        
        exit("nextProcedureSignature");
    }
    
    // Scans for the given token, if it doesn't appear we give
    // a generic error message. If it is present we consume it.
    private void nextExpected(int token)
    {
        if (scanner.getToken() == token)
            scanner.nextToken();
        else
            source.error(tokenToString(token) + " expected.");
    }
    
    // nextExpected but we can specify a different error message.
    private void nextExpected(int token, String comment)
    {
        if (scanner.getToken() == token)
            scanner.nextToken();
        else
            source.error(comment);
    }
    
    // Checks if descriptor's type has a subtype of type.
    private void check(Descriptor descriptor, Type type)
    { 
        if(!descriptor.getType().isSubtype(type))
        { 
            source.error("Expression has unexpected type."); 
        }
    }
         
    // Run some test code.
    public static void main(String[] args)
    {
        Compiler p = new Compiler(args[0]);
    }
    
    // Checks if a token is in a set represented by a bitstring.
    private static boolean tokenIsInSet(int token, long set)
    {
        return (set & (1L << token)) != 0;
    }
    
    // Returns a bitstring composed of the passed in arguments.
    private static long makeSet(int... elements)
    {
        long set = 0L;
        for(int element:elements)
        {
            set |= 1L << element;
        }
        return set;
    }
}