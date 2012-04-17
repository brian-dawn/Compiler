/*
Compiler.java
Author: Brian Dawn 
Collaborated with: Paul Martinek
Latest Revision: April 16th, 2012.
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

                Label label = new Label(procName);
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
                nextGlobalDeclaration();
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

        enter("nextExpression");

        RegisterDescriptor left = nextConjunction();
        Label label = new Label("expression");

        assembler.emit("sne", left.getRegister(), left.getRegister(), allocator.zero);
        assembler.emit("bne", left.getRegister(), allocator.zero, label);

        while(scanner.getToken() == boldOrToken)
        {
            check(left, intType);

            scanner.nextToken(); // Skip 'or' token.
            RegisterDescriptor des = nextConjunction();
            check(des, intType);

            assembler.emit("sne", left.getRegister(), des.getRegister(), allocator.zero);
            assembler.emit("bne", left.getRegister(), allocator.zero, label);
            allocator.release(des.getRegister());
        }

        assembler.emit(label);
        
        exit("nextExpression");
        return left;
    }
    
    // Parses a conjunction.
    // ex. <comparison> and <comparison>
    private RegisterDescriptor nextConjunction()
    {
        enter("nextConjunction");

        RegisterDescriptor left = nextComparison();
        Label label = new Label("conjunction");

        assembler.emit("sne", left.getRegister(), left.getRegister(), allocator.zero);
        assembler.emit("beq", left.getRegister(), allocator.zero, label);
        
        while(scanner.getToken() == boldAndToken)
        {
            check(left, intType);

            scanner.nextToken(); // Skip 'and' token.
            RegisterDescriptor des = nextComparison();
            check(des, intType);

            assembler.emit("sne", left.getRegister(), des.getRegister(), allocator.zero);
            assembler.emit("beq", left.getRegister(), allocator.zero, label);
            allocator.release(des.getRegister());
        }
        
        assembler.emit(label);

        exit("nextConjunction");
        return left;
    }
    
    // Parses a comparison.
    // ex. <sum> < <sum>
    private RegisterDescriptor nextComparison()
    {
        enter("nextComparison");
        
        RegisterDescriptor left = nextSum();
        
        int comparisonToken = scanner.getToken();
        if (tokenIsInSet(comparisonToken, COMPARISON_SET))
        {
            check(left, intType);

            scanner.nextToken(); // Skip comparison token.

            RegisterDescriptor right = nextSum();
            check(right, intType);
            assembler.emit("# Comparison.");
            switch(comparisonToken)
            {
                case lessToken: // left < right
                    assembler.emit("slt", left.getRegister(), left.getRegister(), right.getRegister());
                    break;
                case lessEqualToken: // left <= right
                    assembler.emit("sle", left.getRegister(), left.getRegister(), right.getRegister());
                    break;
                case greaterToken: // left > right
                    assembler.emit("sgt", right.getRegister(), right.getRegister(), left.getRegister());
                    break;
                case greaterEqualToken: // left >= right
                    assembler.emit("sge", left.getRegister(), left.getRegister(), right.getRegister());
                    break;
                case lessGreaterToken: // left <> right
                    assembler.emit("sne", left.getRegister(), left.getRegister(), right.getRegister());
                    break;
                case equalToken: // left = right
                    assembler.emit("seq", left.getRegister(), left.getRegister(), right.getRegister());
                    break;
            }
            allocator.release(right.getRegister());
        }
        
        exit("nextComparison");
        return left;
    }
    
    // Parses a sum.
    // ex. <product> + <product> - <product>
    private RegisterDescriptor nextSum()
    {
        enter("nextSum");

        RegisterDescriptor left = nextProduct();

        
        while (tokenIsInSet(scanner.getToken(), SUM_SET))
        {
            check(left, intType);
            int sumToken = scanner.getToken();
            scanner.nextToken(); // Skip '+' or '-' token.
            RegisterDescriptor right = nextProduct();
            check(right, intType);
            assembler.emit("# Sum.");
            if( sumToken == plusToken)
                assembler.emit("add", left.getRegister(), left.getRegister(), right.getRegister());
            else
                assembler.emit("sub", left.getRegister(), left.getRegister(), right.getRegister());

            allocator.release(right.getRegister());
        }
        
        exit("nextSum");
        return left;
    }
    
    // Parses a product.
    // ex. <term> * <term> / <term>
    private RegisterDescriptor nextProduct()
    {

        enter("nextProduct");

        RegisterDescriptor left = nextTerm();
        while (tokenIsInSet(scanner.getToken(), PRODUCT_SET))
        {
            check(left, intType);
            int productToken = scanner.getToken();
            scanner.nextToken(); // Skip '*' or '/' token.

            RegisterDescriptor right = nextTerm();
            check(right, intType);
            assembler.emit("# Product.");
            if( productToken == starToken)
                assembler.emit("mul", left.getRegister(), left.getRegister(), right.getRegister());
            else
                assembler.emit("div", left.getRegister(), left.getRegister(), right.getRegister());

            allocator.release(right.getRegister());            
            
        }
        
        exit("nextProduct");
        return left;
    }
    
    // Parses a term.
    // ex. -<unit>
    private RegisterDescriptor nextTerm()
    {
        RegisterDescriptor descriptor = null;
        enter("nextTerm");
        
        int termToken = scanner.getToken();
        if (tokenIsInSet(termToken, TERM_SET))
        {
            scanner.nextToken();
            descriptor = nextTerm();
            check(descriptor, intType);
            assembler.emit("# Term.");
            if (termToken == boldNotToken)
                assembler.emit("seq", descriptor.getRegister(), allocator.zero, descriptor.getRegister());
            else
                assembler.emit("sub", descriptor.getRegister(), allocator.zero, descriptor.getRegister());
        }
        else
            descriptor = nextUnit();
        
        exit("nextTerm");
        return descriptor;
    }
    
    // Parses the argument list of a call.
    private RegisterDescriptor nextCall()
    {
        enter("nextCall");
        
        // Retrieve the ProcedureType from the symbolTable.
        Descriptor descriptor = symbolTable.getDescriptor(scanner.getString());
        ProcedureType type = null;
        
        if(descriptor.getType() instanceof ProcedureType)
            type = (ProcedureType)descriptor.getType();
        else
            source.error(scanner.getString() + " is not a procedure.");

        // Has to be a GlobalProcedureDescriptor at this point.
        GlobalProcedureDescriptor procedureDescriptor = (GlobalProcedureDescriptor) descriptor;

        int arity = 0;
        scanner.nextToken(); // Skip '(' token.
        if (scanner.getToken() != closeParenToken)
        {
            arity++;
            if(type.getArity() < arity)
                source.error("Invalid number of arguments.");
                
            ProcedureType.Parameter param = type.getParameters();
            RegisterDescriptor regdes = nextExpression();
            check(regdes, param.getType());
            
            // Compile code for first expression in call.
            assembler.emit("# Call.");
            assembler.emit("sw", regdes.getRegister(), 0, allocator.sp);
            assembler.emit("addi", allocator.sp, allocator.sp, -4);
            allocator.release(regdes.getRegister());

            while (scanner.getToken() == commaToken)
            {
                nextExpected(commaToken, ", or ) expected.");
                
                arity++;
                if(type.getArity() < arity)
                    source.error("Invalid number of arguments.");
                    
                regdes = nextExpression();
                param = param.getNext();
                check(regdes, param.getType());

                // Compile code for E sub k.
                assembler.emit("sw", regdes.getRegister(), 0, allocator.sp);
                assembler.emit("addi", allocator.sp, allocator.sp, -4);
                allocator.release(regdes.getRegister());
            }
        }
        nextExpected(closeParenToken);
        
        if(type.getArity() != arity)
            source.error("Invalid number of arguments.");
        
        // Compile code for jump.
        Allocator.Register reg = allocator.request();
        assembler.emit("jal", procedureDescriptor.getLabel());
        assembler.emit("move", reg, allocator.v0);

        exit("nextCall");
        
        return new RegisterDescriptor(type.getValue(), reg);
    }


    // Parses a unit.
    private RegisterDescriptor nextUnit()
    {
        enter("nextUnit");
        
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
                
                switch (scanner.getToken())
                {
                    case openParenToken:
                    {
                        descriptor = nextCall();
                        break;
                    }
                        
                    case openBracketToken:
                    {
                        NameDescriptor nameDes = symbolTable.getDescriptor(scanner.getString());
                        descriptor = new RegisterDescriptor(nameDes.getType(), nameDes.rvalue());

                        nameDes = 
                            symbolTable.getDescriptor(scanner.getString());
                        reg = nameDes.rvalue();

                        if(! (descriptor.getType() instanceof ArrayType))
                            source.error(scanner.getString() + 
                                " is not an array.");
                            
                        scanner.nextToken(); // Skip '[' token.
                        descriptor = nextExpression();
                        check(descriptor, intType);

                        nextExpected(closeBracketToken);

                        assembler.emit("sll", descriptor.getRegister(), descriptor.getRegister(), 2);
                        assembler.emit("add", reg, reg, descriptor.getRegister());
                        assembler.emit("lw", reg, 0, reg);
                        
                        allocator.release(descriptor.getRegister());
                        

                        descriptor = new RegisterDescriptor(intType, reg);
                        break;
                    }
                    default:
                    {
                        NameDescriptor nameDes = symbolTable.getDescriptor(scanner.getString());
                        descriptor = new RegisterDescriptor(nameDes.getType(), nameDes.rvalue());
                        break;
                    }
                }
                
                break;
            default:
                source.error("Unit expected.");
                break;
        }

        exit("nextUnit");

        return descriptor;
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
    
    // Parses a global declaration.
    private void nextGlobalDeclaration()
    {
        enter("nextGlobalDeclaration");
        switch(scanner.getToken())
        {
            case boldIntToken:
            {
                scanner.nextToken(); // Skip 'int' token.

                Label label = global.enterVariable(intType);
                GlobalVariableDescriptor descriptor = new GlobalVariableDescriptor(intType, label);
                symbolTable.setDescriptor(scanner.getString(), descriptor);   

                break;
            }
            case boldStringToken:
            {
                scanner.nextToken(); // Skip 'string' token.

                Label label = global.enterVariable(stringType);
                GlobalVariableDescriptor descriptor = new GlobalVariableDescriptor(stringType, label);
                symbolTable.setDescriptor(scanner.getString(), descriptor);

                break;
            }
            case openBracketToken:
            {
                scanner.nextToken(); // Skip '[' token.

                nextExpected(intConstantToken);
                nextExpected(closeBracketToken);
                nextExpected(boldIntToken);
                

                ArrayType arrayType = new ArrayType(scanner.getInt(), intType);

                Label label = global.enterVariable(arrayType);
                GlobalArrayDescriptor descriptor = new GlobalArrayDescriptor(arrayType, label);
                symbolTable.setDescriptor(scanner.getString(), descriptor);

                break;
            }
            default:
                source.error("Global Declaration expected.");
                break;
        }
        nextExpected(nameToken); 
        exit("nextGlobalDeclaration");
    }

    // Parses a local declaration and returns a negative offset.
    private int nextLocalDeclaration(int offset, boolean isParameter)
    {
        enter("nextLocalDeclaration");

        switch(scanner.getToken())
        {
            case boldIntToken:
            {
                scanner.nextToken(); // Skip 'int' token.
                LocalVariableDescriptor descriptor = new LocalVariableDescriptor(intType, offset);
                symbolTable.setDescriptor(scanner.getString(), descriptor);
                if (isParameter)
                    offset -= 4;
                else
                    offset -= intType.getSize();

                break;
            }
            case boldStringToken:
            {
                scanner.nextToken(); // Skip 'string' token.

                LocalVariableDescriptor descriptor = new LocalVariableDescriptor(stringType, offset);
                symbolTable.setDescriptor(scanner.getString(), descriptor);
                if (isParameter)
                    offset -= 4;
                else
                    offset -= stringType.getSize();

                break;
            }
            case openBracketToken:
            {
                scanner.nextToken(); // Skip '[' token.

                nextExpected(intConstantToken);
                nextExpected(closeBracketToken);
                nextExpected(boldIntToken);
                

                ArrayType arrayType = new ArrayType(scanner.getInt(), intType);

                LocalArrayDescriptor descriptor = new LocalArrayDescriptor(arrayType, offset);
                symbolTable.setDescriptor(scanner.getString(), descriptor);
                if (isParameter)
                    offset -= 4;
                else
                    offset -= arrayType.getSize();
                
                break;
            }
            default:
                source.error("Local Declaration expected.");
                break;
        }

        nextExpected(nameToken); 
        exit("nextLocalDeclaration");
        return offset;
    }

    
    // Emits code for a procedure prelude.
    private void emitProcedurePrelude(int localOffset, int arity)
    {
        assembler.emit("# Begin procedure prelude.");
        assembler.emit("addi", allocator.sp, allocator.sp, -(40 - localOffset));

        assembler.emit("sw", allocator.ra, 40, allocator.sp);
        assembler.emit("sw", allocator.fp, 36, allocator.sp);

        for(int i = 0; i<8; i++)
        {
            int offset = 32 - i * 4;
            String emit = "sw $s" + i + ", " + offset + "($sp)";
            assembler.emit(emit);
        }

        assembler.emit("addi", allocator.fp, allocator.sp, (40 - localOffset + 4 * arity));
        assembler.emit("# End procedure prelude.");
    }

    // Emits code for a procedure postlude.
    private void emitProcedurePostlude(int localOffset, int arity)
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

        assembler.emit("addi", allocator.sp, allocator.sp, (40 - localOffset + 4 * arity));
        assembler.emit("jr $ra");
        assembler.emit("# End procedure postlude.");
    }

    // Parses an entire procedure.
    private void nextProcedure()
    {
        enter("nextProcedure");
        
        symbolTable.push();
        scanner.nextToken(); // Skip 'proc' token.
        nextExpected(nameToken);
        
        // Get the label of the procedure from the symbol table.
        // Emit the descriptors label.
        String procName = scanner.getString();
        GlobalProcedureDescriptor descriptor = 
            (GlobalProcedureDescriptor)symbolTable.getDescriptor(procName);

        assembler.emit("# Procedure " + procName + ".");
        assembler.emit(descriptor.getLabel());

        int parameterOffset = nextProcedureSignature(); // TODO: not sure why we need this parameter offset.

        nextExpected(colonToken);
        nextProcedureBody(descriptor);
        symbolTable.pop();
        
        exit("nextProcedure");
    }
    
    // Parses the body of a procedure.
    private void nextProcedureBody(GlobalProcedureDescriptor descriptor)
    {
        enter("nextProcedureBody");
        
        int localOffset = 0;

        if(tokenIsInSet(scanner.getToken(), DECLARATION_SET))
        {
            localOffset = nextLocalDeclaration(localOffset, false);
            while(scanner.getToken() == semicolonToken)
            {
                scanner.nextToken(); // Skip ';' token.
                localOffset = nextLocalDeclaration(localOffset, false);
            }
        }

        int arity = ((ProcedureType)descriptor.getType()).getArity();
        emitProcedurePrelude(localOffset, arity);

        assembler.emit("# Begin procedure body.");
        nextBegin();
        assembler.emit("# End procedure body.");

        emitProcedurePostlude(localOffset, arity);
            
        exit("nextProcedureBody");
    }
    
    // Parses the signature part of a procedure.
    // Return the offset of the parameters, each parameter
    // is 1 word, or 4 bytes.
    private int nextProcedureSignature()
    {
        enter("nextProcedureSignature");
        
        int parameterOffset = 0;

        nextExpected(openParenToken);
        if(tokenIsInSet(scanner.getToken(), DECLARATION_SET))
        {
            parameterOffset = nextLocalDeclaration(parameterOffset, true);
            while(scanner.getToken() == commaToken)
            {
                scanner.nextToken(); // Skip ',' token.
                parameterOffset = nextLocalDeclaration(parameterOffset, true);
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
        return parameterOffset;
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
