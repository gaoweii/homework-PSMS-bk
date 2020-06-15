package process.gao;


import system.annotation.ProcessRun;

public class Test3 {
    @ProcessRun(serviceTime = 8, requestTime = 8000)
    public void say() {
        System.out.println("Hello");
    }
}
