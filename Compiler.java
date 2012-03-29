/*
Compiler.java
Brian Dawn 
Latest Revision: March 29th, 2012.
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
    
    // Offset for declared local variables.
    private int offset = 0;
    
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
            source.error("Can't assign to procedure.");
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
        assembler = new Assembler("out.asm");

        source = new Source(srcPath, assembler);
        scanner = new Scanner(source);

        global = new Global(assembler);
        allocator = new Allocator(source);
        
        symbolTable = new SymbolTable(source);
        symbolTable.push();
        
        // Pass 1
        passOne();
        
        source.close();
        
        // Reset variables for 2nd pass.
        source = new Source(srcPath, assembler);
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

                Label label = global.enterString(procName);
                GlobalProcedureDescriptor descriptor = new GlobalProcedureDescriptor(procedureType, label);
                symbolTable.setDescriptor(procName, descriptor);

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
                nextDeclaration(true);
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
    // TODO: unsure how to fix descriptors for this method.
    private RegisterDescriptor nextCall()
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
        // return new NameDescriptor(storedProcType.getValue());
        return null;
    }
    
    // Parses a unit.
    private RegisterDescriptor nextUnit()
    {
        enter("nextUnit");
        
        // Initialize to null just so the compiler stops complaining it might
        // not have been initialized.
        RegisterDescriptor descriptor = null;
        Allocator.Register reg = null;
        
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
                
                NameDescriptor nameDes = symbolTable.getDescriptor(scanner.getString());
                descriptor = new RegisterDescriptor(nameDes.getType(), nameDes.rvalue());
                
                switch (scanner.getToken())
                {
                    case openParenToken:

                        checkProcType();
                        
                        //nameDes = nextCall();
                        break;
                        
                    case openBracketToken:
                    
                        nameDes = 
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
    
    // Parses a local declaration.
    private void nextDeclaration(boolean isGlobal)
    {
        enter("nextDeclaration");
        switch(scanner.getToken())
        {
            case boldIntToken: nextIntDeclaration(isGlobal); break;
            case boldStringToken: nextStringDeclaration(isGlobal); break;
            case openBracketToken: nextArrayDeclaration(isGlobal); break;
            default:
                source.error("Declaration expected.");
                break;
        }    
        exit("nextDeclaration");
    }
    
    // Parses an int declaration.
    private void nextIntDeclaration(boolean isGlobal)
    {
        enter("nextIntDeclaration");
        
        scanner.nextToken(); // Skip 'int' token.
        
        if (isGlobal)
        {
            Label label = global.enterVariable(intType);
            GlobalVariableDescriptor descriptor = new GlobalVariableDescriptor(intType, label);
            symbolTable.setDescriptor(scanner.getString(), descriptor);
        }
        else
        {
            LocalVariableDescriptor descriptor = new LocalVariableDescriptor(intType, offset);
            symbolTable.setDescriptor(scanner.getString(), descriptor);
            offset -= intType.getSize();
        }
        
        nextExpected(nameToken);
        
        exit("nextIntDeclaration");
    }
    
    // Parses a string declaration.
    private void nextStringDeclaration(boolean isGlobal)
    {
        enter("nextStringDeclaration");
        
        scanner.nextToken(); // Skip 'string' token.
        
        if (isGlobal)
        {
            Label label = global.enterString(scanner.getString());
            GlobalVariableDescriptor descriptor = new GlobalVariableDescriptor(stringType, label);
            symbolTable.setDescriptor(scanner.getString(), descriptor);
        }
        else
        {
            LocalVariableDescriptor descriptor = new LocalVariableDescriptor(stringType, offset);
            symbolTable.setDescriptor(scanner.getString(), descriptor);
            offset -= stringType.getSize();
        }
        nextExpected(nameToken);
        
        exit("nextStringDeclaration");
    }
    
    // Parses an array declaration.
    private void nextArrayDeclaration(boolean isGlobal)
    {
        enter("nextArrayDeclaration");
        
        scanner.nextToken(); // Skip '[' token.
        nextExpected(intConstantToken);
        nextExpected(closeBracketToken);
        nextExpected(boldIntToken);
        nextExpected(nameToken);
        
        ArrayType arrayType = new ArrayType(scanner.getInt(), intType);

        if (isGlobal)
        {
            Label label = global.enterVariable(arrayType);
            GlobalArrayDescriptor descriptor = new GlobalArrayDescriptor(arrayType, label);
            symbolTable.setDescriptor(scanner.getString(), descriptor);
        }
        else
        {
            LocalArrayDescriptor descriptor = new LocalArrayDescriptor(arrayType, offset);
            symbolTable.setDescriptor(scanner.getString(), descriptor);
            offset -= arrayType.getSize();
        }
        
        exit("nextArrayDeclaration");
    }
    
    // Emits code for a procedure prelude.
    private void emitProcedurePrelude(int localSizeSum, int arity)
    {
        assembler.emit("# Begin procedure prelude.");
        assembler.emit("addi", allocator.sp, allocator.sp, -(40 + localSizeSum));

        assembler.emit("sw", allocator.ra, 40, allocator.sp);
        assembler.emit("sw", allocator.fp, 36, allocator.sp);

        for(int i = 0; i<8; i++)
        {
            int offset = 32 - i * 4;
            String emit = "sw $s" + i + ", " + offset + "($sp)";
            assembler.emit(emit);
        }

        assembler.emit("addi", allocator.fp, allocator.sp, (40 + localSizeSum + 4 * arity));
        assembler.emit("# End procedure prelude.");
    }

    // Emits code for a procedure postlude.
    private void emitProcedurePostlude(int localSizeSum, int arity)
    {
        assembler.emit("# Begin procedure postlude.");
        assembler.emit("lw", allocator.ra, 40, allocator.sp);
        assembler.emit("lw", allocator.fp, 36, allocator.sp);

        for(int i = 0; i<8; i++)
        {
            int offset = 32 - i * 4;
            String emit = "lw $s" + i + ", " + offset + "($sp)";
            assembler.emit(emit);
        }

        assembler.emit("addi", allocator.sp, allocator.sp, (40 + localSizeSum + 4 * arity));
        assembler.emit("jr $ra");
        assembler.emit("# End procedure postlude.");
    }

    // Parses an entire procedure.
    private void nextProcedure()
    {
        enter("nextProcedure");
        
        symbolTable.push();
        offset = 0; // Set the global offset to 0 for the new procedure.
        scanner.nextToken(); // Skip 'proc' token.
        nextExpected(nameToken);
        
        // Get the label of the procedure from the symbol table.
        String procName = scanner.getString();
        GlobalProcedureDescriptor descriptor = 
            (GlobalProcedureDescriptor)symbolTable.getDescriptor(procName);

        assembler.emit("# Procedure " + procName + ".");
        nextProcedureSignature();
        nextExpected(colonToken);
        nextProcedureBody(descriptor);
        symbolTable.pop();
        
        exit("nextProcedure");
    }
    
    // Parses the body of a procedure.
    private void nextProcedureBody(GlobalProcedureDescriptor descriptor)
    {
        enter("nextProcedureBody");
        
        // At this point offset contains the parameters.
        int oldOffset = offset;
        if(tokenIsInSet(scanner.getToken(), DECLARATION_SET))
        {
            nextDeclaration(false);
            while(scanner.getToken() == semicolonToken)
            {
                scanner.nextToken(); // Skip ';' token.
                nextDeclaration(false);
            }
        }
        // At this point offset contains both the locals and the parameters.
        int localSizeSum = oldOffset - offset; // Calculate local(p).
        int arity = ((ProcedureType)descriptor.getType()).getArity();
        emitProcedurePrelude(localSizeSum, arity);

        assembler.emit("# Begin procedure body.");
        nextBegin();
        assembler.emit("# End procedure body.");

        emitProcedurePostlude(localSizeSum, arity);
            
        exit("nextProcedureBody");
    }
    
    // Parses the signature part of a procedure.
    private void nextProcedureSignature()
    {
        enter("nextProcedureSignature");
        
        nextExpected(openParenToken);
        if(tokenIsInSet(scanner.getToken(), DECLARATION_SET))
        {
            nextDeclaration(false);
            while(scanner.getToken() == commaToken)
            {
                scanner.nextToken(); // Skip ',' token.
                nextDeclaration(false);
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
    
    // Checks if descriptor's type is a subtype of type.
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
        Compiler p = new Compiler("example.snarl");
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
