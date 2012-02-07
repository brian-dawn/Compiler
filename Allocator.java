//Allocator.java
//Brian Dawn
//Latest Revision: February 6th, 2012.
    
public class Allocator
{
    public final class Register
    {
        private String name; //Printable name of this Register.
        private Register next; //Next Register is Registers.
        private boolean used; //Has this Register been allocated?
    
        //Prevent use of the default constructor.
        private Register()
        {
        }
    
        //Create a new register.
        private Register(String name, Register next, boolean used)
        {
            this.name = name;
            this.next = next;
            this.used = used;
        }
    
        //Getter for used.
        public boolean isUsed()
        {
            return used;
        }
    
        public String toString()
        {
            return name;
        }
    }
    
    //Built in registers that are always available.
    public final Register fp = new Register("$fp", null, true);
    public final Register ra = new Register("$ra", null, true);
    public final Register sp = new Register("$sp", null, true);
    public final Register v0 = new Register("$v0", null, true);
    public final Register zero = new Register("$zero", null, true);
    
    private Register registers; //Linked stack of Register's to be allocated.
    private Source source; //So we can call source.error.
    
    //Create a new allocator from a source file.
    public Allocator(Source source)
    {
        this.source = source;
        
        //Make s0 through s7 by building the list backwards.
        Register next = null;
        for(int i = 7; i>=0; i--)
        {
            Register r = new Register("$s" + i, next, false);
            next = r;
        }
        registers = next;
        
        
    }
    
    //Request a register from the allocator.
    public Register request()
    {
        if (registers == null)
            source.error("Expression is too complex.");
        
        Register register = registers;
        registers = registers.next;
        register.used = true;
        return register;
    }
    
    //Return a register to the allocator.
    public void release(Register register) throws RuntimeException
    {
        if (!register.isUsed())
            throw new RuntimeException("Register released twice.");
        
        register.used = false;
        Register temp_registers = registers;
        registers = register;
        registers.next = temp_registers;
    }
    
    //Testing.
    public static void main(String[] args)
    {
        Source source = new Source(args[0]);

        Allocator allocator = new Allocator(source);
        
        for(int i = 0; i < 7; i++)
            System.out.println(allocator.request());
        
        //The s7 register.
        Register r = allocator.request();
        
        allocator.release(r);
        System.out.println(allocator.request());
        allocator.release(r);
        //allocator.release(r); Uncomment this and get an exception.
        allocator.request();
        allocator.request();
    }
}