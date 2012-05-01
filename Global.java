/*
Global.java
Brian Dawn
Latest Revision: February 11th, 2012.
 */

import java.util.Map;
import java.util.HashMap;
import java.util.Vector;

class Global
{
    //Stores strings and labels, that way we don't store repeat
    //strings in different labels.
    private HashMap<String, Label> labelMap;
    
    //Keeps track of global variables.
    private Vector<Label> labelVector;
    private Vector<Integer> sizeVector;
    
    //The assembler we use to emit instructions to.
    private Assembler assembler;

    //Create a new instance of Global with an assembler.
    public Global(Assembler assembler)
    {
        labelMap = new HashMap<String, Label>();
        labelVector = new Vector<Label>();
        sizeVector = new Vector<Integer>();

        this.assembler = assembler;
    }

    //Add a new string.
    public Label enterString(String string)
    {
        Label label = labelMap.get(string);
        if (label == null)
        {
            label = new Label("string");
            labelMap.put(string, label);
        }

        return label;
    }

    //Add a new global variable with a type.
    public Label enterVariable(Type type)
    {
        Label label = new Label("variable");

        labelVector.add(label);
        sizeVector.add(type.getSize());
        return label;
    }

    //Spit the globals out to the file targeted by the assembler.
    public void emit()
    {
        assembler.emitTop(".data");

        //Iterate through the labelMap retrieving both key and value.
        for (Map.Entry<String, Label> entry : labelMap.entrySet())
        {
            String string = entry.getKey();
            Label label = entry.getValue();

            assembler.emitTop(label + ": .asciiz \"" + string + "\"");
        }

        for (int i = 0; i < labelVector.size(); i++)
        {
            assembler.emitTop(labelVector.get(i) + ": .space " + sizeVector.get(i));
        }

        assembler.emitTop(".text");
    }

    //Testing code.
    public static void main(String[] args)
    {
        BasicType stringType = new BasicType("string", Type.addressSize, null);
        BasicType intType = new BasicType("int", Type.wordSize, null);

        Assembler asmblr = new Assembler("out.asm");
        Global global = new Global(asmblr);

        global.enterString("hello");
        global.enterString("test string!");

        global.enterVariable(new ArrayType(10, intType));
        global.enterVariable(intType);

        global.emit();
        asmblr.close();
    }
}