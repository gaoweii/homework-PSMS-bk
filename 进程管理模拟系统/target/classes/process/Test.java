package process;

import system.annotation.ProcessRun;

public class Test {
    Integer integer;
    public Test() {
        integer = new Integer(10);
    }

    @ProcessRun(serviceTime = 5)
    public void say() {
        System.out.println(integer);
    }

    @ProcessRun(serviceTime = 6)
    public void hello() {
        System.out.println("sadsad");
    }
}
