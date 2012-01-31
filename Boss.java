/*
Brian Dawn
November 21, 2011

Answers
1a. girl is a subtype of child.
    child is a subtype of person.
    
    child       person       person
    -----   X   ------   =   ------  
    girl        child        girl


1d.
    Cannot be done, teen cannot have more than one subtype given
    the current system of using BasicType.

Output

true
[5] person
[5] man
false
true
proc (man, woman) child
proc (person, person) person
false
false

*/
    
class Boss
{
    //Basic types for question 1.
    private BasicType person;
    private BasicType child;
    private BasicType adult;
    private BasicType man;
    private BasicType woman;
    private BasicType girl;
    private BasicType boy;
    
    //ArrayTypes for question 2.
    private ArrayType person5;
    private ArrayType man5;
    
    //ProcedureTypes for question 3.
    private ProcedureType makeBaby;
    private ProcedureType constructPerson;
    
    public Boss()
    {
        //Question 1.
        person = new BasicType("person", 0, null);
        adult = new BasicType("adult", 0, person);
        child = new BasicType("child", 0, person);
        man = new BasicType("man", 0, adult);
        woman = new BasicType("woman", 0, adult);
        girl = new BasicType("girl", 0, child);
        boy = new BasicType("boy", 0, child);
        
        //Question 2.
        person5 = new ArrayType(5, person);
        man5 = new ArrayType(5, man);
        
        //Question 3.
        makeBaby = new ProcedureType();
        makeBaby.addParameter(man);
        makeBaby.addParameter(woman);
        makeBaby.addValue(child);
        
        constructPerson = new ProcedureType();
        constructPerson.addParameter(person);
        constructPerson.addParameter(person);
        constructPerson.addValue(person);
    }

    
    
    public static void main(String[] args)
    {
        Boss b = new Boss();
        
        //Question 1
        System.out.println(b.girl.isSubtype(b.person));
        
        //Question 2
        System.out.println(b.person5);
        System.out.println(b.man5);
        System.out.println(b.person5.isSubtype(b.man5));
        System.out.println(b.man5.isSubtype(b.person5));
        
        //Question 3
        System.out.println(b.makeBaby);
        System.out.println(b.constructPerson);
        
        System.out.println(b.makeBaby.isSubtype(b.constructPerson));
        System.out.println(b.constructPerson.isSubtype(b.makeBaby));
        
        
    }
    
}
