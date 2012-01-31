/*
Descriptor.java
Brian Dawn 
Latest Revision: November 29th, 2011.

A Descriptor is a type container.
*/


class Descriptor
{
    //The Type for this descriptor.
    private Type type;
    
    public Descriptor(Type type)
    {
        this.type = type;
    }
    
    public Type getType()
    {
        return type;
    }
    
    public String toString()
    {
        return type.toString();
    }
}