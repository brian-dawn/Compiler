/*
SymbolTable.java
Brian Dawn 
Latest Revision: November 29th, 2011.

A SymbolTable used for the SNARL Compiler.
*/

import java.util.HashMap;
import java.util.LinkedList;
import java.util.EmptyStackException;

class SymbolTable
{
    //The source object used for error calling.
    private Source source;
    
    //List of HashMaps for the SymbolTable.
    //We could have used Stack but that would
    //make things more complex later.
    private LinkedList<HashMap<String, Descriptor>> list;
    
    //Initialize the list of HashMaps.
    public SymbolTable(Source src)
    {
        source = src;
        list = new LinkedList<HashMap<String, Descriptor>>();
    }
    
    //Change the Source object.
    public void setSource(Source src)
    {
        source = src;
    }
    
    //Returns whether or not the SymbolTable has any scopes.
    public boolean isEmpty()
    {
        return list.isEmpty();
    }
    
    //Push a new scope onto the front of the list.
    public void push()
    {
        list.add(0, new HashMap<String, Descriptor>());
    }
    
    //Remove the scope from the front of the list.
    public void pop() throws RuntimeException
    {
        if (isEmpty()) throw new RuntimeException("pop called on empty SymbolTable.");
        
        list.remove(0);
    }
    
    //Returns whether or not name is declared in any scope.
    public boolean isDeclared(String name)
    {
        for (HashMap<String, Descriptor> scope : list)
        {
            if (scope.get(name) != null)
                return true;
        }
        return false;
    }
    
    //Returns a descriptor corresponding to name if found.
    //Otherwise there is an error in the compiled program.
    public Descriptor getDescriptor(String name) throws RuntimeException
    {
        if (isEmpty()) throw new RuntimeException("getDescriptor called on empty SymbolTable.");
        
        for (HashMap<String, Descriptor> scope : list)
        {
            Descriptor descrip = scope.get(name);
            if (descrip != null)
                return descrip;
        }
        
        source.error(name + " is not declared.");
        return null;
    }
    
    //Add name and descriptor into the top scope (front of the list).
    //If name is already in the top scope then there is an error
    //in the compiled program.
    public void setDescriptor(String name, Descriptor descriptor) throws RuntimeException
    {
        if (isEmpty()) throw new RuntimeException("setDescriptor called on empty SymbolTable.");
        
        HashMap<String, Descriptor> topMostScope = list.get(0);
        
        if (topMostScope.get(name) == null)
        {
            topMostScope.put(name, descriptor);
        }
        else
        {
            source.error(name + " is already declared.");
        }
    }
    
    //Test the SymbolTable methods.
    public static void main(String[] args)
    {
        SymbolTable st = new SymbolTable(new Source(args[0]));
        
        Type intType = new BasicType("int", Type.wordSize, null);
        Descriptor intDesc = new Descriptor(intType);
        
        st.push();
        
        st.setDescriptor("global", intDesc);
        
        //Uncomment: Gives an already declared error.
        //st.setDescriptor("global", intDesc); 
        
        System.out.println(st.isDeclared("global"));
        
        st.push();
        st.setDescriptor("local", intDesc);
        
        System.out.println(st.getDescriptor("local") != null);
        
        st.pop();
        
        //Uncomment: Gives a not declared error.
        //System.out.println(st.getDescriptor("local") != null);
        
        System.out.println(st.isDeclared("local"));
        
        st.pop();
        
    }
    
}