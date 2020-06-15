package system.module.processpool;

import lombok.Data;

/**
 * PCB模块类
 * 会重复执行@ProcessRun注释过的方法
 *
 * */
@Data
public class PCB {

    private String name;

    private long pid; //系统分配

    private ProcessStatus processStatus;

    private long priority;

    private Class processInfo;

    private int serviceTime;

    private int arriveTime;

    private int finishTime;

    private int runTime;

    private int requestTime;

    private int requestTime_save;

    private String method;

    private Object task;



}


