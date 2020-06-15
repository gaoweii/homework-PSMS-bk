package system.module;


import com.alibaba.fastjson.JSON;
import lombok.Data;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import system.module.core.Module;
import system.module.core.ProcessFramework;
import system.module.network.InfoBody;
import system.module.network.MyWebSocket;

import java.util.concurrent.locks.LockSupport;

/**框架通信模块,作为服务类，最先初始化*/
@Data
public class FrameworkMsg implements Module {


    private Object object;
    private String msg;
    private Thread toWho;
    private Instructions instructions;
    private static Logger logger = LoggerFactory.getLogger("log");


    public enum  Instructions {
        SHUTDOWN_PROCESSPOOL, NULL, FIFO, QUEUE_SHOW, SJF, RR
    }


    public void init() {
        logger.info("FrameworkMsg init...");
    }

    public void run() {

    }


    public void sendMsgToThread(Thread thread, String msg, Instructions instructions, Object object) {
        synchronized (this) {
            this.toWho = thread;
            this.msg = msg;
            this.instructions = instructions;
            this.object = object;
            LockSupport.unpark(toWho);
        }

    }

    public void sendMsgToThread(Thread thread) {
        synchronized (this) {
            this.toWho = thread;
            this.msg = "";
            this.instructions = Instructions.NULL;
            this.object = null;
            LockSupport.unpark(toWho);
        }

    }

    public void sendMsgToThread(Thread thread, String msg) {
        synchronized (this) {
            this.toWho = thread;
            this.msg = msg;
            this.object = null;
            this.instructions = Instructions.NULL;
            LockSupport.unpark(toWho);
        }

    }

    @SneakyThrows
    public void sendMsgToThread(Thread thread, Instructions instructions) {
        synchronized (this) {
            this.toWho = thread;
            this.msg = msg;
            this.object = null;
            this.instructions = instructions;
            LockSupport.unpark(toWho);
        }

    }

    public void sendMsgToSocket(String msg, Object o) {
        synchronized (this) {
            this.toWho = null;
            this.object = o;
            this.msg = msg;
            this.instructions = Instructions.NULL;
            MyWebSocket myWebSocket = (MyWebSocket) ProcessFramework.getAllService().get("system.module.network.MyWebSocket");
            InfoBody infoBody = new InfoBody();
            infoBody.setHeader(msg);
            infoBody.setMsg(JSON.toJSONString(o));
            myWebSocket.send(infoBody);

        }
    }

    public void close() {

    }
}


