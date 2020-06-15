package process;

import system.annotation.Notice;
import system.annotation.NoticeParam;
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

    @ProcessRun(serviceTime = 5)
    @Notice(target = "process.Test.hello3")
    public String hello2() {
        return "223";
    }

    public void hello3(@NoticeParam String str) {
        System.out.println(str);
    }
}
