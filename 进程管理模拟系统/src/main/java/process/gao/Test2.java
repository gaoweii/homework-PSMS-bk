package process.gao;

import system.annotation.ProcessRun;

/**
 * 这里用管程的思想解决同步问题。。。*/
public class Test2 {

    @ProcessRun(name = "test2", priority = 6, serviceTime = 4)
    public void print() {
        System.out.println("我是test2");
    }

    @ProcessRun(serviceTime = 3, requestTime = 3000)
    public void print2() {
        System.out.println("123");
    }

    @ProcessRun(serviceTime = 5)
    public void print3() {
        System.out.println("345");
    }
}
