package system.module.processpool;

import lombok.Data;
import lombok.SneakyThrows;
import org.apache.commons.collections.map.MultiValueMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import system.annotation.Notice;
import system.annotation.NoticeParam;
import system.module.FrameworkMsg;
import system.module.core.Module;
import system.module.core.ProcessFramework;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.LockSupport;


@Data
public class ProcessPool implements Module {
    private List<PCB> waitQueue;
    private List<PCB> readyQueue;
    private List<PCB> runQueue;
    private int waitQueueCapacity;
    private int readyQueueCapacity;
    private int runQueueCapacity;
    private List<PCB> pcbList;
    private MultiValueMap PCBMethods;
    private long pidCal;
    private static Logger logger = LoggerFactory.getLogger("log");
    private FrameworkMsg msg;
    private ExecutorService executorService;
    private List<List<PCB>> queues;
    private List<String> producers;
    private Monitor monitor;


    public void sendMsg(Object o) {
        msg.setObject(o);
    }

    public boolean haveFinished() {
        return (waitQueue.size() != 0 || readyQueue.size() != 0 || runQueue.size() != 0);
    }

    /**通过异步回调的方式模拟对系统资源的请求*/
    public void PCBBlocked(PCB pcb) {
        CompletableFuture<Void> future = CompletableFuture.supplyAsync(() -> {
            try {
                waitQueue.add(pcb);
                pcb.setProcessStatus(ProcessStatus.WAIT);
                pcb.setRequestTime_save(pcb.getRequestTime());
                Thread.sleep(pcb.getRequestTime());
                pcb.setRequestTime(-1);
                waitQueue.remove(pcb);
                pcb.setProcessStatus(ProcessStatus.READY);
                readyQueue.add(pcb);

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return null;
        }, executorService);
    }

    void sendMsg() {
        queues.clear();
        queues.add(waitQueue);
        queues.add(readyQueue);
        queues.add(runQueue);
        msg.sendMsgToSocket("queues", queues);
        msg.sendMsgToSocket("PCB", pcbList);
        msg.sendMsgToSocket("finished", null);

    }

    @SneakyThrows
    private void runProcess(PCB pcb) {
        synchronized (this) {
            int runtime = 0;
            while (pcb.getRunTime() < pcb.getServiceTime()) {
                Method method = pcb.getProcessInfo().getMethod(pcb.getMethod());
                Notice notice = method.getAnnotation(Notice.class);
                /**这里要实现的功能是，如果有参数，就*/
                Parameter[] parameters1 = method.getParameters();
                List<Parameter> parameters = new ArrayList<>();
                for (Parameter parameter : parameters1) {
                    parameters.add(parameter);
                }

                for (Parameter parameter : parameters) {
                    NoticeParam noticeParam = parameter.getAnnotation(NoticeParam.class);
                    if (noticeParam != null) {
                    }
                }
                if (notice != null) {
                   String msg = (String) method.invoke(pcb.getTask(), parameters.toArray());
                   if (runtime + 1 == notice.time()) {
                       monitor.addMonitor(pcb.getProcessInfo().getName() + "." + method.getName(), notice.target(), msg);
                   }
                } else {
                    pcb.getProcessInfo().getMethod(pcb.getMethod()).invoke(pcb.getTask(), parameters.toArray());
                }
                runtime++;
                pcb.setRunTime(runtime);
                Thread.sleep(1000);
            }
            pcb.setFinishTime(runtime + pcb.getArriveTime());
            pcb.setProcessStatus(ProcessStatus.FINISH);
        }
    }

    @SneakyThrows
    public void FIFO() {
        while (haveFinished()) {
            /**设置锁，避免在循环途中队列被改变，发生意外（可能性极小）*/
            synchronized (this) {
                for (int i = 0; readyQueue.size() != 0 && i < runQueueCapacity - runQueue.size(); ++i) {
                    PCB pcb = readyQueue.remove(0);
                    pcb.setProcessStatus(ProcessStatus.RUN);
                    runQueue.add(pcb);
                    sendMsg();
                }

                while (runQueue.size() != 0) {
                    PCB pcb = runQueue.remove(0);
                    if (pcb.getRequestTime() > 0) {
                        PCBBlocked(pcb);
                        sendMsg();
                        continue;
                    }
                    runProcess(pcb);
                }

            }
            /**这里让当前线程睡眠1ms，避免死锁*/
            Thread.sleep(1);
        }
    }

    @SneakyThrows
    public void RR() {
        while (haveFinished()) {
            synchronized (this) {
               PCB pcb = readyQueue.remove(0);
               pcb.setProcessStatus(ProcessStatus.RUN);
               runQueue.add(pcb);
                sendMsg();
                if (pcb.getRequestTime() > 0) {
                   runQueue.remove(pcb);
                   PCBBlocked(pcb);
                   sendMsg();
                   continue;
               }
               for (int i = 1; i < 3 && pcb.getRunTime() < pcb.getServiceTime(); ++i) {
                   pcb.setRunTime(pcb.getRunTime() + 1);
                   Thread.sleep(1000);
                   if (pcb.getRunTime() >= pcb.getServiceTime()) {
                       pcb.setProcessStatus(ProcessStatus.FINISH);
                       break;
                   }
               }
                runQueue.remove(pcb);
                if (pcb.getProcessStatus() != ProcessStatus.FINISH) {
                   pcb.setProcessStatus(ProcessStatus.READY);
                   readyQueue.add(pcb);
               }
            }
        }
    }

    @SneakyThrows
    public void SJF() {
        while (haveFinished()) {
            synchronized (this) {
                int minTime = 999999999;
                PCB minTImePCB = null;
                for (PCB pcb : readyQueue) {
                   if (minTime > pcb.getServiceTime()) {
                       minTime = pcb.getServiceTime();
                       minTImePCB = pcb;
                   }
                }
                readyQueue.remove(minTImePCB);
                if (minTImePCB == null) {
                    continue;
                }
                minTImePCB.setProcessStatus(ProcessStatus.RUN);
                runQueue.add(minTImePCB);
                sendMsg();
                if (minTImePCB.getRequestTime() > 0) {
                    runQueue.remove(minTImePCB);
                    PCBBlocked(minTImePCB);
                    sendMsg();
                    continue;
                }
                runQueue.remove(minTImePCB);
                runProcess(minTImePCB);
                Thread.sleep(1);

            }
        }
    }



    public void init() {
        msg = ProcessFramework.getFrameworkMsg();
        monitor = new Monitor();
        init(20, 20, 1);
    }

    @SneakyThrows
    public void reset() {
        for (PCB pcb : pcbList) {
            pcb.setProcessStatus(ProcessStatus.READY);
            pcb.setRunTime(0);
            pcb.setFinishTime(0);
            pcb.setRequestTime(pcb.getRequestTime_save());
            readyQueue.add(pcb);

        }
        Thread.sleep(1000);
        sendMsg();
    }

    public void init(int waitQueueSize, int readyQueueSize, int runQueueSize) {
        executorService = Executors.newCachedThreadPool();
        queues = new ArrayList<>();
        logger.info("processPool init... \n" + "waitQueueSize = " + waitQueueSize + "; readyQueueSize = " + readyQueueSize + "; runQueueSize = " + runQueueSize);
        waitQueueCapacity = waitQueueSize;
        readyQueueCapacity = readyQueueSize;
        runQueueCapacity = runQueueSize;
        pidCal = 0;
        waitQueue = new ArrayList<>(waitQueueSize);
        readyQueue = new ArrayList<>(readyQueueSize);
        runQueue = new ArrayList<>(runQueueSize);
        pcbList = ProcessFramework.getPCBList();
        PCBMethods = (MultiValueMap) ProcessFramework.getPCBProcessMethods();
        logger.info( "PCB INFO: \n");
        for (PCB pcb : pcbList) {
            pcb.setPid(pidCal++);
            pcb.setProcessStatus(ProcessStatus.READY);
            logger.info(pcb.toString());
            readyQueue.add(pcb);
        }
        msg.setObject(pcbList);
    }

    @SneakyThrows
    public void run() {
        logger.info("ProcessPool run..");
        here:
        while (true) {
            synchronized (this) {
                LockSupport.park();
                switch (msg.getInstructions()) {
                    case SHUTDOWN_PROCESSPOOL:
                        break here;
                    case NULL:
                        break;
                    case FIFO:
                        FIFO();
                        reset();
                        break ;
                    case SJF:
                        SJF();
                        reset();
                        break ;
                    case RR:
                        RR();
                        reset();
                        break ;
                }
            }
            /**同样，避免死锁*/
            Thread.sleep(1);
        }
        close();
    }

    public void close() {
        System.out.println("processPool close");
    }
}

class Monitor {

    private Map<String, Map<String, String>> producers_consumers;

    Monitor() {
        producers_consumers = new HashMap<>();
    }

    public void addMonitor(String producer, String consumer, String msg) {
        if (producers_consumers.containsKey(producer) == false) {
            producers_consumers.put(producer, new HashMap<>());
            producers_consumers.get(producer).put(consumer, msg);

        } else {
            producers_consumers.get(producer).put(consumer, msg);
        }
    }

    public void removeMonitor(String producer, String consumer) {
        producers_consumers.get(producer).remove(consumer);
        if (producers_consumers.get(producer).isEmpty() == true) {
            producers_consumers.remove(producer);
        }
    }

    public String getMessage(String producer, String consumer) {
        String msg = producers_consumers.get(producer).get(consumer);
        producers_consumers.get(producer).get(consumer);
        return msg;
    }
}
